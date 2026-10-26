package fr.cliker.clikerv2.social;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.hunting.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class CommunityStore {
    public record Profile(
            String tag,
            String color,
            long bugs,
            long kills,
            long bosses,
            long popularity,
            long prestige) {}

    public record Welcome(UUID owner, String name, long at) {}

    private final DatabaseManager db;
    private final RewardLedger ledger;

    public CommunityStore(DatabaseManager db, RewardLedger ledger) {
        this.db = db;
        this.ledger = ledger;
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    private <T> CompletableFuture<T> run(Work<T> work) {
        return db.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        T result = work.run(c);
                        c.commit();
                        return result;
                    } catch (SQLException | RuntimeException e) {
                        try {
                            c.rollback();
                        } catch (SQLException failure) {
                            e.addSuppressed(failure);
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Welcome> initialize() {
        return run(
                c -> {
                    try (var s = c.createStatement()) {
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS social_popularity(owner TEXT PRIMARY"
                                    + " KEY,points INTEGER NOT NULL DEFAULT 0 CHECK(points>=0))");
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS community_profiles(owner TEXT PRIMARY"
                                    + " KEY,tag TEXT NOT NULL DEFAULT '',color TEXT NOT NULL"
                                    + " DEFAULT 'white',bugs INTEGER NOT NULL DEFAULT 0"
                                    + " CHECK(bugs>=0))");
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS community_newcomer(id INTEGER PRIMARY"
                                    + " KEY CHECK(id=1),owner TEXT NOT NULL,name TEXT NOT"
                                    + " NULL,joined_at INTEGER NOT NULL)");
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS community_welcomes(newcomer TEXT NOT"
                                    + " NULL,owner TEXT NOT NULL,PRIMARY KEY(newcomer,owner))");
                        try (var r =
                                s.executeQuery(
                                        "SELECT owner,name,joined_at FROM community_newcomer WHERE"
                                            + " id=1")) {
                            return r.next()
                                    ? new Welcome(
                                            UUID.fromString(r.getString(1)),
                                            r.getString(2),
                                            r.getLong(3))
                                    : null;
                        }
                    }
                });
    }

    public CompletableFuture<Map<UUID, Profile>> profiles(Collection<UUID> owners) {
        var copy = List.copyOf(owners);
        return run(
                c -> {
                    Map<UUID, Profile> result = new HashMap<>();
                    String sql =
                            "SELECT"
                                + " COALESCE(p.tag,''),COALESCE(p.color,'white'),COALESCE(p.bugs,0),COALESCE((SELECT"
                                + " total FROM player_mob_kills WHERE uuid=?),0),COALESCE((SELECT"
                                + " SUM(kills) FROM bestiary_stats WHERE owner=? AND"
                                + " boss=1),0),COALESCE((SELECT points FROM social_popularity WHERE"
                                + " owner=?),0),COALESCE((SELECT level FROM player_prestige WHERE"
                                + " uuid=?),0) FROM (SELECT 1) LEFT JOIN community_profiles p ON"
                                + " p.owner=?";
                    try (var s = c.prepareStatement(sql)) {
                        for (UUID owner : copy) {
                            for (int i = 1; i <= 5; i++) s.setString(i, owner.toString());
                            try (var r = s.executeQuery()) {
                                r.next();
                                result.put(
                                        owner,
                                        new Profile(
                                                r.getString(1),
                                                r.getString(2),
                                                r.getLong(3),
                                                r.getLong(4),
                                                r.getLong(5),
                                                r.getLong(6),
                                                r.getLong(7)));
                            }
                        }
                    }
                    return Map.copyOf(result);
                });
    }

    public CompletableFuture<Void> preference(UUID owner, String tag, String color) {
        return run(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "INSERT OR IGNORE INTO community_profiles(owner) VALUES(?)")) {
                        s.setString(1, owner.toString());
                        s.executeUpdate();
                    }
                    if (tag != null)
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE community_profiles SET tag=? WHERE owner=?")) {
                            s.setString(1, tag);
                            s.setString(2, owner.toString());
                            s.executeUpdate();
                        }
                    if (color != null)
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE community_profiles SET color=? WHERE owner=?")) {
                            s.setString(1, color);
                            s.setString(2, owner.toString());
                            s.executeUpdate();
                        }
                    return null;
                });
    }

    public CompletableFuture<Void> bugs(UUID owner, long count) {
        if (count < 0 || count > 1000000000)
            throw new IllegalArgumentException("Compteur invalide");
        return run(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "INSERT INTO community_profiles(owner,bugs) VALUES(?,?) ON"
                                        + " CONFLICT(owner) DO UPDATE SET bugs=excluded.bugs")) {
                        s.setString(1, owner.toString());
                        s.setLong(2, count);
                        s.executeUpdate();
                    }
                    return null;
                });
    }

    public CompletableFuture<Void> newcomer(Welcome target) {
        return run(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "INSERT INTO community_newcomer(id,owner,name,joined_at)"
                                        + " VALUES(1,?,?,?) ON CONFLICT(id) DO UPDATE SET"
                                        + " owner=excluded.owner,name=excluded.name,joined_at=excluded.joined_at")) {
                        s.setString(1, target.owner().toString());
                        s.setString(2, target.name());
                        s.setLong(3, target.at());
                        s.executeUpdate();
                    }
                    return null;
                });
    }

    /** 0 expiré/invalide, 1 déjà souhaité, 2 récompense validée exactement une fois. */
    public CompletableFuture<Integer> welcome(
            UUID sender, String name, UUID expected, long now, long window, Gains gains) {
        return run(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "SELECT owner,joined_at FROM community_newcomer WHERE id=1")) {
                        try (var r = s.executeQuery()) {
                            if (!r.next()
                                    || !r.getString(1).equals(expected.toString())
                                    || sender.equals(expected)
                                    || now < r.getLong(2)
                                    || now - r.getLong(2) > window) return 0;
                        }
                    }
                    try (var s =
                            c.prepareStatement(
                                    "INSERT OR IGNORE INTO community_welcomes(newcomer,owner)"
                                        + " VALUES(?,?)")) {
                        s.setString(1, expected.toString());
                        s.setString(2, sender.toString());
                        if (s.executeUpdate() == 0) return 1;
                    }
                    if (!ledger.grant(c, "welcome:" + expected + ":" + sender, sender, name, gains))
                        throw new SQLException("Accueil déjà récompensé");
                    return 2;
                });
    }
}
