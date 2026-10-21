package fr.cliker.clikerv2.bag;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BagStore {
    private final DatabaseManager database;

    public BagStore(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<String> load(UUID uuid) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT items FROM bags WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? rows.getString("items") : null;
                }
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        });
    }

    public CompletableFuture<Void> save(UUID uuid, String itemsYaml) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO bags (uuid, items) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET items = excluded.items")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, itemsYaml);
                statement.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }
}
