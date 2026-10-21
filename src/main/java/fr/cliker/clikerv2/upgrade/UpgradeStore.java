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
        return database.submit(connection -> {
            Map<UpgradeType, Integer> levels = new EnumMap<>(UpgradeType.class);
            for (UpgradeType type : UpgradeType.values()) levels.put(type, 0);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT richesse, fortune, sagesse, force, vitesse, vitalite, resistance, cadence FROM player_upgrades WHERE uuid = ?")) {
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
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return levels;
        });
    }

    public CompletableFuture<Void> setLevel(UUID uuid, UpgradeType type, int level) {
        return database.submit(connection -> {
            try (PreparedStatement insert = connection.prepareStatement("INSERT OR IGNORE INTO player_upgrades (uuid) VALUES (?)")) {
                insert.setString(1, uuid.toString());
                insert.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            try (PreparedStatement update = connection.prepareStatement("UPDATE player_upgrades SET " + column(type) + " = ? WHERE uuid = ?")) {
                update.setInt(1, level);
                update.setString(2, uuid.toString());
                update.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }

    private static String column(UpgradeType type) { return type.id(); }
}
