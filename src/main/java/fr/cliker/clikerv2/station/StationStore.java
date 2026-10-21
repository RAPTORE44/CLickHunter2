package fr.cliker.clikerv2.station;

import fr.cliker.clikerv2.DatabaseManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    public CompletableFuture<Void> save(String id, StationType type, Block block) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO stations (id, type, world, x, y, z) VALUES (?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, type.name());
                statement.setString(3, block.getWorld().getName());
                statement.setInt(4, block.getX());
                statement.setInt(5, block.getY());
                statement.setInt(6, block.getZ());
                statement.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }

    public CompletableFuture<Void> removeById(String id) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM stations WHERE id = ?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }

    public CompletableFuture<List<StationPoint>> loadAll() {
        return database.submit(connection -> {
            List<StationPoint> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT id, type, world, x, y, z FROM stations");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    World world = Bukkit.getWorld(rows.getString("world"));
                    if (world == null) continue;
                    StationType type;
                    try { type = StationType.valueOf(rows.getString("type")); } catch (IllegalArgumentException invalid) { continue; }
                    Location location = new Location(world, rows.getInt("x"), rows.getInt("y"), rows.getInt("z"));
                    result.add(new StationPoint(rows.getString("id"), type, location));
                }
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return result;
        });
    }
}
