package fr.cliker.clikerv2;

import fr.cliker.clikerv2.economy.CurrencyType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class EconomyService {
    private final DatabaseManager database;
    private final Map<CurrencyType, Long> startingBalances;

    public EconomyService(DatabaseManager database, Map<CurrencyType, Long> startingBalances) {
        this.database = database;
        this.startingBalances = new EnumMap<>(CurrencyType.class);
        this.startingBalances.putAll(startingBalances);
    }

    public CompletableFuture<Balances> getBalances(UUID uuid) {
        return database.submit(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "SELECT ecus, gemmes_sombres, cendres FROM player_balances"
                                            + " WHERE uuid = ?")) {
                        statement.setString(1, uuid.toString());
                        try (ResultSet result = statement.executeQuery()) {
                            if (!result.next()) return Balances.empty();
                            return new Balances(
                                    result.getLong("ecus"),
                                    result.getLong("gemmes_sombres"),
                                    result.getLong("cendres"));
                        }
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                });
    }

    public record PlayerSnapshot(Balances balances, long kills) {}

    public CompletableFuture<PlayerSnapshot> playerSnapshot(UUID owner) {
        return database.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "SELECT b.ecus,b.gemmes_sombres,b.cendres,COALESCE(k.total,0)"
                                        + " FROM player_balances b LEFT JOIN player_mob_kills k ON"
                                        + " k.uuid=b.uuid WHERE b.uuid=?")) {
                        s.setString(1, owner.toString());
                        try (var rs = s.executeQuery()) {
                            return rs.next()
                                    ? new PlayerSnapshot(
                                            new Balances(
                                                    rs.getLong(1), rs.getLong(2), rs.getLong(3)),
                                            rs.getLong(4))
                                    : new PlayerSnapshot(Balances.empty(), 0);
                        }
                    } catch (SQLException error) {
                        throw new DatabaseManager.DatabaseException(error);
                    }
                });
    }

    public CompletableFuture<Long> getBalance(UUID uuid, String playerName, CurrencyType currency) {
        return database.submit(
                connection -> {
                    try {
                        ensurePlayer(connection, uuid, playerName);
                        return readBalance(connection, uuid, currency);
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                });
    }

    public CompletableFuture<Long> change(
            UUID uuid, String playerName, CurrencyType currency, long amount) {
        if (amount == Long.MIN_VALUE)
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Montant hors limites"));
        return database.submit(
                connection -> {
                    try {
                        ensurePlayer(connection, uuid, playerName);
                        String field = column(currency);
                        String sql =
                                "UPDATE player_balances SET "
                                        + field
                                        + " = "
                                        + field
                                        + " + ? WHERE uuid = ? AND "
                                        + field
                                        + (amount >= 0 ? " <= ?" : " >= ?");
                        try (PreparedStatement statement = connection.prepareStatement(sql)) {
                            statement.setLong(1, amount);
                            statement.setString(2, uuid.toString());
                            statement.setLong(3, amount >= 0 ? Long.MAX_VALUE - amount : -amount);
                            if (statement.executeUpdate() == 0)
                                throw new IllegalArgumentException("Solde insuffisant");
                        }
                        return readBalance(connection, uuid, currency);
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                });
    }

    public CompletableFuture<Long> set(
            UUID uuid, String playerName, CurrencyType currency, long amount) {
        if (amount < 0)
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Le montant ne peut pas être négatif"));
        return database.submit(
                connection -> {
                    try {
                        ensurePlayer(connection, uuid, playerName);
                        try (PreparedStatement statement =
                                connection.prepareStatement(
                                        "UPDATE player_balances SET "
                                                + column(currency)
                                                + " = ? WHERE uuid = ?")) {
                            statement.setLong(1, amount);
                            statement.setString(2, uuid.toString());
                            statement.executeUpdate();
                        }
                        return amount;
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                });
    }

    private void ensurePlayer(Connection connection, UUID uuid, String playerName)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT OR IGNORE INTO player_balances (uuid, player_name, ecus,"
                                + " gemmes_sombres, cendres) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, playerName);
            statement.setLong(3, startingBalances.getOrDefault(CurrencyType.ECUS, 0L));
            statement.setLong(4, startingBalances.getOrDefault(CurrencyType.GEMMES_SOMBRES, 0L));
            statement.setLong(5, startingBalances.getOrDefault(CurrencyType.CENDRES, 0L));
            statement.executeUpdate();
        }
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "UPDATE player_balances SET player_name = ? WHERE uuid = ?")) {
            statement.setString(1, playerName);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }

    public enum ZonePurchase {
        PURCHASED,
        ALREADY_OWNED,
        INSUFFICIENT_FUNDS
    }

    /** Le débit et le droit d'accès sont validés ensemble, ou tous deux annulés. */
    public CompletableFuture<ZonePurchase> purchaseZone(
            UUID uuid,
            String playerName,
            String zoneId,
            String permission,
            CurrencyType currency,
            long price) {
        if (price < 0)
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Prix de zone non configuré"));
        return database.submit(
                connection -> {
                    try {
                        connection.setAutoCommit(false);
                        try (PreparedStatement owned =
                                connection.prepareStatement(
                                        "SELECT 1 FROM buyzone_unlocks WHERE uuid=? AND"
                                                + " zone_id=?")) {
                            owned.setString(1, uuid.toString());
                            owned.setString(2, zoneId);
                            try (ResultSet rows = owned.executeQuery()) {
                                if (rows.next()) {
                                    connection.rollback();
                                    return ZonePurchase.ALREADY_OWNED;
                                }
                            }
                        }
                        ensurePlayer(connection, uuid, playerName);
                        try (PreparedStatement debit =
                                connection.prepareStatement(
                                        "UPDATE player_balances SET "
                                                + column(currency)
                                                + " = "
                                                + column(currency)
                                                + " - ? WHERE uuid=? AND "
                                                + column(currency)
                                                + " >= ?")) {
                            debit.setLong(1, price);
                            debit.setString(2, uuid.toString());
                            debit.setLong(3, price);
                            if (debit.executeUpdate() == 0) {
                                connection.rollback();
                                return ZonePurchase.INSUFFICIENT_FUNDS;
                            }
                        }
                        try (PreparedStatement grant =
                                connection.prepareStatement(
                                        "INSERT INTO buyzone_unlocks(uuid,zone_id,permission)"
                                                + " VALUES(?,?,?)")) {
                            grant.setString(1, uuid.toString());
                            grant.setString(2, zoneId);
                            grant.setString(3, permission);
                            grant.executeUpdate();
                        }
                        connection.commit();
                        return ZonePurchase.PURCHASED;
                    } catch (SQLException exception) {
                        try {
                            connection.rollback();
                        } catch (SQLException rollback) {
                            exception.addSuppressed(rollback);
                        }
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                });
    }

    /** Débit et remise dans /claim font partie de la même transaction. */
    public CompletableFuture<Boolean> purchaseItem(
            UUID owner,
            String name,
            String source,
            CurrencyType currency,
            long cost,
            String payload) {
        if (cost < 0)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Prix négatif"));
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        try (var s =
                                c.prepareStatement(
                                        "SELECT 1 FROM claim_items WHERE owner=? AND source=?")) {
                            s.setString(1, owner.toString());
                            s.setString(2, source);
                            try (var rs = s.executeQuery()) {
                                if (rs.next()) {
                                    c.rollback();
                                    return true;
                                }
                            }
                        }
                        ensurePlayer(c, owner, name);
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE player_balances SET "
                                                + column(currency)
                                                + "="
                                                + column(currency)
                                                + "-? WHERE uuid=? AND "
                                                + column(currency)
                                                + ">=?")) {
                            s.setLong(1, cost);
                            s.setString(2, owner.toString());
                            s.setLong(3, cost);
                            if (s.executeUpdate() != 1) {
                                c.rollback();
                                return false;
                            }
                        }
                        fr.cliker.clikerv2.rewards.MailStore.insert(c, owner, source, payload);
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

    /** Une transformation rend l'objet original si le solde est insuffisant. */
    public CompletableFuture<Boolean> transformItem(
            UUID owner,
            String name,
            String source,
            CurrencyType currency,
            long cost,
            String original,
            String transformed) {
        if (cost < 0)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Prix négatif"));
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        try (var s =
                                c.prepareStatement(
                                        "SELECT kind FROM reward_ledger WHERE source=? AND"
                                                + " owner=?")) {
                            s.setString(1, source);
                            s.setString(2, owner.toString());
                            try (var rs = s.executeQuery()) {
                                if (rs.next()) {
                                    boolean paid = rs.getString(1).equals("gear-paid");
                                    c.rollback();
                                    return paid;
                                }
                            }
                        }
                        ensurePlayer(c, owner, name);
                        boolean paid;
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE player_balances SET "
                                                + column(currency)
                                                + "="
                                                + column(currency)
                                                + "-? WHERE uuid=? AND "
                                                + column(currency)
                                                + ">=?")) {
                            s.setLong(1, cost);
                            s.setString(2, owner.toString());
                            s.setLong(3, cost);
                            paid = s.executeUpdate() == 1;
                        }
                        fr.cliker.clikerv2.rewards.MailStore.insert(
                                c, owner, source, paid ? transformed : original);
                        try (var s =
                                c.prepareStatement(
                                        "INSERT INTO reward_ledger(source,owner,kind)"
                                                + " VALUES(?,?,?)")) {
                            s.setString(1, source);
                            s.setString(2, owner.toString());
                            s.setString(3, paid ? "gear-paid" : "gear-return");
                            s.executeUpdate();
                        }
                        c.commit();
                        return paid;
                    } catch (SQLException e) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public enum AuctionPurchase {
        PURCHASED,
        UNAVAILABLE,
        INSUFFICIENT_FUNDS
    }

    public CompletableFuture<AuctionPurchase> purchaseAuction(UUID buyer, String name, String id) {
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        String seller, sellerName, item;
                        long price, listedAt;
                        try (var s =
                                c.prepareStatement(
                                        "SELECT seller_uuid,seller_name,item,price,listed_at FROM"
                                                + " auctions WHERE id=? AND expired=0")) {
                            s.setString(1, id);
                            try (var rs = s.executeQuery()) {
                                if (!rs.next()) {
                                    c.rollback();
                                    return AuctionPurchase.UNAVAILABLE;
                                }
                                seller = rs.getString(1);
                                sellerName = rs.getString(2);
                                item = rs.getString(3);
                                price = rs.getLong(4);
                                listedAt = rs.getLong(5);
                            }
                        }
                        if (price < 1
                                || seller.equals(buyer.toString())
                                || System.currentTimeMillis() - listedAt
                                        >= fr.cliker.clikerv2.auction.AuctionManager
                                                .EXPIRY_MILLIS) {
                            c.rollback();
                            return AuctionPurchase.UNAVAILABLE;
                        }
                        ensurePlayer(c, buyer, name);
                        ensurePlayer(c, UUID.fromString(seller), sellerName);
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE player_balances SET ecus=ecus-? WHERE uuid=? AND"
                                                + " ecus>=?")) {
                            s.setLong(1, price);
                            s.setString(2, buyer.toString());
                            s.setLong(3, price);
                            if (s.executeUpdate() != 1) {
                                c.rollback();
                                return AuctionPurchase.INSUFFICIENT_FUNDS;
                            }
                        }
                        long tax = fr.cliker.clikerv2.auction.AuctionListing.tax(price),
                                proceeds = price - tax;
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE player_balances SET ecus=ecus+? WHERE uuid=? AND"
                                                + " ecus<=?")) {
                            s.setLong(1, proceeds);
                            s.setString(2, seller);
                            s.setLong(3, Long.MAX_VALUE - proceeds);
                            if (s.executeUpdate() != 1)
                                throw new SQLException("Solde vendeur maximal dépassé");
                        }
                        fr.cliker.clikerv2.rewards.MailStore.insert(
                                c, buyer, "auction-buy:" + id, item);
                        try (var s = c.prepareStatement("DELETE FROM auctions WHERE id=?")) {
                            s.setString(1, id);
                            if (s.executeUpdate() != 1)
                                throw new SQLException("Annonce déjà vendue");
                        }
                        c.commit();
                        return AuctionPurchase.PURCHASED;
                    } catch (SQLException e) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    private long readBalance(Connection connection, UUID uuid, CurrencyType currency)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT " + column(currency) + " FROM player_balances WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    public CompletableFuture<List<LeaderboardEntry>> topBalances(CurrencyType currency, int limit) {
        return database.submit(
                connection -> {
                    List<LeaderboardEntry> result = new ArrayList<>();
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "SELECT player_name, "
                                            + column(currency)
                                            + " AS amount FROM player_balances ORDER BY amount DESC"
                                            + " LIMIT ?")) {
                        statement.setInt(1, limit);
                        try (ResultSet rows = statement.executeQuery()) {
                            while (rows.next())
                                result.add(
                                        new LeaderboardEntry(
                                                rows.getString("player_name"),
                                                rows.getLong("amount")));
                        }
                    } catch (SQLException exception) {
                        throw new DatabaseManager.DatabaseException(exception);
                    }
                    return result;
                });
    }

    private static String column(CurrencyType currency) {
        return currency.key().replace('-', '_');
    }

    public record Balances(long ecus, long gemmesSombres, long cendres) {
        public static Balances empty() {
            return new Balances(0, 0, 0);
        }
    }

    public record LeaderboardEntry(String playerName, long amount) {}
}
