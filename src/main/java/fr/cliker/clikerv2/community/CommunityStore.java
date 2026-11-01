package fr.cliker.clikerv2.community;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.rewards.MailStore;
import fr.cliker.clikerv2.rewards.OutgoingStore;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Courrier et points AFK : aucune mutation d'inventaire sur le thread SQLite. */
public final class CommunityStore {
    private final DatabaseManager db;
    private final CompletableFuture<Void> ready;

    public record Letter(
            String id, UUID sender, String name, String body, String payload, boolean read) {}

    @FunctionalInterface
    private interface Query<T> {
        T run(Connection c) throws SQLException;
    }

    public CommunityStore(DatabaseManager db) {
        this.db = db;
        ready =
                query(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.execute(
                                        "CREATE TABLE IF NOT EXISTS community_mail(id TEXT PRIMARY"
                                                + " KEY, owner TEXT NOT NULL, sender TEXT NOT NULL,"
                                                + " name TEXT NOT NULL, body TEXT NOT NULL, payload"
                                                + " TEXT, seen INTEGER NOT NULL DEFAULT 0, created"
                                                + " INTEGER NOT NULL)");
                                s.execute(
                                        "CREATE INDEX IF NOT EXISTS community_mail_owner ON"
                                                + " community_mail(owner,created)");
                                s.execute(
                                        "CREATE TABLE IF NOT EXISTS mail_blocks(owner TEXT NOT"
                                                + " NULL,sender TEXT NOT NULL,PRIMARY"
                                                + " KEY(owner,sender))");
                                s.execute(
                                        "CREATE TABLE IF NOT EXISTS afk_accounts(owner TEXT PRIMARY"
                                                + " KEY,seconds INTEGER NOT NULL DEFAULT 0,points"
                                                + " INTEGER NOT NULL DEFAULT 0)");
                                s.execute(
                                        "CREATE TABLE IF NOT EXISTS afk_receipts(id TEXT PRIMARY"
                                                + " KEY)");
                                s.execute(
                                        "CREATE TABLE IF NOT EXISTS afk_time_sessions(id TEXT"
                                            + " PRIMARY KEY,owner TEXT NOT NULL,seconds INTEGER NOT"
                                            + " NULL DEFAULT 0)");
                            }
                            return null;
                        });
    }

    private <T> CompletableFuture<T> query(Query<T> action) {
        return db.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        T result = action.run(c);
                        c.commit();
                        return result;
                    } catch (SQLException | RuntimeException e) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    private <T> CompletableFuture<T> after(Query<T> action) {
        return ready.thenCompose(v -> query(action));
    }

    public CompletableFuture<Void> send(
            String id,
            UUID owner,
            UUID sender,
            String name,
            String body,
            String payload,
            String receipt,
            int maximum) {
        return after(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "SELECT 1 FROM mail_blocks WHERE owner=? AND sender=?")) {
                        s.setString(1, owner.toString());
                        s.setString(2, sender.toString());
                        try (var r = s.executeQuery()) {
                            if (r.next()) throw new SQLException("Ce joueur refuse ton courrier.");
                        }
                    }
                    try (var s =
                            c.prepareStatement(
                                    "SELECT count(*) FROM community_mail WHERE owner=?")) {
                        s.setString(1, owner.toString());
                        try (var r = s.executeQuery()) {
                            if (r.next() && r.getInt(1) >= maximum)
                                throw new SQLException("Boîte aux lettres pleine.");
                        }
                    }
                    if (body.length() > 15000 || (body.isBlank() && payload == null))
                        throw new SQLException("Courrier vide ou trop long.");
                    if (receipt != null) OutgoingStore.commit(c, receipt, sender, payload);
                    try (var s =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " community_mail(id,owner,sender,name,body,payload,created)"
                                        + " VALUES(?,?,?,?,?,?,?)")) {
                        s.setString(1, id);
                        s.setString(2, owner.toString());
                        s.setString(3, sender.toString());
                        s.setString(4, name);
                        s.setString(5, body);
                        s.setString(6, payload);
                        s.setLong(7, System.currentTimeMillis());
                        s.executeUpdate();
                    }
                    return null;
                });
    }

    public CompletableFuture<List<Letter>> inbox(UUID owner, int page) {
        return after(
                c -> {
                    List<Letter> letters = new ArrayList<>();
                    try (var s =
                            c.prepareStatement(
                                    "SELECT id,sender,name,body,payload,seen FROM community_mail"
                                        + " WHERE owner=? ORDER BY created DESC,id LIMIT 45 OFFSET"
                                        + " ?")) {
                        s.setString(1, owner.toString());
                        s.setInt(2, Math.max(0, Math.min(10000, page)) * 45);
                        try (var r = s.executeQuery()) {
                            while (r.next())
                                letters.add(
                                        new Letter(
                                                r.getString(1),
                                                UUID.fromString(r.getString(2)),
                                                r.getString(3),
                                                r.getString(4),
                                                r.getString(5),
                                                r.getBoolean(6)));
                        }
                    }
                    return letters;
                });
    }

    public CompletableFuture<Integer> unread(UUID owner) {
        return after(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "SELECT count(*) FROM community_mail WHERE owner=? AND"
                                            + " seen=0")) {
                        s.setString(1, owner.toString());
                        try (var r = s.executeQuery()) {
                            return r.next() ? r.getInt(1) : 0;
                        }
                    }
                });
    }

    public CompletableFuture<Void> read(UUID owner, String id) {
        return after(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "UPDATE community_mail SET seen=1 WHERE owner=? AND id=?")) {
                        s.setString(1, owner.toString());
                        s.setString(2, id);
                        s.executeUpdate();
                    }
                    return null;
                });
    }

    public CompletableFuture<Boolean> take(UUID owner, String id) {
        return after(
                c -> {
                    String payload;
                    try (var s =
                            c.prepareStatement(
                                    "SELECT payload FROM community_mail WHERE id=? AND owner=?")) {
                        s.setString(1, id);
                        s.setString(2, owner.toString());
                        try (var r = s.executeQuery()) {
                            if (!r.next() || (payload = r.getString(1)) == null) return false;
                        }
                    }
                    MailStore.insert(c, owner, "letter:" + id, payload);
                    try (var s =
                            c.prepareStatement(
                                    "UPDATE community_mail SET payload=NULL,seen=1 WHERE id=? AND"
                                            + " owner=?")) {
                        s.setString(1, id);
                        s.setString(2, owner.toString());
                        s.executeUpdate();
                    }
                    return true;
                });
    }

    public CompletableFuture<Boolean> delete(UUID owner, String id) {
        return after(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "DELETE FROM community_mail WHERE id=? AND owner=? AND payload"
                                            + " IS NULL")) {
                        s.setString(1, id);
                        s.setString(2, owner.toString());
                        return s.executeUpdate() == 1;
                    }
                });
    }

    public CompletableFuture<Void> block(UUID owner, UUID sender, boolean block) {
        return after(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    block
                                            ? "INSERT OR IGNORE INTO mail_blocks(owner,sender)"
                                                    + " VALUES(?,?)"
                                            : "DELETE FROM mail_blocks WHERE owner=? AND"
                                                    + " sender=?")) {
                        s.setString(1, owner.toString());
                        s.setString(2, sender.toString());
                        s.executeUpdate();
                    }
                    return null;
                });
    }

    public CompletableFuture<Long> points(UUID owner) {
        return after(
                c -> {
                    try (var s =
                            c.prepareStatement("SELECT points FROM afk_accounts WHERE owner=?")) {
                        s.setString(1, owner.toString());
                        try (var r = s.executeQuery()) {
                            return r.next() ? r.getLong(1) : 0L;
                        }
                    }
                });
    }

    /** Une ligne par session AFK, même si la durée est sauvegardée régulièrement. */
    public CompletableFuture<Long> accrueSession(
            String session, UUID owner, long elapsed, int interval) {
        if (elapsed < 0 || interval < 1)
            return CompletableFuture.failedFuture(new IllegalArgumentException());
        return after(
                c -> {
                    long previous = 0;
                    try (var s =
                            c.prepareStatement(
                                    "SELECT owner,seconds FROM afk_time_sessions WHERE id=?")) {
                        s.setString(1, session);
                        try (var r = s.executeQuery()) {
                            if (r.next()) {
                                if (!owner.toString().equals(r.getString(1)))
                                    throw new SQLException("Session AFK étrangère");
                                previous = r.getLong(2);
                            }
                        }
                    }
                    if (elapsed <= previous) return 0L;
                    try (var s =
                            c.prepareStatement(
                                    "INSERT INTO afk_time_sessions(id,owner,seconds) VALUES(?,?,?)"
                                            + " ON CONFLICT(id) DO UPDATE SET"
                                            + " seconds=excluded.seconds")) {
                        s.setString(1, session);
                        s.setString(2, owner.toString());
                        s.setLong(3, elapsed);
                        s.executeUpdate();
                    }
                    try (var s =
                            c.prepareStatement(
                                    "INSERT OR IGNORE INTO afk_accounts(owner) VALUES(?)")) {
                        s.setString(1, owner.toString());
                        s.executeUpdate();
                    }
                    long remainder;
                    try (var s =
                            c.prepareStatement("SELECT seconds FROM afk_accounts WHERE owner=?")) {
                        s.setString(1, owner.toString());
                        try (var r = s.executeQuery()) {
                            r.next();
                            remainder = r.getLong(1);
                        }
                    }
                    long total = Math.addExact(remainder, elapsed - previous),
                            gained = total / interval;
                    try (var s =
                            c.prepareStatement(
                                    "UPDATE afk_accounts SET seconds=?,points=points+? WHERE"
                                            + " owner=? AND points<=?")) {
                        s.setLong(1, total % interval);
                        s.setLong(2, gained);
                        s.setString(3, owner.toString());
                        s.setLong(4, Long.MAX_VALUE - gained);
                        if (s.executeUpdate() != 1)
                            throw new SQLException("Points AFK maximum dépassés");
                    }
                    return gained;
                });
    }

    /** Le même lot de temps ne peut jamais être crédité deux fois, même après reconnexion. */
    public CompletableFuture<Long> accrue(String receipt, UUID owner, long seconds, int interval) {
        if (seconds < 0 || seconds > 3600 || interval < 1)
            return CompletableFuture.failedFuture(new IllegalArgumentException());
        return after(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "INSERT OR IGNORE INTO afk_receipts(id) VALUES(?)")) {
                        s.setString(1, receipt);
                        if (s.executeUpdate() == 0) return 0L;
                    }
                    try (var s =
                            c.prepareStatement(
                                    "INSERT OR IGNORE INTO afk_accounts(owner) VALUES(?)")) {
                        s.setString(1, owner.toString());
                        s.executeUpdate();
                    }
                    long total;
                    try (var s =
                            c.prepareStatement("SELECT seconds FROM afk_accounts WHERE owner=?")) {
                        s.setString(1, owner.toString());
                        try (var r = s.executeQuery()) {
                            r.next();
                            total = r.getLong(1) + seconds;
                        }
                    }
                    long gained = total / interval;
                    try (var s =
                            c.prepareStatement(
                                    "UPDATE afk_accounts SET seconds=?,points=points+? WHERE"
                                            + " owner=?")) {
                        s.setLong(1, total % interval);
                        s.setLong(2, gained);
                        s.setString(3, owner.toString());
                        s.executeUpdate();
                    }
                    return gained;
                });
    }

    public CompletableFuture<Boolean> buy(String receipt, UUID owner, long price, String payload) {
        if (price < 1) return CompletableFuture.failedFuture(new IllegalArgumentException());
        return after(
                c -> {
                    try (var s = c.prepareStatement("SELECT 1 FROM afk_receipts WHERE id=?")) {
                        s.setString(1, receipt);
                        try (var r = s.executeQuery()) {
                            if (r.next()) return false;
                        }
                    }
                    try (var s =
                            c.prepareStatement(
                                    "UPDATE afk_accounts SET points=points-? WHERE owner=? AND"
                                            + " points>=?")) {
                        s.setLong(1, price);
                        s.setString(2, owner.toString());
                        s.setLong(3, price);
                        if (s.executeUpdate() != 1) return false;
                    }
                    MailStore.insert(c, owner, "afk-shop:" + receipt, payload);
                    try (var s = c.prepareStatement("INSERT INTO afk_receipts(id) VALUES(?)")) {
                        s.setString(1, receipt);
                        s.executeUpdate();
                    }
                    return true;
                });
    }
}
