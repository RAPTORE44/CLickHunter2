package fr.cliker.clikerv2.mobs;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MobKillStore {
    private final DatabaseManager database;

    public MobKillStore(DatabaseManager database) {
        this.database = database;
    }

    public record Entry(String playerName, long total) {}

    /** Classement des kills : le nom vient de player_balances (toujours rempli en parallèle des kills). */
    public CompletableFuture<List<Entry>> top(int limit) {
        return database.submit(connection -> {
            List<Entry> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT b.player_name AS player_name, k.total AS total FROM player_mob_kills k " +
                            "JOIN player_balances b ON b.uuid = k.uuid ORDER BY k.total DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(new Entry(rows.getString("player_name"), rows.getLong("total")));
                }
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return result;
        });
    }

    public CompletableFuture<Void> increment(UUID uuid) { return add(uuid, 1); }

    public CompletableFuture<Void> add(UUID uuid, long amount) {
        return database.submit(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_mob_kills (uuid, total) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET total = total + excluded.total")) {
                insert.setString(1, uuid.toString());
                insert.setLong(2, amount);
                insert.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }

    public CompletableFuture<Long> getTotal(UUID uuid) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT total FROM player_mob_kills WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getLong("total") : 0L;
                }
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        });
    }
}
