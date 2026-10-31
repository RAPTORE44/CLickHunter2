package fr.cliker.clikerv2.buyzone;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class BuyZoneStore {
    public record Access(Set<String> owned, Map<String, Boolean> overrides) {}

    private final DatabaseManager database;

    public BuyZoneStore(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Set<String>> load(UUID uuid) {
        return state(uuid).thenApply(Access::owned);
    }

    public CompletableFuture<Access> state(UUID uuid) {
        return database.submit(
                c -> {
                    try {
                        var owned = new HashSet<String>();
                        var overrides = new HashMap<String, Boolean>();
                        try (var s =
                                c.prepareStatement(
                                        "SELECT zone_id FROM buyzone_unlocks WHERE uuid=?")) {
                            s.setString(1, uuid.toString());
                            try (var r = s.executeQuery()) {
                                while (r.next()) owned.add(r.getString(1));
                            }
                        }
                        try (var s =
                                c.prepareStatement(
                                        "SELECT zone_id,allowed FROM buyzone_overrides WHERE"
                                            + " uuid=?")) {
                            s.setString(1, uuid.toString());
                            try (var r = s.executeQuery()) {
                                while (r.next()) overrides.put(r.getString(1), r.getBoolean(2));
                            }
                        }
                        return new Access(Set.copyOf(owned), Map.copyOf(overrides));
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Void> setAccess(
            UUID uuid, String zone, String permission, boolean allowed) {
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        try (var s =
                                c.prepareStatement(
                                        "INSERT INTO buyzone_overrides(uuid,zone_id,allowed)"
                                            + " VALUES(?,?,?) ON CONFLICT(uuid,zone_id) DO UPDATE"
                                            + " SET allowed=excluded.allowed")) {
                            s.setString(1, uuid.toString());
                            s.setString(2, zone);
                            s.setBoolean(3, allowed);
                            s.executeUpdate();
                        }
                        try (var s =
                                c.prepareStatement(
                                        allowed
                                                ? "INSERT INTO"
                                                      + " buyzone_unlocks(uuid,zone_id,permission)"
                                                      + " VALUES(?,?,?) ON CONFLICT(uuid,zone_id)"
                                                      + " DO UPDATE SET"
                                                      + " permission=excluded.permission"
                                                : "DELETE FROM buyzone_unlocks WHERE uuid=? AND"
                                                      + " zone_id=?")) {
                            s.setString(1, uuid.toString());
                            s.setString(2, zone);
                            if (allowed) s.setString(3, permission);
                            s.executeUpdate();
                        }
                        c.commit();
                        return null;
                    } catch (SQLException e) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<UUID> resolve(String name) {
        return database.submit(
                c -> {
                    try {
                        for (String sql :
                                List.of(
                                        "SELECT uuid FROM player_balances WHERE player_name=?"
                                            + " COLLATE NOCASE LIMIT 1",
                                        "SELECT uuid FROM referral_players WHERE name=? COLLATE"
                                            + " NOCASE ORDER BY last_seen DESC LIMIT 1")) {
                            try (var s = c.prepareStatement(sql)) {
                                s.setString(1, name);
                                try (var r = s.executeQuery()) {
                                    if (r.next()) return UUID.fromString(r.getString(1));
                                }
                            }
                        }
                        return null;
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }
}
