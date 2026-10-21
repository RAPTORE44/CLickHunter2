package fr.cliker.clikerv2.auction;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AuctionStore {
    private final DatabaseManager database;

    public AuctionStore(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Void> save(AuctionListing listing) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO auctions (id, seller_uuid, seller_name, item, price, listed_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, listing.id());
                statement.setString(2, listing.sellerUuid().toString());
                statement.setString(3, listing.sellerName());
                statement.setString(4, listing.itemYaml());
                statement.setLong(5, listing.price());
                statement.setLong(6, listing.listedAt());
                statement.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }

    public CompletableFuture<Void> removeById(String id) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM auctions WHERE id = ?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }

    /** Annonces encore actives (non expirées), pour peupler le cache mémoire au démarrage. */
    public CompletableFuture<List<AuctionListing>> loadActive() {
        return query("SELECT id, seller_uuid, seller_name, item, price, listed_at FROM auctions WHERE expired = 0");
    }

    /** Annonces expirées d'un vendeur en attente de retour (le joueur était hors-ligne au moment de l'expiration). */
    public CompletableFuture<List<AuctionListing>> loadExpiredFor(UUID seller) {
        return database.submit(connection -> {
            List<AuctionListing> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, seller_uuid, seller_name, item, price, listed_at FROM auctions WHERE expired = 1 AND seller_uuid = ?")) {
                statement.setString(1, seller.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(read(rows));
                }
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return result;
        });
    }

    public CompletableFuture<Void> markExpired(String id) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE auctions SET expired = 1 WHERE id = ?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return null;
        });
    }

    private CompletableFuture<List<AuctionListing>> query(String sql) {
        return database.submit(connection -> {
            List<AuctionListing> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(read(rows));
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return result;
        });
    }

    private AuctionListing read(ResultSet rows) throws SQLException {
        return new AuctionListing(
                rows.getString("id"),
                UUID.fromString(rows.getString("seller_uuid")),
                rows.getString("seller_name"),
                rows.getString("item"),
                rows.getLong("price"),
                rows.getLong("listed_at"));
    }
}
