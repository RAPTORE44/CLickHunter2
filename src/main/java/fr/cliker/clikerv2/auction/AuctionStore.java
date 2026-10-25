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
        return database.submit(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "INSERT INTO auctions (id, seller_uuid, seller_name, item,"
                                            + " price, listed_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                        statement.setString(1, listing.id());
                        statement.setString(2, listing.sellerUuid().toString());
                        statement.setString(3, listing.sellerName());
                        statement.setString(4, listing.itemYaml());
                        statement.setLong(5, listing.price());
                        statement.setLong(6, listing.listedAt());
                        statement.executeUpdate();
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    return null;
                });
    }

    public CompletableFuture<Void> publish(AuctionListing listing, boolean advertise) {
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        long fee =
                                Math.addExact(
                                        AuctionListing.tax(listing.price()),
                                        advertise ? advertisingFee(listing.price()) : 0);
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE player_balances SET ecus=ecus-? WHERE uuid=? AND"
                                                + " ecus>=?")) {
                            s.setLong(1, fee);
                            s.setString(2, listing.sellerUuid().toString());
                            s.setLong(3, fee);
                            if (s.executeUpdate() != 1)
                                throw new SQLException(
                                        "Écus insuffisants pour les frais de publication");
                        }
                        try (var s =
                                c.prepareStatement(
                                        "INSERT INTO"
                                            + " auctions(id,seller_uuid,seller_name,item,price,listed_at,fee_paid)"
                                            + " VALUES(?,?,?,?,?,?,1)")) {
                            s.setString(1, listing.id());
                            s.setString(2, listing.sellerUuid().toString());
                            s.setString(3, listing.sellerName());
                            s.setString(4, listing.itemYaml());
                            s.setLong(5, listing.price());
                            s.setLong(6, listing.listedAt());
                            s.executeUpdate();
                        }
                        c.commit();
                        return null;
                    } catch (SQLException e) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public static long advertisingFee(long price) {
        if (price < 0) throw new IllegalArgumentException();
        return price / 20 + (price % 20 == 0 ? 0 : 1);
    }

    public CompletableFuture<Void> removeById(String id) {
        return database.submit(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement("DELETE FROM auctions WHERE id = ?")) {
                        statement.setString(1, id);
                        statement.executeUpdate();
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    return null;
                });
    }

    /** Annonces encore actives (non expirées), pour peupler le cache mémoire au démarrage. */
    public CompletableFuture<List<AuctionListing>> loadActive() {
        return query(
                "SELECT id, seller_uuid, seller_name, item, price, listed_at FROM auctions WHERE"
                        + " expired = 0");
    }

    /**
     * Annonces expirées d'un vendeur en attente de retour (le joueur était hors-ligne au moment de
     * l'expiration).
     */
    public CompletableFuture<List<AuctionListing>> loadExpiredFor(UUID seller) {
        return database.submit(
                connection -> {
                    List<AuctionListing> result = new ArrayList<>();
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "SELECT id, seller_uuid, seller_name, item, price, listed_at"
                                        + " FROM auctions WHERE expired = 1 AND seller_uuid = ?")) {
                        statement.setString(1, seller.toString());
                        try (ResultSet rows = statement.executeQuery()) {
                            while (rows.next()) result.add(read(rows));
                        }
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    return result;
                });
    }

    public CompletableFuture<Void> markExpired(String id) {
        return database.submit(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "UPDATE auctions SET expired = 1 WHERE id = ?")) {
                        statement.setString(1, id);
                        statement.executeUpdate();
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    return null;
                });
    }

    /**
     * Le retour d'une annonce supprimée est conservé dans /claim, propriétaire hors ligne compris.
     */
    public CompletableFuture<Boolean> returnToOwner(String id) {
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        String owner, item;
                        try (var s =
                                c.prepareStatement(
                                        "SELECT seller_uuid,item FROM auctions WHERE id=?")) {
                            s.setString(1, id);
                            try (var rs = s.executeQuery()) {
                                if (!rs.next()) {
                                    c.rollback();
                                    return false;
                                }
                                owner = rs.getString(1);
                                item = rs.getString(2);
                            }
                        }
                        fr.cliker.clikerv2.rewards.MailStore.insert(
                                c, UUID.fromString(owner), "auction-return:" + id, item);
                        try (var s = c.prepareStatement("DELETE FROM auctions WHERE id=?")) {
                            s.setString(1, id);
                            s.executeUpdate();
                        }
                        c.commit();
                        return true;
                    } catch (SQLException e) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    private CompletableFuture<List<AuctionListing>> query(String sql) {
        return database.submit(
                connection -> {
                    List<AuctionListing> result = new ArrayList<>();
                    try (PreparedStatement statement = connection.prepareStatement(sql);
                            ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) result.add(read(rows));
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
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
