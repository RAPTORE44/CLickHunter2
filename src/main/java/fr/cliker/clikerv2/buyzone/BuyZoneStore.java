package fr.cliker.clikerv2.buyzone;

import fr.cliker.clikerv2.DatabaseManager;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class BuyZoneStore {
    private final DatabaseManager database;
    public BuyZoneStore(DatabaseManager database) { this.database = database; }
    public CompletableFuture<Set<String>> load(UUID uuid) {
        return database.submit(connection -> {
            Set<String> result = new HashSet<>();
            try (PreparedStatement query = connection.prepareStatement("SELECT zone_id FROM buyzone_unlocks WHERE uuid=?")) {
                query.setString(1, uuid.toString());
                try (ResultSet rows = query.executeQuery()) { while (rows.next()) result.add(rows.getString(1)); }
            } catch (SQLException e) { throw new DatabaseManager.DatabaseException(e); }
            return result;
        });
    }
}
