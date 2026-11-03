package fr.cliker.clikerv2.achievements;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Lectures indexées et déblocages atomiques, uniquement sur la file SQLite existante. */
public final class AchievementStore {
    public record Progress(
            Map<String, Long> values, Map<String, Long> unlocked, List<String> added) {}

    private final DatabaseManager db;
    private final CompletableFuture<Void> ready;

    public AchievementStore(DatabaseManager db) {
        this.db = db;
        ready =
                db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.executeUpdate(
                                        "CREATE TABLE IF NOT EXISTS achievement_progress(owner TEXT"
                                                + " NOT NULL,metric TEXT NOT NULL,value INTEGER NOT"
                                                + " NULL,PRIMARY KEY(owner,metric))");
                                s.executeUpdate(
                                        "CREATE TABLE IF NOT EXISTS achievement_unlocks(owner TEXT"
                                                + " NOT NULL,id TEXT NOT NULL,unlocked INTEGER NOT"
                                                + " NULL,PRIMARY KEY(owner,id))");
                                // Aucun balayage de tous les courriers/sessions pour suivre un seul
                                // joueur.
                                if (table(c, "community_mail"))
                                    s.executeUpdate(
                                            "CREATE INDEX IF NOT EXISTS achievement_mail_sender ON"
                                                    + " community_mail(sender)");
                                if (table(c, "afk_time_sessions"))
                                    s.executeUpdate(
                                            "CREATE INDEX IF NOT EXISTS achievement_afk_owner ON"
                                                    + " afk_time_sessions(owner)");
                                s.executeUpdate(
                                        "CREATE INDEX IF NOT EXISTS achievement_contract_owner ON"
                                                + " hunting_contracts(owner,claimed)");
                                return null;
                            } catch (SQLException e) {
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        });
    }

    public CompletableFuture<Progress> refresh(
            UUID owner, Map<String, Long> live, List<AchievementCatalog.Entry> catalog) {
        var copy = Map.copyOf(live);
        return ready.thenCompose(
                v ->
                        db.submit(
                                c -> {
                                    try {
                                        c.setAutoCommit(false);
                                        Map<String, Long> previous =
                                                read(
                                                        c,
                                                        "SELECT metric,value FROM"
                                                            + " achievement_progress WHERE owner=?",
                                                        owner);
                                        Map<String, Long> values = new HashMap<>(copy);
                                        try (var s =
                                                c.prepareStatement(
                                                        "SELECT mob,boss,kills FROM bestiary_stats"
                                                                + " WHERE owner=?")) {
                                            s.setString(1, owner.toString());
                                            try (var r = s.executeQuery()) {
                                                long kills = 0, bosses = 0;
                                                while (r.next()) {
                                                    long count = Math.max(0, r.getLong(3));
                                                    values.put(
                                                            (r.getBoolean(2) ? "boss_" : "mob_")
                                                                    + r.getString(1),
                                                            count);
                                                    kills = saturatedAdd(kills, count);
                                                    if (r.getBoolean(2))
                                                        bosses = saturatedAdd(bosses, count);
                                                }
                                                values.put("kills", kills);
                                                values.put("bosses", bosses);
                                            }
                                        }
                                        values.put(
                                                "prestige",
                                                scalar(
                                                        c,
                                                        "SELECT level FROM player_prestige WHERE"
                                                                + " uuid=?",
                                                        owner));
                                        values.put(
                                                "talismans",
                                                scalar(
                                                        c,
                                                        "SELECT count(*) FROM registered_talismans"
                                                                + " WHERE owner=?",
                                                        owner));
                                        values.put(
                                                "contracts",
                                                scalar(
                                                        c,
                                                        "SELECT count(*) FROM hunting_contracts"
                                                                + " WHERE owner=? AND claimed=1",
                                                        owner));
                                        values.put(
                                                "raids",
                                                scalar(
                                                        c,
                                                        "SELECT COALESCE(sum(fights),0) FROM"
                                                            + " boss_records WHERE owner=? AND boss"
                                                            + " LIKE 'raid:%'",
                                                        owner));
                                        values.put(
                                                "serverboss",
                                                scalar(
                                                        c,
                                                        "SELECT COALESCE(sum(fights),0) FROM"
                                                            + " boss_records WHERE owner=? AND boss"
                                                            + " LIKE 'serveur:%'",
                                                        owner));
                                        values.put(
                                                "xp",
                                                scalar(
                                                        c,
                                                        "SELECT total FROM earned_xp WHERE owner=?",
                                                        owner));
                                        values.put(
                                                "ecus",
                                                scalar(
                                                        c,
                                                        "SELECT COALESCE(sum(amount),0) FROM"
                                                            + " bestiary_money WHERE owner=? AND"
                                                            + " currency='ecus'",
                                                        owner));
                                        values.put(
                                                "gems",
                                                scalar(
                                                        c,
                                                        "SELECT COALESCE(sum(amount),0)/10 FROM"
                                                            + " bestiary_money WHERE owner=? AND"
                                                            + " currency='gemmes-sombres'",
                                                        owner));
                                        values.put(
                                                "atm",
                                                scalar(
                                                        c,
                                                        "SELECT COALESCE(total,0) FROM atm_claimed"
                                                                + " WHERE owner=?",
                                                        owner));
                                        values.put(
                                                "outpost",
                                                scalar(
                                                        c,
                                                        "SELECT tier+1 FROM outposts WHERE owner=?",
                                                        owner));
                                        values.put(
                                                "mail",
                                                scalar(
                                                        c,
                                                        "SELECT total FROM achievement_mail_counts"
                                                            + " WHERE owner=?",
                                                        owner));
                                        optional(
                                                c,
                                                values,
                                                "afk",
                                                "afk_time_sessions",
                                                "SELECT COALESCE(sum(seconds),0)/60 FROM"
                                                        + " afk_time_sessions WHERE owner=?",
                                                owner);
                                        optional(
                                                c,
                                                values,
                                                "pinata",
                                                "pinata_totals",
                                                "SELECT COALESCE(sum(hits),0) FROM pinata_totals"
                                                        + " WHERE owner=?",
                                                owner);
                                        optional(
                                                c,
                                                values,
                                                "welcomes",
                                                "community_welcomes",
                                                "SELECT count(*) FROM community_welcomes WHERE"
                                                        + " owner=?",
                                                owner);
                                        previous.forEach((k, n) -> values.merge(k, n, Math::max));
                                        try (var s =
                                                c.prepareStatement(
                                                        "INSERT INTO"
                                                            + " achievement_progress(owner,metric,value)"
                                                            + " VALUES(?,?,?) ON"
                                                            + " CONFLICT(owner,metric) DO UPDATE"
                                                            + " SET value=excluded.value")) {
                                            for (var e : values.entrySet())
                                                if (!Objects.equals(
                                                        previous.get(e.getKey()), e.getValue())) {
                                                    s.setString(1, owner.toString());
                                                    s.setString(2, e.getKey());
                                                    s.setLong(3, Math.max(0, e.getValue()));
                                                    s.addBatch();
                                                }
                                            s.executeBatch();
                                        }
                                        Map<String, Long> unlocked =
                                                read(
                                                        c,
                                                        "SELECT id,unlocked FROM"
                                                            + " achievement_unlocks WHERE owner=?",
                                                        owner);
                                        List<String> added = new ArrayList<>();
                                        long now = System.currentTimeMillis();
                                        try (var s =
                                                c.prepareStatement(
                                                        "INSERT OR IGNORE INTO"
                                                            + " achievement_unlocks(owner,id,unlocked)"
                                                            + " VALUES(?,?,?)")) {
                                            for (var e : catalog)
                                                if (!unlocked.containsKey(e.id())
                                                        && values.getOrDefault(e.metric(), 0L)
                                                                >= e.target()) {
                                                    s.setString(1, owner.toString());
                                                    s.setString(2, e.id());
                                                    s.setLong(3, now);
                                                    if (s.executeUpdate() == 1) {
                                                        unlocked.put(e.id(), now);
                                                        added.add(e.id());
                                                    }
                                                }
                                        }
                                        c.commit();
                                        return new Progress(
                                                Map.copyOf(values),
                                                Map.copyOf(unlocked),
                                                List.copyOf(added));
                                    } catch (SQLException | RuntimeException e) {
                                        try {
                                            c.rollback();
                                        } catch (SQLException failure) {
                                            e.addSuppressed(failure);
                                        }
                                        throw new DatabaseManager.DatabaseException(e);
                                    }
                                }));
    }

    private static long saturatedAdd(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    private static boolean table(Connection c, String name) throws SQLException {
        try (var s =
                c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            s.setString(1, name);
            try (var r = s.executeQuery()) {
                return r.next();
            }
        }
    }

    private static void optional(
            Connection c,
            Map<String, Long> result,
            String metric,
            String table,
            String sql,
            UUID owner)
            throws SQLException {
        if (table(c, table)) result.put(metric, scalar(c, sql, owner));
    }

    private static long scalar(Connection c, String sql, UUID owner) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            s.setString(1, owner.toString());
            try (var r = s.executeQuery()) {
                return r.next() ? Math.max(0, r.getLong(1)) : 0;
            }
        }
    }

    private static Map<String, Long> read(Connection c, String sql, UUID owner)
            throws SQLException {
        Map<String, Long> result = new HashMap<>();
        try (var s = c.prepareStatement(sql)) {
            s.setString(1, owner.toString());
            try (var r = s.executeQuery()) {
                while (r.next()) result.put(r.getString(1), r.getLong(2));
            }
        }
        return result;
    }
}
