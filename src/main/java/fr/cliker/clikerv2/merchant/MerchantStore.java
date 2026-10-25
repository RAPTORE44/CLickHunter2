package fr.cliker.clikerv2.merchant;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.config.StoredLocation;

import org.bukkit.Location;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MerchantStore {
    private final DatabaseManager database;

    public MerchantStore(DatabaseManager database) {
        this.database = database;
    }

    public record MerchantPoint(String id, String mobId, Location location) {}

    public CompletableFuture<Void> save(String id, String mobId, Location location) {
        StoredLocation saved = StoredLocation.capture(location);
        return database.submit(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "INSERT INTO merchants (id, mob_id, world, x, y, z, yaw) VALUES"
                                            + " (?, ?, ?, ?, ?, ?, ?)")) {
                        statement.setString(1, id);
                        statement.setString(2, mobId);
                        statement.setString(3, saved.world());
                        statement.setDouble(4, saved.x());
                        statement.setDouble(5, saved.y());
                        statement.setDouble(6, saved.z());
                        statement.setFloat(7, saved.yaw());
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
                            connection.prepareStatement("DELETE FROM merchants WHERE id = ?")) {
                        statement.setString(1, id);
                        return statement.executeUpdate() > 0;
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                });
    }

    private record Row(String id, String value, StoredLocation position, boolean boss) {}

    public CompletableFuture<List<MerchantPoint>> loadAll() {
        return database.submit(
                        c -> {
                            List<Row> rows = new ArrayList<>();
                            try (var statement = c.prepareStatement("SELECT * FROM merchants");
                                    var result = statement.executeQuery()) {
                                while (result.next()) {
                                    try {
                                        rows.add(
                                                new Row(
                                                        result.getString("id"),
                                                        result.getString("mob_id"),
                                                        StoredLocation.read(result, true, false),
                                                        false));
                                    } catch (RuntimeException invalid) {
                                        database.invalidRecord(
                                                "merchants", result.getString("id"), invalid);
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
                                            List<MerchantPoint> points = new ArrayList<>();
                                            for (var row : rows) {
                                                Location location = row.position().resolve();
                                                if (location == null) continue;
                                                points.add(
                                                        new MerchantPoint(
                                                                row.id(), row.value(), location));
                                            }
                                            return points;
                                        }));
    }
}
