package fr.cliker.clikerv2.daily;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Une file SQLite et une transaction IMMEDIATE empêchent les doubles récupérations. */
public final class RewardStore implements AutoCloseable {
    private final String url;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "NorthBlueDaily-SQLite"));
    public record Claim(boolean accepted, DailyProgress progress) {}

    public RewardStore(Path file) throws Exception {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Class.forName("org.sqlite.JDBC");
        url = "jdbc:sqlite:" + file.toAbsolutePath();
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("CREATE TABLE IF NOT EXISTS daily_progress (uuid TEXT PRIMARY KEY, last_claim TEXT NOT NULL, day INTEGER NOT NULL CHECK(day BETWEEN 1 AND 31))");
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement s = connection.createStatement()) { s.execute("PRAGMA busy_timeout=5000"); }
        return connection;
    }

    public CompletableFuture<DailyProgress> load(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = open()) { return read(c, uuid); }
            catch (SQLException e) { throw new CompletionException(e); }
        }, executor);
    }

    public CompletableFuture<Claim> claim(UUID uuid, int requestedDay, Supplier<LocalDate> calendarDate) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = open(); Statement transaction = c.createStatement()) {
                transaction.execute("BEGIN IMMEDIATE");
                try {
                    // La date est évaluée au moment de la transaction, même si la file traverse minuit.
                    LocalDate today = calendarDate.get();
                    DailyProgress previous = read(c, uuid);
                    if (previous.claimedToday(today) || previous.currentDay(today) != requestedDay) {
                        transaction.execute("ROLLBACK");
                        return new Claim(false, previous);
                    }
                    DailyProgress next = new DailyProgress(today, requestedDay);
                    try (PreparedStatement s = c.prepareStatement("INSERT INTO daily_progress(uuid,last_claim,day) VALUES(?,?,?) ON CONFLICT(uuid) DO UPDATE SET last_claim=excluded.last_claim, day=excluded.day")) {
                        s.setString(1, uuid.toString()); s.setString(2, today.toString()); s.setInt(3, requestedDay);
                        s.executeUpdate();
                    }
                    transaction.execute("COMMIT");
                    return new Claim(true, next);
                } catch (Exception e) {
                    try { transaction.execute("ROLLBACK"); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                    throw e;
                }
            } catch (Exception e) { throw new CompletionException(e); }
        }, executor);
    }

    private DailyProgress read(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT last_claim,day FROM daily_progress WHERE uuid=?")) {
            s.setString(1, uuid.toString());
            try (ResultSet rows = s.executeQuery()) {
                return rows.next() ? new DailyProgress(LocalDate.parse(rows.getString(1)), rows.getInt(2)) : DailyProgress.EMPTY;
            }
        }
    }

    @Override public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) throw new IllegalStateException("Sauvegardes SQLite encore en cours");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
