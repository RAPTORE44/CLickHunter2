package fr.cliker.clikerv2.discord.sync;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class RankLinkStore {
    public record Link(String id, UUID owner, String discord, String guild, boolean unlinking) {}

    public record State(Boolean baseline, Boolean pending, boolean toMinecraft) {}

    private final DatabaseManager database;

    public RankLinkStore(DatabaseManager database) {
        this.database = database;
    }

    @FunctionalInterface
    private interface Query<T> {
        T run(Connection c) throws SQLException;
    }

    private <T> CompletableFuture<T> query(Query<T> work) {
        return database.submit(
                c -> {
                    try {
                        return work.run(c);
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    private static Link row(ResultSet rs) throws SQLException {
        return new Link(
                rs.getString("id"),
                UUID.fromString(rs.getString("owner")),
                rs.getString("discord"),
                rs.getString("guild"),
                rs.getBoolean("unlinking"));
    }

    private static Boolean nullable(ResultSet r, int column) throws SQLException {
        boolean value = r.getBoolean(column);
        return r.wasNull() ? null : value;
    }

    public CompletableFuture<Link> link(UUID owner) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement("SELECT * FROM discord_rank_links WHERE owner=?")) {
                        q.setString(1, owner.toString());
                        try (var r = q.executeQuery()) {
                            return r.next() ? row(r) : null;
                        }
                    }
                });
    }

    public CompletableFuture<List<Link>> page(String guild, String after, int limit) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "SELECT * FROM discord_rank_links WHERE guild=? AND owner>?"
                                        + " ORDER BY owner LIMIT ?")) {
                        q.setString(1, guild);
                        q.setString(2, after);
                        q.setInt(3, limit);
                        List<Link> links = new ArrayList<>();
                        try (var r = q.executeQuery()) {
                            while (r.next()) links.add(row(r));
                        }
                        return List.copyOf(links);
                    }
                });
    }

    public CompletableFuture<Boolean> issue(UUID owner, String hash, long now, long expires) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement("DELETE FROM discord_link_codes WHERE expires<?")) {
                        q.setLong(1, now);
                        q.executeUpdate();
                    }
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO discord_link_codes(owner,hash,issued,expires)"
                                        + " SELECT ?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM"
                                        + " discord_rank_links WHERE owner=?) ON CONFLICT(owner) DO"
                                        + " UPDATE SET"
                                        + " hash=excluded.hash,issued=excluded.issued,expires=excluded.expires")) {
                        q.setString(1, owner.toString());
                        q.setString(2, hash);
                        q.setLong(3, now);
                        q.setLong(4, expires);
                        q.setString(5, owner.toString());
                        return q.executeUpdate() == 1;
                    }
                });
    }

    /** Une transaction empêche les doubles liaisons et consomme le code une seule fois. */
    public CompletableFuture<Link> redeem(
            String hash, String discord, String guild, long messageTime, long now) {
        return query(
                c -> {
                    c.setAutoCommit(false);
                    try {
                        String owner;
                        try (var q =
                                c.prepareStatement(
                                        "SELECT owner FROM discord_link_codes WHERE hash=? AND"
                                            + " issued<=? AND expires>=?")) {
                            q.setString(1, hash);
                            q.setLong(2, messageTime);
                            q.setLong(3, now);
                            try (var r = q.executeQuery()) {
                                if (!r.next()) {
                                    c.rollback();
                                    return null;
                                }
                                owner = r.getString(1);
                            }
                        }
                        String id = UUID.randomUUID().toString();
                        try (var q =
                                c.prepareStatement(
                                        "INSERT OR IGNORE INTO"
                                            + " discord_rank_links(id,owner,discord,guild,unlinking)"
                                            + " VALUES(?,?,?,?,0)")) {
                            q.setString(1, id);
                            q.setString(2, owner);
                            q.setString(3, discord);
                            q.setString(4, guild);
                            if (q.executeUpdate() != 1) {
                                c.rollback();
                                return null;
                            }
                        }
                        try (var q =
                                c.prepareStatement("DELETE FROM discord_link_codes WHERE hash=?")) {
                            q.setString(1, hash);
                            q.executeUpdate();
                        }
                        c.commit();
                        return new Link(id, UUID.fromString(owner), discord, guild, false);
                    } catch (SQLException e) {
                        c.rollback();
                        throw e;
                    }
                });
    }

    public CompletableFuture<Boolean> beginUnlink(UUID owner) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "UPDATE discord_rank_links SET unlinking=1 WHERE owner=?")) {
                        q.setString(1, owner.toString());
                        return q.executeUpdate() == 1;
                    }
                });
    }

    public CompletableFuture<Void> delete(Link link) {
        return query(
                c -> {
                    c.setAutoCommit(false);
                    try {
                        try (var q =
                                c.prepareStatement(
                                        "DELETE FROM discord_rank_state WHERE link_id=?")) {
                            q.setString(1, link.id());
                            q.executeUpdate();
                        }
                        try (var q =
                                c.prepareStatement(
                                        "DELETE FROM discord_rank_links WHERE id=? AND"
                                            + " unlinking=1")) {
                            q.setString(1, link.id());
                            q.executeUpdate();
                        }
                        c.commit();
                        return null;
                    } catch (SQLException e) {
                        c.rollback();
                        throw e;
                    }
                });
    }

    public CompletableFuture<State> state(Link link, String group, String role) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "SELECT baseline,pending,to_minecraft FROM discord_rank_state"
                                        + " WHERE link_id=? AND group_name=? AND role=?")) {
                        q.setString(1, link.id());
                        q.setString(2, group);
                        q.setString(3, role);
                        try (var r = q.executeQuery()) {
                            return r.next()
                                    ? new State(nullable(r, 1), nullable(r, 2), r.getBoolean(3))
                                    : new State(null, null, false);
                        }
                    }
                });
    }

    public CompletableFuture<Boolean> pending(
            Link link, String group, String role, boolean target, boolean toMinecraft) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " discord_rank_state(link_id,group_name,role,pending,to_minecraft)"
                                        + " SELECT ?,?,?,?,? WHERE EXISTS(SELECT 1 FROM"
                                        + " discord_rank_links WHERE id=? AND unlinking=0) ON"
                                        + " CONFLICT(link_id,group_name,role) DO UPDATE SET"
                                        + " pending=excluded.pending,to_minecraft=excluded.to_minecraft")) {
                        q.setString(1, link.id());
                        q.setString(2, group);
                        q.setString(3, role);
                        q.setBoolean(4, target);
                        q.setBoolean(5, toMinecraft);
                        q.setString(6, link.id());
                        return q.executeUpdate() == 1;
                    }
                });
    }

    public CompletableFuture<Void> settle(Link link, String group, String role, boolean target) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "UPDATE discord_rank_state SET"
                                        + " baseline=?,pending=NULL,to_minecraft=0 WHERE link_id=?"
                                        + " AND group_name=? AND role=?")) {
                        q.setBoolean(1, target);
                        q.setString(2, link.id());
                        q.setString(3, group);
                        q.setString(4, role);
                        q.executeUpdate();
                        return null;
                    }
                });
    }

    public CompletableFuture<Long> pendingUntil() {
        return query(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT COALESCE(MAX(expires),0) FROM"
                                                + " discord_link_codes")) {
                        return r.next() ? r.getLong(1) : 0L;
                    }
                });
    }

    public CompletableFuture<Boolean> validCode(String hash, long messageTime, long now) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "SELECT 1 FROM discord_link_codes WHERE hash=? AND issued<=?"
                                        + " AND expires>=?")) {
                        q.setString(1, hash);
                        q.setLong(2, messageTime);
                        q.setLong(3, now);
                        try (var r = q.executeQuery()) {
                            return r.next();
                        }
                    }
                });
    }

    public CompletableFuture<String> cursor(String channel) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "SELECT message_id FROM discord_rank_cursor WHERE channel=?")) {
                        q.setString(1, channel);
                        try (var r = q.executeQuery()) {
                            return r.next() ? r.getString(1) : null;
                        }
                    }
                });
    }

    public CompletableFuture<Void> cursor(String channel, String id) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO discord_rank_cursor(channel,message_id)"
                                        + " VALUES(?,?) ON CONFLICT(channel) DO UPDATE SET"
                                        + " message_id=excluded.message_id")) {
                        q.setString(1, channel);
                        q.setString(2, id);
                        q.executeUpdate();
                        return null;
                    }
                });
    }
}
