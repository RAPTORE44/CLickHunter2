package fr.cliker.clikerv2.hologram;

import fr.cliker.clikerv2.DatabaseManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO holograms (id, type, world, x, y, z) VALUES (?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, type);
                statement.setString(3, location.getWorld().getName());
                statement.setDouble(4, location.getX());
                statement.setDouble(5, location.getY());
                statement.setDouble(6, location.getZ());
                statement.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }

    public CompletableFuture<Boolean> removeById(String id) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM holograms WHERE id = ?")) {
                statement.setString(1, id);
                return statement.executeUpdate() > 0;
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        });
    }

    public CompletableFuture<List<HologramPoint>> loadAll() {
        return database.submit(connection -> {
            List<HologramPoint> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT id, type, world, x, y, z FROM holograms");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    World world = Bukkit.getWorld(rows.getString("world"));
                    if (world == null) continue;
                    Location location = new Location(world, rows.getDouble("x"), rows.getDouble("y"), rows.getDouble("z"));
                    result.add(new HologramPoint(rows.getString("id"), rows.getString("type"), location));
                }
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return result;
        });
    }
}
