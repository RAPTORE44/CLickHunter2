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
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT ecus, gemmes_sombres, cendres FROM player_balances WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return Balances.empty();
                    return new Balances(result.getLong("ecus"), result.getLong("gemmes_sombres"), result.getLong("cendres"));
                }
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        });
    }

    public CompletableFuture<Long> getBalance(UUID uuid, String playerName, CurrencyType currency) {
        return database.submit(connection -> {
            try {
                ensurePlayer(connection, uuid, playerName);
                return readBalance(connection, uuid, currency);
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        });
    }

    public CompletableFuture<Long> change(UUID uuid, String playerName, CurrencyType currency, long amount) {
        return database.submit(connection -> {
            try {
                ensurePlayer(connection, uuid, playerName);
                String field = column(currency);
                String sql = "UPDATE player_balances SET " + field + " = " + field + " + ? WHERE uuid = ? AND " + field + " + ? >= 0";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, amount); statement.setString(2, uuid.toString()); statement.setLong(3, amount);
                    if (statement.executeUpdate() == 0) throw new IllegalArgumentException("Solde insuffisant");
                }
                return readBalance(connection, uuid, currency);
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        });
    }

    public CompletableFuture<Long> set(UUID uuid, String playerName, CurrencyType currency, long amount) {
        if (amount < 0) return CompletableFuture.failedFuture(new IllegalArgumentException("Le montant ne peut pas être négatif"));
        return database.submit(connection -> {
            try {
                ensurePlayer(connection, uuid, playerName);
                try (PreparedStatement statement = connection.prepareStatement("UPDATE player_balances SET " + column(currency) + " = ? WHERE uuid = ?")) {
                    statement.setLong(1, amount); statement.setString(2, uuid.toString()); statement.executeUpdate();
                }
                return amount;
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        });
    }

    private void ensurePlayer(Connection connection, UUID uuid, String playerName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO player_balances (uuid, player_name, ecus, gemmes_sombres, cendres) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString()); statement.setString(2, playerName);
            statement.setLong(3, startingBalances.getOrDefault(CurrencyType.ECUS, 0L));
            statement.setLong(4, startingBalances.getOrDefault(CurrencyType.GEMMES_SOMBRES, 0L));
            statement.setLong(5, startingBalances.getOrDefault(CurrencyType.CENDRES, 0L)); statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE player_balances SET player_name = ? WHERE uuid = ?")) {
            statement.setString(1, playerName); statement.setString(2, uuid.toString()); statement.executeUpdate();
        }
    }

    public enum ZonePurchase { PURCHASED, ALREADY_OWNED, INSUFFICIENT_FUNDS }

    /** Le débit et le droit d'accès sont validés ensemble, ou tous deux annulés. */
    public CompletableFuture<ZonePurchase> purchaseZone(UUID uuid, String playerName, String zoneId,
                                                       String permission, CurrencyType currency, long price) {
        if (price < 0) return CompletableFuture.failedFuture(new IllegalArgumentException("Prix de zone non configuré"));
        return database.submit(connection -> {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement owned = connection.prepareStatement("SELECT 1 FROM buyzone_unlocks WHERE uuid=? AND zone_id=?")) {
                    owned.setString(1, uuid.toString()); owned.setString(2, zoneId);
                    try (ResultSet rows = owned.executeQuery()) {
                        if (rows.next()) { connection.rollback(); return ZonePurchase.ALREADY_OWNED; }
                    }
                }
                ensurePlayer(connection, uuid, playerName);
                try (PreparedStatement debit = connection.prepareStatement("UPDATE player_balances SET " + column(currency)
                        + " = " + column(currency) + " - ? WHERE uuid=? AND " + column(currency) + " >= ?")) {
                    debit.setLong(1, price); debit.setString(2, uuid.toString()); debit.setLong(3, price);
                    if (debit.executeUpdate() == 0) { connection.rollback(); return ZonePurchase.INSUFFICIENT_FUNDS; }
                }
                try (PreparedStatement grant = connection.prepareStatement("INSERT INTO buyzone_unlocks(uuid,zone_id,permission) VALUES(?,?,?)")) {
                    grant.setString(1, uuid.toString()); grant.setString(2, zoneId); grant.setString(3, permission);
                    grant.executeUpdate();
                }
                connection.commit();
                return ZonePurchase.PURCHASED;
            } catch (SQLException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw new DatabaseManager.DatabaseException(exception);
            }
        });
    }

    private long readBalance(Connection connection, UUID uuid, CurrencyType currency) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + column(currency) + " FROM player_balances WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    public CompletableFuture<List<LeaderboardEntry>> topBalances(CurrencyType currency, int limit) {
        return database.submit(connection -> {
            List<LeaderboardEntry> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_name, " + column(currency) + " AS amount FROM player_balances ORDER BY amount DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(new LeaderboardEntry(rows.getString("player_name"), rows.getLong("amount")));
                }
            } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
            return result;
        });
    }

    private static String column(CurrencyType currency) { return currency.key().replace('-', '_'); }
    public record Balances(long ecus, long gemmesSombres, long cendres) { public static Balances empty() { return new Balances(0, 0, 0); } }
    public record LeaderboardEntry(String playerName, long amount) {}
}
