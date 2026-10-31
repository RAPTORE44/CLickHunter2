package fr.cliker.clikerv2.rewards;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** File d'objets durable ; les monnaies ne passent jamais par ce coffre. */
public final class MailStore {
    private final DatabaseManager database;

    public record Entry(String id, String payload) {}

    public record Page(List<Entry> entries, int total) {}

    public MailStore(DatabaseManager database) {
        this.database = database;
    }

    public static Entry insert(Connection c, UUID owner, String source, String payload)
            throws SQLException {
        try (var s =
                c.prepareStatement(
                        "INSERT OR IGNORE INTO claim_items(id,owner,source,payload)"
                                + " VALUES(?,?,?,?)")) {
            s.setString(1, UUID.randomUUID().toString());
            s.setString(2, owner.toString());
            s.setString(3, source);
            s.setString(4, payload);
            s.executeUpdate();
        }
        try (var s =
                c.prepareStatement(
                        "SELECT id,payload FROM claim_items WHERE owner=? AND source=?")) {
            s.setString(1, owner.toString());
            s.setString(2, source);
            try (var r = s.executeQuery()) {
                if (!r.next()) throw new SQLException("Récompense introuvable après insertion");
                return new Entry(r.getString(1), r.getString(2));
            }
        }
    }

    public CompletableFuture<Entry> enqueue(UUID owner, String source, String payload) {
        return database.submit(
                c -> {
                    try {
                        return insert(c, owner, source, payload);
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Page> page(UUID owner, int page) {
        return database.submit(
                c -> {
                    try {
                        int total;
                        try (var s =
                                c.prepareStatement(
                                        "SELECT COUNT(*) FROM claim_items WHERE owner=? AND"
                                                + " state<>2")) {
                            s.setString(1, owner.toString());
                            try (var r = s.executeQuery()) {
                                r.next();
                                total = r.getInt(1);
                            }
                        }
                        List<Entry> entries = new ArrayList<>();
                        try (var s =
                                c.prepareStatement(
                                        "SELECT id,payload FROM claim_items WHERE owner=? AND"
                                                + " state<>2 ORDER BY rowid LIMIT 45 OFFSET ?")) {
                            s.setString(1, owner.toString());
                            s.setLong(2, (long) Math.max(0, page) * 45);
                            try (var r = s.executeQuery()) {
                                while (r.next())
                                    entries.add(new Entry(r.getString(1), r.getString(2)));
                            }
                        }
                        return new Page(entries, total);
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Entry> pendingSource(UUID owner, String source) {
        return database.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "SELECT id,payload FROM claim_items WHERE owner=? AND source=?"
                                        + " AND state<>2")) {
                        s.setString(1, owner.toString());
                        s.setString(2, source);
                        try (var r = s.executeQuery()) {
                            return r.next() ? new Entry(r.getString(1), r.getString(2)) : null;
                        }
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Entry> reserve(UUID owner, String id, String lease, long now) {
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE claim_items SET state=1,lease=?,lease_until=? WHERE"
                                                + " owner=? AND id=? AND state<>2 AND"
                                                + " lease_until<=?")) {
                            s.setString(1, lease);
                            s.setLong(2, now + 30000);
                            s.setString(3, owner.toString());
                            s.setString(4, id);
                            s.setLong(5, now);
                            if (s.executeUpdate() != 1) {
                                c.rollback();
                                return null;
                            }
                        }
                        Entry entry;
                        try (var s =
                                c.prepareStatement(
                                        "SELECT payload FROM claim_items WHERE owner=? AND id=?")) {
                            s.setString(1, owner.toString());
                            s.setString(2, id);
                            try (var r = s.executeQuery()) {
                                if (!r.next())
                                    throw new SQLException("Récompense réservée introuvable");
                                entry = new Entry(id, r.getString(1));
                            }
                        }
                        c.commit();
                        return entry;
                    } catch (SQLException e) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Void> finish(UUID owner, String id, String lease, boolean delivered) {
        return database.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "UPDATE claim_items SET state=?,lease_until=0 WHERE owner=? AND"
                                            + " id=? AND lease=? AND state=1")) {
                        s.setInt(1, delivered ? 2 : 0);
                        s.setString(2, owner.toString());
                        s.setString(3, id);
                        s.setString(4, lease);
                        if (s.executeUpdate() != 1) {
                            if (delivered) {
                                try (var check =
                                        c.prepareStatement(
                                                "SELECT state FROM claim_items WHERE owner=? AND"
                                                        + " id=?")) {
                                    check.setString(1, owner.toString());
                                    check.setString(2, id);
                                    try (var r = check.executeQuery()) {
                                        if (r.next() && r.getInt(1) == 2) return null;
                                    }
                                }
                            }
                            throw new SQLException("Réservation de récompense expirée ou invalide");
                        }
                        return null;
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Set<String>> delivered(UUID owner, Set<String> candidates) {
        if (candidates.isEmpty()) return CompletableFuture.completedFuture(Set.of());
        return database.submit(
                c -> {
                    Set<String> ids = new HashSet<>();
                    try (var s =
                            c.prepareStatement(
                                    "SELECT id FROM claim_items WHERE owner=? AND state=2 AND id IN"
                                            + " ("
                                            + String.join(
                                                    ",",
                                                    Collections.nCopies(candidates.size(), "?"))
                                            + ")")) {
                        s.setString(1, owner.toString());
                        int parameter = 2;
                        for (String id : candidates) s.setString(parameter++, id);
                        try (var r = s.executeQuery()) {
                            while (r.next()) ids.add(r.getString(1));
                        }
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                    return ids;
                });
    }

    /** Fractionnement atomique avant livraison : une partie livrable et le surplus durable. */
    public CompletableFuture<Boolean> split(
            UUID owner, String id, String expected, String deliverable, String overflow) {
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        try (var update =
                                c.prepareStatement(
                                        "UPDATE claim_items SET payload=? WHERE id=? AND owner=?"
                                                + " AND state=0 AND payload=?")) {
                            update.setString(1, deliverable);
                            update.setString(2, id);
                            update.setString(3, owner.toString());
                            update.setString(4, expected);
                            if (update.executeUpdate() != 1) {
                                c.rollback();
                                return false;
                            }
                        }
                        insert(c, owner, "overflow:" + id + ":" + UUID.randomUUID(), overflow);
                        c.commit();
                        return true;
                    } catch (SQLException error) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(error);
                    }
                });
    }
}
