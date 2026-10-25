package fr.cliker.clikerv2.station;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.config.StoredLocation;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class StationStore {
    private final DatabaseManager database;

    public StationStore(DatabaseManager database) {
        this.database = database;
    }

    public record StationPoint(String id, StationType type, Location location) {}

    public static String idFor(Block block) {
        return block.getWorld().getName()
                + ":"
                + block.getX()
                + ":"
                + block.getY()
                + ":"
                + block.getZ();
    }

    public CompletableFuture<Void> save(String id, StationType type, Block block) {
        StoredLocation saved = StoredLocation.capture(block.getLocation());
        return database.submit(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "INSERT INTO stations (id, type, world, x, y, z) VALUES (?, ?,"
                                            + " ?, ?, ?, ?)")) {
                        statement.setString(1, id);
                        statement.setString(2, type.name());
                        statement.setString(3, saved.world());
                        statement.setInt(4, (int) saved.x());
                        statement.setInt(5, (int) saved.y());
                        statement.setInt(6, (int) saved.z());
                        statement.executeUpdate();
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    return null;
                });
    }

    public CompletableFuture<Void> removeById(String id) {
        return database.submit(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement("DELETE FROM stations WHERE id = ?")) {
                        statement.setString(1, id);
                        statement.executeUpdate();
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    return null;
                });
    }

    private record Row(String id, String value, StoredLocation position, boolean boss) {}

    public CompletableFuture<List<StationPoint>> loadAll() {
        return database.submit(
                        c -> {
                            List<Row> rows = new ArrayList<>();
                            try (var statement = c.prepareStatement("SELECT * FROM stations");
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
                                                "stations", result.getString("id"), invalid);
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
                                            List<StationPoint> points = new ArrayList<>();
                                            for (var row : rows) {
                                                Location location = row.position().resolve();
                                                if (location == null) continue;
                                                StationType type;
                                                try {
                                                    type = StationType.valueOf(row.value());
                                                } catch (IllegalArgumentException error) {
                                                    continue;
                                                }
                                                points.add(
                                                        new StationPoint(row.id(), type, location));
                                            }
                                            return points;
                                        }));
    }
}
