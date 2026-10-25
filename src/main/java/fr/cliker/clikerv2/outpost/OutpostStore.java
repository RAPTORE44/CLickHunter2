package fr.cliker.clikerv2.outpost;

import fr.cliker.clikerv2.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class OutpostStore {
    public record Plot(long id, UUID owner, String name, int tier) {
        public int x() {
            return Math.toIntExact((id % 100) * 256);
        }

        public int z() {
            return Math.toIntExact((id / 100) * 256);
        }

        public boolean contains(double x, double z, int radius) {
            return x >= x() - radius
                    && x < x() + radius + 1
                    && z >= z() - radius
                    && z < z() + radius + 1;
        }
    }

    public record Snapshot(List<Plot> plots, Map<UUID, Set<UUID>> trusted) {}

    public record Expansion(String status, Plot plot) {}

    private final DatabaseManager db;

    public OutpostStore(DatabaseManager db) {
        this.db = db;
    }

    private static Plot plot(ResultSet r) throws SQLException {
        return new Plot(
                r.getLong("id"),
                UUID.fromString(r.getString("owner")),
                r.getString("name"),
                r.getInt("tier"));
    }

    public CompletableFuture<Snapshot> load() {
        return db.submit(
                c -> {
                    try {
                        List<Plot> plots = new ArrayList<>();
                        Map<UUID, Set<UUID>> trusted = new HashMap<>();
                        try (var s = c.createStatement();
                                var r = s.executeQuery("SELECT * FROM outposts")) {
                            while (r.next()) plots.add(plot(r));
                        }
                        try (var s = c.createStatement();
                                var r = s.executeQuery("SELECT * FROM outpost_trusted")) {
                            while (r.next())
                                trusted.computeIfAbsent(
                                                UUID.fromString(r.getString(1)),
                                                k -> new HashSet<>())
                                        .add(UUID.fromString(r.getString(2)));
                        }
                        return new Snapshot(plots, trusted);
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Plot> create(UUID owner, String name) {
        return db.submit(
                c -> {
                    try {
                        try (var s =
                                c.prepareStatement(
                                        "INSERT INTO outposts(owner,name) VALUES(?,?) ON"
                                                + " CONFLICT(owner) DO UPDATE SET"
                                                + " name=excluded.name")) {
                            s.setString(1, owner.toString());
                            s.setString(2, name);
                            s.executeUpdate();
                        }
                        try (var s = c.prepareStatement("SELECT * FROM outposts WHERE owner=?")) {
                            s.setString(1, owner.toString());
                            try (var r = s.executeQuery()) {
                                if (!r.next()) throw new SQLException("Avant-poste introuvable");
                                return plot(r);
                            }
                        }
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Expansion> expand(UUID owner, long[] prices) {
        return db.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        Plot plot;
                        try (var s = c.prepareStatement("SELECT * FROM outposts WHERE owner=?")) {
                            s.setString(1, owner.toString());
                            try (var r = s.executeQuery()) {
                                if (!r.next()) {
                                    c.rollback();
                                    return new Expansion("absent", null);
                                }
                                plot = plot(r);
                            }
                        }
                        int next = plot.tier() + 1;
                        if (next >= prices.length) {
                            c.rollback();
                            return new Expansion("maximum", plot);
                        }
                        long cost = prices[next];
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE player_balances SET ecus=ecus-? WHERE uuid=? AND"
                                                + " ecus>=?")) {
                            s.setLong(1, cost);
                            s.setString(2, owner.toString());
                            s.setLong(3, cost);
                            if (s.executeUpdate() != 1) {
                                c.rollback();
                                return new Expansion("insuffisant", plot);
                            }
                        }
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE outposts SET tier=? WHERE owner=? AND tier=?")) {
                            s.setInt(1, next);
                            s.setString(2, owner.toString());
                            s.setInt(3, plot.tier());
                            if (s.executeUpdate() != 1)
                                throw new SQLException("Extension concurrente");
                        }
                        c.commit();
                        return new Expansion(
                                "agrandi", new Plot(plot.id(), owner, plot.name(), next));
                    } catch (SQLException e) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Boolean> trust(UUID owner, UUID guest, boolean add) {
        return db.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    add
                                            ? "INSERT OR IGNORE INTO outpost_trusted(owner,guest)"
                                                    + " VALUES(?,?)"
                                            : "DELETE FROM outpost_trusted WHERE owner=? AND"
                                                    + " guest=?")) {
                        s.setString(1, owner.toString());
                        s.setString(2, guest.toString());
                        return s.executeUpdate() > 0;
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<List<String>> wealth() {
        return db.submit(
                c -> {
                    try (var s = c.createStatement();
                            var r =
                                    s.executeQuery(
                                            "SELECT"
                                                + " o.name,COALESCE(b.ecus,0),COALESCE(b.gemmes_sombres,0),COALESCE(b.cendres,0)"
                                                + " FROM outposts o LEFT JOIN player_balances b ON"
                                                + " b.uuid=o.owner ORDER BY COALESCE(b.ecus,0)"
                                                + " DESC,o.id LIMIT 10")) {
                        List<String> list = new ArrayList<>();
                        while (r.next())
                            list.add(
                                    "&e#"
                                            + (list.size() + 1)
                                            + " &f"
                                            + r.getString(1)
                                            + " &8• &6"
                                            + fr.cliker.clikerv2.economy.CurrencyType.ECUS.format(
                                                    r.getLong(2))
                                            + " Écus &8• &5"
                                            + fr.cliker.clikerv2.economy.CurrencyType.GEMMES_SOMBRES
                                                    .format(r.getLong(3))
                                            + " Gemmes &8• &7"
                                            + fr.cliker.clikerv2.economy.CurrencyType.CENDRES
                                                    .format(r.getLong(4))
                                            + " Cendres");
                        return list;
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }
}
