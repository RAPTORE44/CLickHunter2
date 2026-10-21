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
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT level FROM player_prestige WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt("level") : 0;
                }
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        });
    }

    public CompletableFuture<Void> setLevel(UUID uuid, int level) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_prestige (uuid, level) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET level = excluded.level")) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, level);
                statement.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }
}
