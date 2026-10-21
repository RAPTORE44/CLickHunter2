package fr.cliker.clikerv2.mobs;

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

public final class MobSpawnStore {
    private final DatabaseManager database;

    public MobSpawnStore(DatabaseManager database) {
        this.database = database;
    }

    public record SpawnPoint(String id, String mobId, Location location, boolean boss) {}

    public CompletableFuture<Void> save(String id, String mobId, Location location, boolean boss) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO mob_spawns (id, mob_id, world, x, y, z, yaw, boss) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, mobId);
                statement.setString(3, location.getWorld().getName());
                statement.setDouble(4, location.getX());
                statement.setDouble(5, location.getY());
                statement.setDouble(6, location.getZ());
                statement.setFloat(7, location.getYaw());
                statement.setInt(8, boss ? 1 : 0);
                statement.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }

    public CompletableFuture<Boolean> removeById(String id) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM mob_spawns WHERE id = ?")) {
                statement.setString(1, id);
                return statement.executeUpdate() > 0;
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        });
    }

    public CompletableFuture<Integer> removeAllForMob(String mobId) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM mob_spawns WHERE mob_id = ?")) {
                statement.setString(1, mobId);
                return statement.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        });
    }

    public CompletableFuture<List<SpawnPoint>> loadAll() {
        return database.submit(connection -> {
            List<SpawnPoint> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT id, mob_id, world, x, y, z, yaw, boss FROM mob_spawns");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    World world = Bukkit.getWorld(rows.getString("world"));
                    if (world == null) continue;
                    Location location = new Location(world, rows.getDouble("x"), rows.getDouble("y"), rows.getDouble("z"), rows.getFloat("yaw"), 0f);
                    result.add(new SpawnPoint(rows.getString("id"), rows.getString("mob_id"), location, rows.getInt("boss") != 0));
                }
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return result;
        });
    }
}
