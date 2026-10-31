package fr.cliker.clikerv2.donors;

import fr.cliker.clikerv2.DatabaseManager;

import java.math.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Points en centièmes : 100 unités = 1 point = 1 euro. Aucun appel réseau. */
public final class DonorPointsStore {
    public static final long MAX_UNITS = 1_000_000_000_000L;

    public record Donor(String name, long units) {}

    public record Change(Donor donor, boolean applied) {}

    public record Page(List<Donor> donors, boolean hasNext) {}

    private final DatabaseManager database;

    public DonorPointsStore(DatabaseManager database) {
        this.database = database;
    }

    public static String key(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}"))
            throw new IllegalArgumentException("Indique un pseudo Minecraft valide.");
        return name.toLowerCase(Locale.ROOT);
    }

    public static long parse(String value) {
        if (!value.matches("[0-9]+([.,][0-9]{1,2})?"))
            throw new IllegalArgumentException(
                    "Indique un montant positif avec au maximum deux décimales, par exemple 9.99.");
        try {
            long units = new BigDecimal(value.replace(',', '.')).movePointRight(2).longValueExact();
            if (units < 1 || units > MAX_UNITS) throw new ArithmeticException();
            return units;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Montant de points hors limites.");
        }
    }

    public static String format(long units) {
        return BigDecimal.valueOf(units, 2).stripTrailingZeros().toPlainString();
    }

    public CompletableFuture<Change> change(
            String name, long delta, String reference, String actor) {
        String key = key(name);
        if (delta == 0 || delta > MAX_UNITS || delta < -MAX_UNITS)
            throw new IllegalArgumentException("Montant de points hors limites.");
        if (reference != null && !reference.matches("[A-Za-z0-9._:-]{1,128}"))
            throw new IllegalArgumentException(
                    "Référence invalide (lettres, chiffres, point, tiret, deux-points ou"
                        + " soulignement). ");
        String receipt = reference == null ? "manual:" + UUID.randomUUID() : "shop:" + reference;
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        try (var s =
                                c.prepareStatement(
                                        "SELECT name_key,delta FROM donor_point_events WHERE"
                                            + " receipt=?")) {
                            s.setString(1, receipt);
                            try (var r = s.executeQuery()) {
                                if (r.next()) {
                                    if (!key.equals(r.getString(1)) || delta != r.getLong(2))
                                        throw new IllegalArgumentException(
                                                "Cette référence a déjà servi à une autre"
                                                    + " opération.");
                                    Donor old = read(c, key, name);
                                    c.rollback();
                                    return new Change(old, false);
                                }
                            }
                        }
                        Donor old = read(c, key, name);
                        long total = Math.addExact(old.units(), delta);
                        if (total < 0)
                            throw new IllegalArgumentException(
                                    "Ce joueur n’a pas assez de points à retirer.");
                        if (total > MAX_UNITS)
                            throw new IllegalArgumentException(
                                    "Le total maximal de points serait dépassé.");
                        try (var s =
                                c.prepareStatement(
                                        "INSERT INTO donor_points(name_key,name,units)"
                                            + " VALUES(?,?,?) ON CONFLICT(name_key) DO UPDATE SET"
                                            + " name=excluded.name,units=excluded.units")) {
                            s.setString(1, key);
                            s.setString(2, name);
                            s.setLong(3, total);
                            s.executeUpdate();
                        }
                        try (var s =
                                c.prepareStatement(
                                        "INSERT INTO"
                                            + " donor_point_events(receipt,name_key,delta,actor)"
                                            + " VALUES(?,?,?,?)")) {
                            s.setString(1, receipt);
                            s.setString(2, key);
                            s.setLong(3, delta);
                            s.setString(4, actor);
                            s.executeUpdate();
                        }
                        c.commit();
                        return new Change(new Donor(name, total), true);
                    } catch (SQLException | RuntimeException e) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        if (e instanceof RuntimeException runtime) throw runtime;
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    private static Donor read(Connection c, String key, String fallback) throws SQLException {
        try (var s = c.prepareStatement("SELECT name,units FROM donor_points WHERE name_key=?")) {
            s.setString(1, key);
            try (var r = s.executeQuery()) {
                return r.next() ? new Donor(r.getString(1), r.getLong(2)) : new Donor(fallback, 0);
            }
        }
    }

    public CompletableFuture<Donor> find(String name) {
        String key = key(name);
        return database.submit(
                c -> {
                    try {
                        return read(c, key, name);
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<List<Donor>> top() {
        return database.submit(
                c -> {
                    try {
                        return query(
                                c,
                                "SELECT name,units FROM donor_points WHERE units>0 ORDER BY units"
                                    + " DESC,name_key ASC LIMIT 3",
                                0,
                                0);
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Page> page(int page) {
        int offset = Math.multiplyExact(Math.max(0, Math.min(page, 1_000_000)), 45);
        return database.submit(
                c -> {
                    try {
                        var rows =
                                query(
                                        c,
                                        "SELECT name,units FROM donor_points ORDER BY units"
                                            + " DESC,name_key ASC LIMIT ? OFFSET ?",
                                        46,
                                        offset);
                        return new Page(
                                List.copyOf(rows.subList(0, Math.min(45, rows.size()))),
                                rows.size() > 45);
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    private static List<Donor> query(Connection c, String sql, int limit, int offset)
            throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            if (limit > 0) {
                s.setInt(1, limit);
                s.setInt(2, offset);
            }
            try (var r = s.executeQuery()) {
                var rows = new ArrayList<Donor>();
                while (r.next()) rows.add(new Donor(r.getString(1), r.getLong(2)));
                return rows;
            }
        }
    }
}
