package fr.cliker.clikerv2.level;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Dernier niveau XP connu de chaque joueur (le niveau vanilla n'est pas stocké côté plugin, donc on le duplique ici pour le classement). */
public final class PlayerLevelStore {
    private final DatabaseManager database;

    public PlayerLevelStore(DatabaseManager database) {
        this.database = database;
    }

    public record Entry(String playerName, int level) {}

    public CompletableFuture<Void> setLevel(UUID uuid, String playerName, int level) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_levels (uuid, player_name, level) VALUES (?, ?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET player_name = excluded.player_name, level = excluded.level")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, playerName);
                statement.setInt(3, level);
                statement.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }

    /** Nombre total de joueurs distincts ayant déjà rejoint (une ligne par joueur, upsert à chaque connexion). */
    public CompletableFuture<Integer> countTotal() {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) AS total FROM player_levels");
                 ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt("total") : 0;
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        });
    }

    public CompletableFuture<List<Entry>> top(int limit) {
        return database.submit(connection -> {
            List<Entry> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_name, level FROM player_levels ORDER BY level DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(new Entry(rows.getString("player_name"), rows.getInt("level")));
                }
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return result;
        });
    }
}
