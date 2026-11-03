package fr.cliker.clikerv2.discord.game;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.*;
import java.util.concurrent.CompletableFuture;

public final class DiscordGameStore {
    public record Message(String id, long updated) {}

    private final DatabaseManager db;

    public DiscordGameStore(DatabaseManager db) {
        this.db = db;
    }

    @FunctionalInterface
    private interface Query<T> {
        T run(Connection c) throws SQLException;
    }

    private <T> CompletableFuture<T> query(Query<T> work) {
        return db.submit(
                c -> {
                    try {
                        return work.run(c);
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Message> message(String bot, String channel, String board) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "SELECT message_id,updated FROM discord_game_boards WHERE bot=?"
                                        + " AND channel=? AND board=?")) {
                        q.setString(1, bot);
                        q.setString(2, channel);
                        q.setString(3, board);
                        try (var r = q.executeQuery()) {
                            return r.next() ? new Message(r.getString(1), r.getLong(2)) : null;
                        }
                    }
                });
    }

    public CompletableFuture<Void> save(
            String bot, String channel, String board, String id, long updated) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " discord_game_boards(bot,channel,board,message_id,updated)"
                                        + " VALUES(?,?,?,?,?) ON CONFLICT(bot,channel,board) DO"
                                        + " UPDATE SET"
                                        + " message_id=excluded.message_id,updated=excluded.updated")) {
                        q.setString(1, bot);
                        q.setString(2, channel);
                        q.setString(3, board);
                        q.setString(4, id);
                        q.setLong(5, updated);
                        q.executeUpdate();
                        return null;
                    }
                });
    }

    public CompletableFuture<Boolean> sent(String id) {
        return query(
                c -> {
                    try (var q = c.prepareStatement("SELECT 1 FROM discord_game_sent WHERE id=?")) {
                        q.setString(1, id);
                        try (var r = q.executeQuery()) {
                            return r.next();
                        }
                    }
                });
    }

    public CompletableFuture<Void> sent(String id, long time) {
        return query(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT OR IGNORE INTO discord_game_sent(id,sent)"
                                        + " VALUES(?,?)")) {
                        q.setString(1, id);
                        q.setLong(2, time);
                        q.executeUpdate();
                    }
                    try (var q = c.prepareStatement("DELETE FROM discord_game_sent WHERE sent<?")) {
                        q.setLong(1, time - 30L * 86400000);
                        q.executeUpdate();
                    }
                    return null;
                });
    }
}
