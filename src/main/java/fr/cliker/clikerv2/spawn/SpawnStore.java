package fr.cliker.clikerv2.spawn;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.config.StoredLocation;

import org.bukkit.Location;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public final class SpawnStore {
    private final DatabaseManager database;

    public SpawnStore(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Void> save(Location location) {
        StoredLocation saved = StoredLocation.capture(location);
        return database.submit(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "INSERT INTO server_spawn (id, world, x, y, z, yaw, pitch)"
                                        + " VALUES (1, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE"
                                        + " SET world = excluded.world, x = excluded.x, y ="
                                        + " excluded.y, z = excluded.z, yaw = excluded.yaw, pitch ="
                                        + " excluded.pitch")) {
                        statement.setString(1, saved.world());
                        statement.setDouble(2, saved.x());
                        statement.setDouble(3, saved.y());
                        statement.setDouble(4, saved.z());
                        statement.setFloat(5, saved.yaw());
                        statement.setFloat(6, saved.pitch());
                        statement.executeUpdate();
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    return null;
                });
    }

    public CompletableFuture<Location> load() {
        return database.submit(
                        c -> {
                            try (var statement =
                                            c.prepareStatement(
                                                    "SELECT * FROM server_spawn WHERE id=1");
                                    var rows = statement.executeQuery()) {
                                if (!rows.next()) return null;
                                try {
                                    return StoredLocation.read(rows, true, true);
                                } catch (RuntimeException invalid) {
                                    database.invalidRecord("server_spawn", "1", invalid);
                                    return null;
                                }
                            } catch (SQLException error) {
                                throw new DatabaseManager.DatabaseException(error);
                            }
                        })
                .thenCompose(
                        saved ->
                                database.onServerThread(
                                        () -> saved == null ? null : saved.resolve()));
    }
}
