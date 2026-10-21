package fr.cliker.clikerv2.spawn;

import fr.cliker.clikerv2.DatabaseManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public final class SpawnStore {
    private final DatabaseManager database;

    public SpawnStore(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Void> save(Location location) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO server_spawn (id, world, x, y, z, yaw, pitch) VALUES (1, ?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT(id) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch")) {
                statement.setString(1, location.getWorld().getName());
                statement.setDouble(2, location.getX());
                statement.setDouble(3, location.getY());
                statement.setDouble(4, location.getZ());
                statement.setFloat(5, location.getYaw());
                statement.setFloat(6, location.getPitch());
                statement.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }

    public CompletableFuture<Location> load() {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT world, x, y, z, yaw, pitch FROM server_spawn WHERE id = 1");
                 ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                World world = Bukkit.getWorld(rows.getString("world"));
                if (world == null) return null;
                return new Location(world, rows.getDouble("x"), rows.getDouble("y"), rows.getDouble("z"), rows.getFloat("yaw"), rows.getFloat("pitch"));
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        });
    }
}
