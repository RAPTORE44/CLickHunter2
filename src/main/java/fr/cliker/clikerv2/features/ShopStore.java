package fr.cliker.clikerv2.features;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.rewards.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public final class ShopStore {
    private final DatabaseManager db;
    private final CompletableFuture<?> ready;

    public ShopStore(DatabaseManager db) {
        this.db = db;
        ready =
                db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.execute(
                                        "CREATE TABLE IF NOT EXISTS market_stock(offer TEXT PRIMARY"
                                                + " KEY,sold INTEGER NOT NULL DEFAULT 0)");
                                return null;
                            } catch (SQLException e) {
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        });
    }

    public CompletableFuture<Map<String, Integer>> stocks() {
        return stocks("");
    }

    public CompletableFuture<Map<String, Integer>> stocks(String prefix) {
        return ready.thenCompose(
                v ->
                        db.submit(
                                c -> {
                                    var result = new HashMap<String, Integer>();
                                    try (var s =
                                            c.prepareStatement(
                                                    "SELECT offer,sold FROM market_stock WHERE"
                                                            + " offer>=? AND offer<?")) {
                                        s.setString(1, prefix);
                                        s.setString(2, prefix + "\uffff");
                                        try (var r = s.executeQuery()) {
                                            while (r.next())
                                                result.put(r.getString(1), r.getInt(2));
                                        }
                                        return result;
                                    } catch (SQLException e) {
                                        throw new DatabaseManager.DatabaseException(e);
                                    }
                                }));
    }

    public CompletableFuture<Boolean> buy(
            UUID owner, String name, String offer, int cap, long price, String payload) {
        return buy(owner, name, offer, cap, price, payload, "shop:" + UUID.randomUUID());
    }

    public CompletableFuture<Boolean> buy(
            UUID owner,
            String name,
            String offer,
            int cap,
            long price,
            String payload,
            String source) {
        if (owner == null
                || cap < 0
                || cap > 5
                || price < 1
                || price > 1_000_000_000_000_000L
                || payload == null
                || offer == null
                || source == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Achat invalide"));
        return ready.thenCompose(
                v ->
                        db.submit(
                                c -> {
                                    try {
                                        c.setAutoCommit(false);
                                        try (var prior =
                                                c.prepareStatement(
                                                        "SELECT payload FROM claim_items WHERE"
                                                            + " owner=? AND source=?")) {
                                            prior.setString(1, owner.toString());
                                            prior.setString(2, source);
                                            try (var row = prior.executeQuery()) {
                                                if (row.next()) {
                                                    boolean same = payload.equals(row.getString(1));
                                                    c.rollback();
                                                    return same;
                                                }
                                            }
                                        }
                                        if (cap > 0) {
                                            try (var s =
                                                    c.prepareStatement(
                                                            "INSERT INTO market_stock(offer,sold)"
                                                                    + " VALUES(?,0) ON CONFLICT DO"
                                                                    + " NOTHING")) {
                                                s.setString(1, offer);
                                                s.executeUpdate();
                                            }
                                            try (var s =
                                                    c.prepareStatement(
                                                            "UPDATE market_stock SET sold=sold+1"
                                                                + " WHERE offer=? AND sold<?")) {
                                                s.setString(1, offer);
                                                s.setInt(2, cap);
                                                if (s.executeUpdate() != 1) {
                                                    c.rollback();
                                                    return false;
                                                }
                                            }
                                        }
                                        try (var s =
                                                c.prepareStatement(
                                                        "UPDATE player_balances SET ecus=ecus-?"
                                                                + " WHERE uuid=? AND ecus>=?")) {
                                            s.setLong(1, price);
                                            s.setString(2, owner.toString());
                                            s.setLong(3, price);
                                            if (s.executeUpdate() != 1) {
                                                c.rollback();
                                                return false;
                                            }
                                        }
                                        MailStore.insert(c, owner, source, payload);
                                        c.commit();
                                        return true;
                                    } catch (SQLException e) {
                                        try {
                                            c.rollback();
                                        } catch (SQLException ignored) {
                                        }
                                        throw new DatabaseManager.DatabaseException(e);
                                    }
                                }));
    }
}
