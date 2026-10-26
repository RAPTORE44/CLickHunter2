package fr.cliker.clikerv2.prestige;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PrestigeStore {
    private final DatabaseManager database;

    public PrestigeStore(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Integer> getLevel(UUID uuid) {
        return database.submit(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "SELECT level FROM player_prestige WHERE uuid = ?")) {
                        statement.setString(1, uuid.toString());
                        try (ResultSet result = statement.executeQuery()) {
                            long level = result.next() ? result.getLong("level") : 0;
                            if (level < 0 || level > 100)
                                throw new SQLException(
                                        "Prestige hors limites pour " + uuid + " : " + level);
                            return (int) level;
                        }
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                });
    }

    public record Reset(int level, long earnedXP) {}

    public CompletableFuture<Boolean> advance(UUID owner, int expected, long requiredKills) {
        if (expected < 0 || expected >= 100 || requiredKills < 0)
            return CompletableFuture.completedFuture(false);
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        int current = 0;
                        long kills = 0, xp = 0;
                        try (var s =
                                c.prepareStatement(
                                        "SELECT level FROM player_prestige WHERE uuid=?")) {
                            s.setString(1, owner.toString());
                            try (var rs = s.executeQuery()) {
                                if (rs.next()) {
                                    long saved = rs.getLong(1);
                                    if (saved < 0 || saved > 100)
                                        throw new SQLException(
                                                "Prestige hors limites pour "
                                                        + owner
                                                        + " : "
                                                        + saved);
                                    current = (int) saved;
                                }
                            }
                        }
                        try (var s =
                                c.prepareStatement(
                                        "SELECT total FROM player_mob_kills WHERE uuid=?")) {
                            s.setString(1, owner.toString());
                            try (var rs = s.executeQuery()) {
                                if (rs.next()) kills = rs.getLong(1);
                            }
                        }
                        if (current != expected || current >= 100 || kills < requiredKills) {
                            c.rollback();
                            return false;
                        }
                        try (var s =
                                c.prepareStatement("SELECT total FROM earned_xp WHERE owner=?")) {
                            s.setString(1, owner.toString());
                            try (var rs = s.executeQuery()) {
                                if (rs.next()) xp = rs.getLong(1);
                            }
                        }
                        try (var s =
                                c.prepareStatement(
                                        "INSERT INTO player_prestige(uuid,level) VALUES(?,?) ON"
                                                + " CONFLICT(uuid) DO UPDATE SET"
                                                + " level=excluded.level")) {
                            s.setString(1, owner.toString());
                            s.setInt(2, current + 1);
                            s.executeUpdate();
                        }
                        try (var s =
                                c.prepareStatement(
                                        "INSERT INTO prestige_resets(owner,level,earned_xp)"
                                            + " VALUES(?,?,?) ON CONFLICT(owner) DO UPDATE SET"
                                            + " level=excluded.level,earned_xp=excluded.earned_xp")) {
                            s.setString(1, owner.toString());
                            s.setInt(2, current + 1);
                            s.setLong(3, xp);
                            s.executeUpdate();
                        }
                        c.commit();
                        return true;
                    } catch (SQLException e) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Reset> pendingReset(UUID owner) {
        return database.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "SELECT level,earned_xp FROM prestige_resets WHERE owner=?")) {
                        s.setString(1, owner.toString());
                        try (var rs = s.executeQuery()) {
                            return rs.next() ? new Reset(rs.getInt(1), rs.getLong(2)) : null;
                        }
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Void> acknowledgeReset(UUID owner, int level) {
        return database.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "DELETE FROM prestige_resets WHERE owner=? AND level=?")) {
                        s.setString(1, owner.toString());
                        s.setInt(2, level);
                        s.executeUpdate();
                        return null;
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Void> setLevel(UUID uuid, int level) {
        if (level < 0 || level > 100)
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Prestige hors limites"));
        return database.submit(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "INSERT INTO player_prestige (uuid, level) VALUES (?, ?) ON"
                                        + " CONFLICT(uuid) DO UPDATE SET level = excluded.level")) {
                        statement.setString(1, uuid.toString());
                        statement.setInt(2, level);
                        statement.executeUpdate();
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    return null;
                });
    }
}
