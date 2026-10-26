package fr.cliker.clikerv2.social;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.hunting.Gains;
import fr.cliker.clikerv2.hunting.RewardLedger;
import fr.cliker.clikerv2.rewards.MailStore;
import fr.cliker.clikerv2.rewards.OutgoingStore;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Les liens réciproques, frais et recommandations sont validés dans une seule transaction. */
public final class SocialStore {
    public record Bond(String id, UUID partner, long since) {}

    public record Death(String world, double x, double y, double z, float yaw, float pitch) {}

    public record Recommendation(boolean accepted, long count, long waitMillis) {}

    private final DatabaseManager db;
    private final RewardLedger ledger;

    public SocialStore(DatabaseManager db, RewardLedger ledger) {
        this.db = db;
        this.ledger = ledger;
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    private <T> CompletableFuture<T> transaction(Work<T> action) {
        return db.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        T result = action.run(c);
                        c.commit();
                        return result;
                    } catch (SQLException | RuntimeException error) {
                        try {
                            c.rollback();
                        } catch (SQLException e) {
                            error.addSuppressed(e);
                        }
                        throw new DatabaseManager.DatabaseException(error);
                    }
                });
    }

    public CompletableFuture<Void> initialize() {
        return transaction(
                c -> {
                    try (var s = c.createStatement()) {
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS social_marriages(owner TEXT PRIMARY"
                                    + " KEY,partner TEXT NOT NULL UNIQUE,bond TEXT NOT NULL,since"
                                    + " INTEGER NOT NULL,CHECK(owner<>partner))");
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS social_deaths(owner TEXT PRIMARY"
                                    + " KEY,world TEXT NOT NULL,x REAL NOT NULL,y REAL NOT NULL,z"
                                    + " REAL NOT NULL,yaw REAL NOT NULL,pitch REAL NOT NULL)");
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS social_popularity(owner TEXT PRIMARY"
                                    + " KEY,points INTEGER NOT NULL DEFAULT 0 CHECK(points>=0))");
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS social_recommenders(owner TEXT PRIMARY"
                                        + " KEY,last_at INTEGER NOT NULL)");
                    }
                    return null;
                });
    }

    private static Bond bond(Connection c, UUID owner) throws SQLException {
        try (var s =
                c.prepareStatement(
                        "SELECT bond,partner,since FROM social_marriages WHERE owner=?")) {
            s.setString(1, owner.toString());
            try (var r = s.executeQuery()) {
                return r.next()
                        ? new Bond(r.getString(1), UUID.fromString(r.getString(2)), r.getLong(3))
                        : null;
            }
        }
    }

    public CompletableFuture<Bond> bond(UUID owner) {
        return transaction(c -> bond(c, owner));
    }

    public CompletableFuture<Boolean> marry(UUID a, UUID b, long now) {
        if (a.equals(b)) return CompletableFuture.completedFuture(false);
        return transaction(
                c -> {
                    if (bond(c, a) != null || bond(c, b) != null) return false;
                    String id = UUID.randomUUID().toString();
                    try (var s =
                            c.prepareStatement(
                                    "INSERT INTO social_marriages(owner,partner,bond,since)"
                                            + " VALUES(?,?,?,?)")) {
                        for (var pair : List.of(List.of(a, b), List.of(b, a))) {
                            s.setString(1, pair.get(0).toString());
                            s.setString(2, pair.get(1).toString());
                            s.setString(3, id);
                            s.setLong(4, now);
                            s.executeUpdate();
                        }
                    }
                    return true;
                });
    }

    /** 0 = lien périmé ; 1 = fonds insuffisants ; 2 = divorce payé et validé. */
    public CompletableFuture<Integer> divorce(UUID owner, String expectedBond, long cost) {
        if (cost < 0) throw new IllegalArgumentException("Prix négatif");
        return transaction(
                c -> {
                    Bond current = bond(c, owner);
                    if (current == null || !current.id().equals(expectedBond)) return 0;
                    if (cost > 0)
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE player_balances SET ecus=ecus-? WHERE uuid=? AND"
                                                + " ecus>=?")) {
                            s.setLong(1, cost);
                            s.setString(2, owner.toString());
                            s.setLong(3, cost);
                            if (s.executeUpdate() != 1) return 1;
                        }
                    try (var s = c.prepareStatement("DELETE FROM social_marriages WHERE bond=?")) {
                        s.setString(1, expectedBond);
                        if (s.executeUpdate() != 2)
                            throw new SQLException("Lien matrimonial incohérent");
                    }
                    return 2;
                });
    }

    public CompletableFuture<Void> gift(
            UUID owner, UUID target, String expectedBond, String receipt, String payload) {
        return transaction(
                c -> {
                    Bond current = bond(c, owner);
                    if (current == null
                            || !current.partner().equals(target)
                            || !current.id().equals(expectedBond))
                        throw new SQLException("Mariage modifié pendant le cadeau");
                    OutgoingStore.commit(c, receipt, owner, payload);
                    MailStore.insert(c, target, "marry-gift:" + receipt, payload);
                    return null;
                });
    }

    public CompletableFuture<Void> death(UUID owner, Death at) {
        return transaction(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "INSERT INTO social_deaths(owner,world,x,y,z,yaw,pitch)"
                                        + " VALUES(?,?,?,?,?,?,?) ON CONFLICT(owner) DO UPDATE SET"
                                        + " world=excluded.world,x=excluded.x,y=excluded.y,z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch")) {
                        s.setString(1, owner.toString());
                        s.setString(2, at.world());
                        s.setDouble(3, at.x());
                        s.setDouble(4, at.y());
                        s.setDouble(5, at.z());
                        s.setFloat(6, at.yaw());
                        s.setFloat(7, at.pitch());
                        s.executeUpdate();
                    }
                    return null;
                });
    }

    public CompletableFuture<Death> death(UUID owner) {
        return transaction(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "SELECT world,x,y,z,yaw,pitch FROM social_deaths WHERE"
                                            + " owner=?")) {
                        s.setString(1, owner.toString());
                        try (var r = s.executeQuery()) {
                            return r.next()
                                    ? new Death(
                                            r.getString(1),
                                            r.getDouble(2),
                                            r.getDouble(3),
                                            r.getDouble(4),
                                            r.getFloat(5),
                                            r.getFloat(6))
                                    : null;
                        }
                    }
                });
    }

    private static long points(Connection c, UUID owner) throws SQLException {
        try (var s = c.prepareStatement("SELECT points FROM social_popularity WHERE owner=?")) {
            s.setString(1, owner.toString());
            try (var r = s.executeQuery()) {
                return r.next() ? r.getLong(1) : 0;
            }
        }
    }

    public CompletableFuture<Long> points(UUID owner) {
        return transaction(c -> points(c, owner));
    }

    public CompletableFuture<Recommendation> recommend(
            UUID owner,
            UUID target,
            String targetName,
            long playedTicks,
            long requiredTicks,
            long now,
            long cooldown,
            long xp) {
        if (owner.equals(target)
                || playedTicks < requiredTicks
                || cooldown < 1
                || xp < 0
                || now < 0)
            return CompletableFuture.completedFuture(new Recommendation(false, 0, 0));
        return transaction(
                c -> {
                    long old = points(c, target);
                    try (var s =
                            c.prepareStatement(
                                    "SELECT last_at FROM social_recommenders WHERE owner=?")) {
                        s.setString(1, owner.toString());
                        try (var r = s.executeQuery()) {
                            if (r.next()) {
                                long last = r.getLong(1);
                                long elapsed = now >= last ? now - last : 0;
                                if (elapsed < cooldown)
                                    return new Recommendation(false, old, cooldown - elapsed);
                            }
                        }
                    }
                    if (old == Long.MAX_VALUE)
                        throw new SQLException("Popularité maximale atteinte");
                    try (var s =
                            c.prepareStatement(
                                    "INSERT INTO social_popularity(owner,points) VALUES(?,1) ON"
                                            + " CONFLICT(owner) DO UPDATE SET points=points+1")) {
                        s.setString(1, target.toString());
                        s.executeUpdate();
                    }
                    try (var s =
                            c.prepareStatement(
                                    "INSERT INTO social_recommenders(owner,last_at) VALUES(?,?) ON"
                                            + " CONFLICT(owner) DO UPDATE SET"
                                            + " last_at=excluded.last_at")) {
                        s.setString(1, owner.toString());
                        s.setLong(2, now);
                        s.executeUpdate();
                    }
                    if (!ledger.grant(
                            c,
                            "recommendation:" + UUID.randomUUID(),
                            target,
                            targetName,
                            Gains.of(0, 0, 0, xp)))
                        throw new SQLException("Récompense déjà attribuée");
                    return new Recommendation(true, old + 1, 0);
                });
    }
}
