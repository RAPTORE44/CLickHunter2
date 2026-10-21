package fr.cliker.clikerv2;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class DatabaseManager {
    private final String jdbcUrl;
    private final ExecutorService executor;

    public DatabaseManager(JavaPlugin plugin, String relativePath) {
        File database = new File(plugin.getDataFolder(), relativePath);
        File parent = database.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Impossible de créer le dossier de la base de données");
        }
        this.jdbcUrl = "jdbc:sqlite:" + database.getAbsolutePath();
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ClikerV2-Database");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void initialize() throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA journal_mode=WAL");
            statement.executeUpdate("PRAGMA busy_timeout=5000");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_balances (uuid TEXT PRIMARY KEY NOT NULL, player_name TEXT NOT NULL, ecus INTEGER NOT NULL DEFAULT 0, gemmes_sombres INTEGER NOT NULL DEFAULT 0, cendres INTEGER NOT NULL DEFAULT 0)");
            migrateMobSpawns(statement);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS mob_spawns (id TEXT PRIMARY KEY NOT NULL, mob_id TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL)");
            migrateMobSpawnsBossColumn(statement);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_upgrades (uuid TEXT PRIMARY KEY NOT NULL, richesse INTEGER NOT NULL DEFAULT 0, fortune INTEGER NOT NULL DEFAULT 0, sagesse INTEGER NOT NULL DEFAULT 0, force INTEGER NOT NULL DEFAULT 0, vitesse INTEGER NOT NULL DEFAULT 0, vitalite INTEGER NOT NULL DEFAULT 0, resistance INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_mob_kills (uuid TEXT PRIMARY KEY NOT NULL, total INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_prestige (uuid TEXT PRIMARY KEY NOT NULL, level INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS merchants (id TEXT PRIMARY KEY NOT NULL, mob_id TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_levels (uuid TEXT PRIMARY KEY NOT NULL, player_name TEXT NOT NULL, level INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS holograms (id TEXT PRIMARY KEY NOT NULL, type TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS auctions (id TEXT PRIMARY KEY NOT NULL, seller_uuid TEXT NOT NULL, seller_name TEXT NOT NULL, item TEXT NOT NULL, price INTEGER NOT NULL, listed_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS server_spawn (id INTEGER PRIMARY KEY CHECK (id = 1), world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stations (id TEXT PRIMARY KEY NOT NULL, type TEXT NOT NULL, world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS bags (uuid TEXT PRIMARY KEY NOT NULL, items TEXT NOT NULL)");
            migrateAuctionsExpiredColumn(statement);
            migrateUpgradesCadenceColumn(statement);
        }
    }

    /** Ancien schéma : une seule zone de spawn par mob (mob_id en clé primaire). Migre vers un id unique par zone pour permettre plusieurs spawns par mob. */
    private void migrateMobSpawns(Statement statement) throws SQLException {
        boolean tableExists = false;
        boolean hasIdColumn = false;
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(mob_spawns)")) {
            while (columns.next()) {
                tableExists = true;
                if ("id".equalsIgnoreCase(columns.getString("name"))) hasIdColumn = true;
            }
        }
        if (!tableExists || hasIdColumn) return;
        statement.executeUpdate("ALTER TABLE mob_spawns RENAME TO mob_spawns_old");
        statement.executeUpdate("CREATE TABLE mob_spawns (id TEXT PRIMARY KEY NOT NULL, mob_id TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL)");
        statement.executeUpdate("INSERT INTO mob_spawns (id, mob_id, world, x, y, z, yaw) SELECT mob_id, mob_id, world, x, y, z, yaw FROM mob_spawns_old");
        statement.executeUpdate("DROP TABLE mob_spawns_old");
    }

    /** Ajoute la colonne "boss" (mobs golems) aux installations existantes qui ne l'ont pas encore. */
    private void migrateMobSpawnsBossColumn(Statement statement) throws SQLException {
        boolean hasBossColumn = false;
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(mob_spawns)")) {
            while (columns.next()) {
                if ("boss".equalsIgnoreCase(columns.getString("name"))) hasBossColumn = true;
            }
        }
        if (!hasBossColumn) statement.executeUpdate("ALTER TABLE mob_spawns ADD COLUMN boss INTEGER NOT NULL DEFAULT 0");
    }

    /** Ajoute la colonne "expired" (annonces hôtel des ventes expirées après 72h) aux installations existantes. */
    private void migrateAuctionsExpiredColumn(Statement statement) throws SQLException {
        boolean hasColumn = false;
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(auctions)")) {
            while (columns.next()) {
                if ("expired".equalsIgnoreCase(columns.getString("name"))) hasColumn = true;
            }
        }
        if (!hasColumn) statement.executeUpdate("ALTER TABLE auctions ADD COLUMN expired INTEGER NOT NULL DEFAULT 0");
    }

    /** Ajoute la colonne "cadence" (upgrade de vitesse d'attaque /autoclick) aux installations existantes. */
    private void migrateUpgradesCadenceColumn(Statement statement) throws SQLException {
        boolean hasColumn = false;
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(player_upgrades)")) {
            while (columns.next()) {
                if ("cadence".equalsIgnoreCase(columns.getString("name"))) hasColumn = true;
            }
        }
        if (!hasColumn) statement.executeUpdate("ALTER TABLE player_upgrades ADD COLUMN cadence INTEGER NOT NULL DEFAULT 0");
    }

    public <T> CompletableFuture<T> submit(Function<Connection, T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = open()) {
                return operation.apply(connection);
            } catch (SQLException exception) {
                throw new DatabaseException(exception);
            }
        }, executor);
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public void close() {
        executor.shutdown();
    }

    public static final class DatabaseException extends RuntimeException {
        public DatabaseException(Throwable cause) { super(cause); }
    }
}
