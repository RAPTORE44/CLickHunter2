package fr.cliker.clikerv2;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class DatabaseManager {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final String jdbcUrl;
    private final ExecutorService executor;

    public DatabaseManager(JavaPlugin plugin, String relativePath) {
        this.plugin = plugin;
        File database = new File(plugin.getDataFolder(), relativePath);
        File parent = database.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Impossible de créer le dossier de la base de données");
        }
        this.jdbcUrl = "jdbc:sqlite:" + database.getAbsolutePath();
        this.executor =
                Executors.newSingleThreadExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "ClikerV2-Database");
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    public void initialize() throws SQLException {
        try (Connection connection = open();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA journal_mode=WAL");
            statement.executeUpdate("PRAGMA busy_timeout=5000");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_balances (uuid TEXT PRIMARY KEY NOT NULL,"
                            + " player_name TEXT NOT NULL, ecus INTEGER NOT NULL DEFAULT 0,"
                            + " gemmes_sombres INTEGER NOT NULL DEFAULT 0, cendres INTEGER NOT NULL"
                            + " DEFAULT 0)");
            migrateMobSpawns(statement);
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS mob_spawns (id TEXT PRIMARY KEY NOT NULL, mob_id"
                        + " TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z"
                        + " REAL NOT NULL, yaw REAL NOT NULL)");
            migrateMobSpawnsBossColumn(statement);
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_upgrades (uuid TEXT PRIMARY KEY NOT NULL,"
                        + " richesse INTEGER NOT NULL DEFAULT 0, fortune INTEGER NOT NULL DEFAULT"
                        + " 0, sagesse INTEGER NOT NULL DEFAULT 0, force INTEGER NOT NULL DEFAULT"
                        + " 0, vitesse INTEGER NOT NULL DEFAULT 0, vitalite INTEGER NOT NULL"
                        + " DEFAULT 0, resistance INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_mob_kills (uuid TEXT PRIMARY KEY NOT NULL,"
                            + " total INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_prestige (uuid TEXT PRIMARY KEY NOT NULL,"
                            + " level INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS merchants (id TEXT PRIMARY KEY NOT NULL, mob_id"
                        + " TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z"
                        + " REAL NOT NULL, yaw REAL NOT NULL)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_levels (uuid TEXT PRIMARY KEY NOT NULL,"
                            + " player_name TEXT NOT NULL, level INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS holograms (id TEXT PRIMARY KEY NOT NULL, type TEXT"
                        + " NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL"
                        + " NOT NULL)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS auctions (id TEXT PRIMARY KEY NOT NULL, seller_uuid"
                            + " TEXT NOT NULL, seller_name TEXT NOT NULL, item TEXT NOT NULL, price"
                            + " INTEGER NOT NULL, listed_at INTEGER NOT NULL)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS server_spawn (id INTEGER PRIMARY KEY CHECK (id ="
                        + " 1), world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT"
                        + " NULL, yaw REAL NOT NULL, pitch REAL NOT NULL)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS stations (id TEXT PRIMARY KEY NOT NULL, type TEXT"
                        + " NOT NULL, world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL,"
                        + " z INTEGER NOT NULL)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS bags (uuid TEXT PRIMARY KEY NOT NULL, items TEXT"
                            + " NOT NULL)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS buyzone_unlocks (uuid TEXT NOT NULL, zone_id TEXT"
                        + " NOT NULL, permission TEXT NOT NULL, purchased_at TEXT NOT NULL DEFAULT"
                        + " CURRENT_TIMESTAMP, PRIMARY KEY(uuid, zone_id))");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS claim_items(id TEXT PRIMARY KEY, owner TEXT NOT"
                        + " NULL, source TEXT NOT NULL, payload TEXT NOT NULL, state INTEGER NOT"
                        + " NULL DEFAULT 0, lease TEXT, lease_until INTEGER NOT NULL DEFAULT 0,"
                        + " UNIQUE(owner,source))");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS claim_owner_state ON claim_items(owner,state)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS outgoing_items(id TEXT PRIMARY KEY,owner TEXT NOT"
                        + " NULL,payload TEXT NOT NULL,state INTEGER NOT NULL DEFAULT 0 CHECK(state"
                        + " IN(0,1,2,3)))");
            statement.executeUpdate(
                    "CREATE UNIQUE INDEX IF NOT EXISTS outgoing_owner_pending ON"
                        + " outgoing_items(owner) WHERE state=0");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS referral_players(uuid TEXT PRIMARY KEY, name TEXT"
                            + " NOT NULL, first_join INTEGER NOT NULL, deadline INTEGER NOT NULL,"
                            + " last_seen INTEGER NOT NULL)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS referral_player_name ON referral_players(name"
                            + " COLLATE NOCASE)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS referrals(recruit TEXT PRIMARY KEY, sponsor TEXT"
                            + " NOT NULL, created_at INTEGER NOT NULL)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS private_notices(id TEXT PRIMARY KEY, owner TEXT NOT"
                        + " NULL, message TEXT NOT NULL, delivered INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS notice_owner ON private_notices(owner,delivered)");
            // Les anciennes réservations ne sont plus actives après un redémarrage du plugin.
            statement.executeUpdate("UPDATE claim_items SET lease_until=0 WHERE state=1");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS reward_ledger(source TEXT PRIMARY KEY,owner TEXT"
                            + " NOT NULL,kind TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT"
                            + " CURRENT_TIMESTAMP)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS earned_xp(owner TEXT PRIMARY KEY,total INTEGER NOT"
                            + " NULL DEFAULT 0)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS bestiary_stats(owner TEXT NOT NULL,mob TEXT NOT"
                        + " NULL,boss INTEGER NOT NULL,kills INTEGER NOT NULL,xp INTEGER NOT"
                        + " NULL,last_ecus INTEGER NOT NULL,last_gems INTEGER NOT NULL,last_cendres"
                        + " INTEGER NOT NULL,last_xp INTEGER NOT NULL,PRIMARY"
                        + " KEY(owner,mob,boss))");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS bestiary_money(owner TEXT NOT NULL,mob TEXT NOT"
                            + " NULL,currency TEXT NOT NULL,amount INTEGER NOT NULL,PRIMARY"
                            + " KEY(owner,mob,currency))");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS hunting_contracts(owner TEXT NOT NULL,contract TEXT"
                        + " NOT NULL,cycle TEXT NOT NULL,baseline INTEGER NOT NULL,claimed INTEGER"
                        + " NOT NULL DEFAULT 0,PRIMARY KEY(owner,contract,cycle))");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS outposts(id INTEGER PRIMARY KEY AUTOINCREMENT,owner"
                        + " TEXT NOT NULL UNIQUE,name TEXT NOT NULL,tier INTEGER NOT NULL DEFAULT"
                        + " 0)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS outpost_trusted(owner TEXT NOT NULL,guest TEXT NOT"
                            + " NULL,PRIMARY KEY(owner,guest))");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS prestige_resets(owner TEXT PRIMARY KEY,level"
                            + " INTEGER NOT NULL,earned_xp INTEGER NOT NULL)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS server_counters(id TEXT PRIMARY KEY,value INTEGER"
                            + " NOT NULL DEFAULT 0)");
            statement.executeUpdate(
                    "INSERT OR IGNORE INTO server_counters(id,value) SELECT"
                        + " 'mob_kills',COALESCE(SUM(kills),0) FROM bestiary_stats WHERE boss=0");
            migrateAuctionsExpiredColumn(statement);
            boolean feeColumn = false;
            try (var columns = statement.executeQuery("PRAGMA table_info(auctions)")) {
                while (columns.next())
                    if ("fee_paid".equals(columns.getString("name"))) feeColumn = true;
            }
            if (!feeColumn)
                statement.executeUpdate(
                        "ALTER TABLE auctions ADD COLUMN fee_paid INTEGER NOT NULL DEFAULT 0");
            migrateUpgradesCadenceColumn(statement);
        }
    }

    /**
     * Ancien schéma : une seule zone de spawn par mob (mob_id en clé primaire). Migre vers un id
     * unique par zone pour permettre plusieurs spawns par mob.
     */
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
        statement.executeUpdate(
                "CREATE TABLE mob_spawns (id TEXT PRIMARY KEY NOT NULL, mob_id TEXT NOT NULL, world"
                    + " TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL"
                    + " NOT NULL)");
        statement.executeUpdate(
                "INSERT INTO mob_spawns (id, mob_id, world, x, y, z, yaw) SELECT mob_id, mob_id,"
                        + " world, x, y, z, yaw FROM mob_spawns_old");
        statement.executeUpdate("DROP TABLE mob_spawns_old");
    }

    /**
     * Ajoute la colonne "boss" (mobs golems) aux installations existantes qui ne l'ont pas encore.
     */
    private void migrateMobSpawnsBossColumn(Statement statement) throws SQLException {
        boolean hasBossColumn = false;
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(mob_spawns)")) {
            while (columns.next()) {
                if ("boss".equalsIgnoreCase(columns.getString("name"))) hasBossColumn = true;
            }
        }
        if (!hasBossColumn)
            statement.executeUpdate(
                    "ALTER TABLE mob_spawns ADD COLUMN boss INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * Ajoute la colonne "expired" (annonces hôtel des ventes expirées après 72h) aux installations
     * existantes.
     */
    private void migrateAuctionsExpiredColumn(Statement statement) throws SQLException {
        boolean hasColumn = false;
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(auctions)")) {
            while (columns.next()) {
                if ("expired".equalsIgnoreCase(columns.getString("name"))) hasColumn = true;
            }
        }
        if (!hasColumn)
            statement.executeUpdate(
                    "ALTER TABLE auctions ADD COLUMN expired INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * Ajoute la colonne "cadence" (upgrade de vitesse d'attaque /autoclick) aux installations
     * existantes.
     */
    private void migrateUpgradesCadenceColumn(Statement statement) throws SQLException {
        boolean hasColumn = false;
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(player_upgrades)")) {
            while (columns.next()) {
                if ("cadence".equalsIgnoreCase(columns.getString("name"))) hasColumn = true;
            }
        }
        if (!hasColumn)
            statement.executeUpdate(
                    "ALTER TABLE player_upgrades ADD COLUMN cadence INTEGER NOT NULL DEFAULT 0");
    }

    public <T> CompletableFuture<T> submit(Function<Connection, T> operation) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try (Connection connection = open()) {
                        return operation.apply(connection);
                    } catch (SQLException exception) {
                        throw new DatabaseException(exception);
                    }
                },
                executor);
    }

    public void invalidRecord(String table, String id, RuntimeException error) {
        plugin.getLogger()
                .log(
                        java.util.logging.Level.WARNING,
                        "Position invalide ignorée dans "
                                + table
                                + " ("
                                + id
                                + "). Enregistrement conservé.",
                        error);
    }

    /** Ne bloque jamais le thread serveur en attendant un résultat SQL. */
    public <T> CompletableFuture<T> onServerThread(java.util.function.Supplier<T> operation) {
        if (org.bukkit.Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(operation.get());
            } catch (RuntimeException error) {
                return CompletableFuture.failedFuture(error);
            }
        }
        var future = new CompletableFuture<T>();
        try {
            org.bukkit.Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                try {
                                    future.complete(operation.get());
                                } catch (RuntimeException error) {
                                    future.completeExceptionally(error);
                                }
                            });
        } catch (RuntimeException error) {
            future.completeExceptionally(error);
        }
        return future;
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
        } catch (SQLException error) {
            connection.close();
            throw error;
        }
        return connection;
    }

    public void close() {
        executor.shutdown();
        try {
            // Laisser les dernières sauvegardes de coffres se terminer avant l'arrêt du serveur.
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Les sauvegardes SQLite ne sont pas terminées après 10 secondes");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Attente des sauvegardes SQLite interrompue", exception);
        }
    }

    public static final class DatabaseException extends RuntimeException {
        public DatabaseException(Throwable cause) {
            super(cause);
        }
    }
}
