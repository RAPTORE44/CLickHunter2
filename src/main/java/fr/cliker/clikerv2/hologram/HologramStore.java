package fr.cliker.clikerv2.hologram;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.config.StoredLocation;

import org.bukkit.Location;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class HologramStore {
    private final DatabaseManager database;

    public HologramStore(DatabaseManager database) {
        this.database = database;
    }

    public record HologramPoint(String id, String type, Location location) {}

    public CompletableFuture<Void> save(String id, String type, Location location) {
        StoredLocation saved = StoredLocation.capture(location);
        return database.submit(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "INSERT INTO holograms (id, type, world, x, y, z) VALUES (?, ?,"
                                            + " ?, ?, ?, ?)")) {
                        statement.setString(1, id);
                        statement.setString(2, type);
                        statement.setString(3, saved.world());
                        statement.setDouble(4, saved.x());
                        statement.setDouble(5, saved.y());
                        statement.setDouble(6, saved.z());
                        statement.executeUpdate();
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    return null;
                });
    }

    public CompletableFuture<Boolean> removeById(String id) {
        return database.submit(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement("DELETE FROM holograms WHERE id = ?")) {
                        statement.setString(1, id);
                        return statement.executeUpdate() > 0;
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                });
    }

    private record Row(String id, String value, StoredLocation position, boolean boss) {}

    public CompletableFuture<List<HologramPoint>> loadAll() {
        return database.submit(
                        c -> {
                            List<Row> rows = new ArrayList<>();
                            try (var statement = c.prepareStatement("SELECT * FROM holograms");
                                    var result = statement.executeQuery()) {
                                while (result.next()) {
                                    try {
                                        rows.add(
                                                new Row(
                                                        result.getString("id"),
                                                        result.getString("type"),
                                                        StoredLocation.read(result, false, false),
                                                        false));
                                    } catch (RuntimeException invalid) {
                                        database.invalidRecord(
                                                "holograms", result.getString("id"), invalid);
                                    }
                                }
                            } catch (SQLException error) {
                                throw new DatabaseManager.DatabaseException(error);
                            }
                            return rows;
                        })
                .thenCompose(
                        rows ->
                                database.onServerThread(
                                        () -> {
                                            List<HologramPoint> points = new ArrayList<>();
                                            for (var row : rows) {
                                                Location location = row.position().resolve();
                                                if (location == null) continue;
                                                points.add(
                                                        new HologramPoint(
                                                                row.id(), row.value(), location));
                                            }
                                            return points;
                                        }));
    }
}
