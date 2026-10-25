package fr.cliker.clikerv2.upgrade;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class UpgradeStore {
    private final DatabaseManager database;

    public UpgradeStore(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Map<UpgradeType, Integer>> getLevels(UUID uuid) {
        return database.submit(
                connection -> {
                    Map<UpgradeType, Integer> levels = new EnumMap<>(UpgradeType.class);
                    for (UpgradeType type : UpgradeType.values()) levels.put(type, 0);
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "SELECT richesse, fortune, sagesse, force, vitesse, vitalite,"
                                            + " resistance, cadence FROM player_upgrades WHERE uuid"
                                            + " = ?")) {
                        statement.setString(1, uuid.toString());
                        try (ResultSet result = statement.executeQuery()) {
                            if (result.next()) {
                                levels.put(UpgradeType.RICHESSE, result.getInt("richesse"));
                                levels.put(UpgradeType.FORTUNE, result.getInt("fortune"));
                                levels.put(UpgradeType.SAGESSE, result.getInt("sagesse"));
                                levels.put(UpgradeType.FORCE, result.getInt("force"));
                                levels.put(UpgradeType.VITESSE, result.getInt("vitesse"));
                                levels.put(UpgradeType.VITALITE, result.getInt("vitalite"));
                                levels.put(UpgradeType.RESISTANCE, result.getInt("resistance"));
                                levels.put(UpgradeType.CADENCE, result.getInt("cadence"));
                            }
                        }
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    return levels;
                });
    }

    public CompletableFuture<Void> setLevel(UUID uuid, UpgradeType type, int level) {
        return database.submit(
                connection -> {
                    try (PreparedStatement insert =
                            connection.prepareStatement(
                                    "INSERT OR IGNORE INTO player_upgrades (uuid) VALUES (?)")) {
                        insert.setString(1, uuid.toString());
                        insert.executeUpdate();
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    try (PreparedStatement update =
                            connection.prepareStatement(
                                    "UPDATE player_upgrades SET "
                                            + column(type)
                                            + " = ? WHERE uuid = ?")) {
                        update.setInt(1, level);
                        update.setString(2, uuid.toString());
                        update.executeUpdate();
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    return null;
                });
    }

    /** Retourne le nouveau niveau, -1 si le solde manque, -2 si le niveau maximal est atteint. */
    public CompletableFuture<Integer> purchase(UUID uuid, UpgradeType type) {
        return purchase(uuid, type, false);
    }

    public CompletableFuture<Integer> purchase(UUID uuid, UpgradeType type, boolean maximum) {
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        try (var s =
                                c.prepareStatement(
                                        "INSERT OR IGNORE INTO player_upgrades(uuid) VALUES(?)")) {
                            s.setString(1, uuid.toString());
                            s.executeUpdate();
                        }
                        int current;
                        try (var s =
                                c.prepareStatement(
                                        "SELECT "
                                                + column(type)
                                                + " FROM player_upgrades WHERE uuid=?")) {
                            s.setString(1, uuid.toString());
                            try (var rs = s.executeQuery()) {
                                if (!rs.next())
                                    throw new SQLException("Améliorations introuvables");
                                current = rs.getInt(1);
                            }
                        }
                        if (current >= UpgradeType.MAX_LEVEL) {
                            c.rollback();
                            return -2;
                        }
                        long balance = 0;
                        try (var s =
                                c.prepareStatement(
                                        "SELECT gemmes_sombres FROM player_balances WHERE"
                                                + " uuid=?")) {
                            s.setString(1, uuid.toString());
                            try (var rs = s.executeQuery()) {
                                if (rs.next()) balance = rs.getLong(1);
                            }
                        }
                        int next = current;
                        long price = 0;
                        do {
                            long step = Math.multiplyExact(UpgradeType.costForLevel(next + 1), 10);
                            if (step > balance - price) break;
                            price = Math.addExact(price, step);
                            next++;
                        } while (maximum && next < UpgradeType.MAX_LEVEL);
                        if (next == current) {
                            c.rollback();
                            return -1;
                        }
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE player_balances SET gemmes_sombres=gemmes_sombres-?"
                                                + " WHERE uuid=? AND gemmes_sombres>=?")) {
                            s.setLong(1, price);
                            s.setString(2, uuid.toString());
                            s.setLong(3, price);
                            if (s.executeUpdate() != 1) {
                                c.rollback();
                                return -1;
                            }
                        }
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE player_upgrades SET "
                                                + column(type)
                                                + "=? WHERE uuid=?")) {
                            s.setInt(1, next);
                            s.setString(2, uuid.toString());
                            s.executeUpdate();
                        }
                        c.commit();
                        return next;
                    } catch (SQLException e) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    private static String column(UpgradeType type) {
        return type.id();
    }
}
