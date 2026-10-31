package fr.cliker.clikerv2.rankings;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.donors.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.hunting.*;
import fr.cliker.clikerv2.level.PlayerLevelStore;
import fr.cliker.clikerv2.mobs.MobKillStore;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.sql.*;
import java.time.*;
import java.util.*;

class RankingStoreTest {
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private RankingStore store;
    private final ZoneId zone = ZoneId.of("Europe/Paris");
    private String path;

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        path = "ranks-" + UUID.randomUUID() + ".db";
        db = new DatabaseManager(plugin, path);
        db.initialize();
        store = new RankingStore(db);
    }

    @AfterEach
    void cleanup() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void sqlAt(String instant, String... statements) {
        db.submit(
                        c -> {
                            try {
                                RankingHistory.register(
                                        c,
                                        zone,
                                        Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
                                try (var s = c.createStatement()) {
                                    for (String sql : statements) s.executeUpdate(sql);
                                }
                                return null;
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
    }

    private long value(RankingMetric metric, RankingPeriod period, LocalDate day) {
        var rows = store.board(metric, period, day).join().entries();
        return rows.isEmpty() ? 0 : rows.getFirst().value();
    }

    @Test
    void currenciesKeepCalendarHistoryAndUseNetChangesRatherThanRefundInflation() {
        sqlAt("2024-01-31T12:00:00Z", "INSERT INTO player_balances VALUES('a','Alice',1000,0,0)");
        sqlAt("2024-02-01T12:00:00Z", "UPDATE player_balances SET ecus=1100 WHERE uuid='a'");
        sqlAt(
                "2024-02-02T12:00:00Z",
                "UPDATE player_balances SET ecus=1070 WHERE uuid='a'",
                "UPDATE player_balances SET ecus=1060 WHERE uuid='a'",
                "UPDATE player_balances SET ecus=1070 WHERE uuid='a'");
        assertEquals(1000, value(RankingMetric.ECUS, RankingPeriod.DAY, LocalDate.of(2024, 1, 31)));
        assertEquals(70, value(RankingMetric.ECUS, RankingPeriod.MONTH, LocalDate.of(2024, 2, 12)));
        assertEquals(1070, value(RankingMetric.ECUS, RankingPeriod.YEAR, LocalDate.of(2024, 6, 1)));
        assertEquals(1070, value(RankingMetric.ECUS, RankingPeriod.ALL, LocalDate.now(zone)));
    }

    @Test
    void dayBoundariesFollowParisAcrossTheSummerTimeChange() {
        sqlAt("2024-03-31T21:59:59Z", "INSERT INTO player_balances VALUES('a','Alice',10,0,0)");
        sqlAt("2024-03-31T22:00:00Z", "UPDATE player_balances SET ecus=30 WHERE uuid='a'");
        assertEquals(10, value(RankingMetric.ECUS, RankingPeriod.DAY, LocalDate.of(2024, 3, 31)));
        assertEquals(20, value(RankingMetric.ECUS, RankingPeriod.DAY, LocalDate.of(2024, 4, 1)));
    }

    @Test
    void killAndLevelTrackingUsesTheExistingCountersAndIgnoresLevelResets() {
        var id = UUID.randomUUID();
        var economy = new EconomyService(db, Map.of());
        economy.getBalance(id, "Alice", CurrencyType.ECUS).join();
        var kills = new MobKillStore(db);
        kills.add(id, 2).join();
        kills.add(id, 3).join();
        var levels = new PlayerLevelStore(db);
        levels.setLevel(id, "Alice", 0).join();
        levels.setLevel(id, "Alice", 5).join();
        levels.setLevel(id, "Alice", 0).join();
        levels.setLevel(id, "Alice", 7).join();
        assertEquals(5, value(RankingMetric.KILLS, RankingPeriod.DAY, LocalDate.now(zone)));
        assertEquals(12, value(RankingMetric.LEVELS, RankingPeriod.DAY, LocalDate.now(zone)));
        assertEquals(7, value(RankingMetric.LEVELS, RankingPeriod.ALL, LocalDate.now(zone)));
    }

    @Test
    void idempotentRewardsAndPointReceiptsAreCountedOnce() {
        var ledger = new RewardLedger(plugin, db);
        var id = UUID.randomUUID();
        try {
            assertTrue(ledger.grant("unique", id, "Alice", Gains.of(100, 10, 5, 0)).join());
            assertFalse(ledger.grant("unique", id, "Alice", Gains.of(100, 10, 5, 0)).join());
            var today = LocalDate.now(zone);
            assertEquals(100, value(RankingMetric.ECUS, RankingPeriod.DAY, today));
            assertEquals(10, value(RankingMetric.GEMS, RankingPeriod.DAY, today));
            assertEquals(5, value(RankingMetric.ASH, RankingPeriod.DAY, today));
            var donors = new DonorPointsStore(db);
            donors.change("Alice", 999, "transaction", "console").join();
            donors.change("Alice", 999, "transaction", "console").join();
            assertEquals(999, value(RankingMetric.DONORS, RankingPeriod.DAY, today));
            donors.change("Alice", -100, null, "console").join();
            assertEquals(899, value(RankingMetric.DONORS, RankingPeriod.DAY, today));
        } finally {
            ledger.shutdown();
        }
    }

    @Test
    void rolledBackOperationsDoNotProduceRankingProgressAndRestartDoesNotDuplicateHistory()
            throws Exception {
        var today = LocalDate.now(zone);
        db.submit(
                        c -> {
                            try {
                                c.setAutoCommit(false);
                                try (var s = c.createStatement()) {
                                    s.executeUpdate(
                                            "INSERT INTO player_balances"
                                                + " VALUES('a','Alice',100,0,0)");
                                }
                                c.rollback();
                                return null;
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertEquals(0, value(RankingMetric.ECUS, RankingPeriod.DAY, today));
        sqlAt(Instant.now().toString(), "INSERT INTO player_balances VALUES('b','Bob',200,0,0)");
        db.close();
        db = new DatabaseManager(plugin, path);
        db.initialize();
        store = new RankingStore(db);
        assertEquals(200, value(RankingMetric.ECUS, RankingPeriod.DAY, today));
        assertEquals(200, value(RankingMetric.ECUS, RankingPeriod.ALL, today));
    }

    @Test
    void legacyBalancesAreNotBackdatedButTimestampedDonationsAreImportedOnce() throws Exception {
        db.close();
        path = "legacy-" + UUID.randomUUID() + ".db";
        var file = new java.io.File(plugin.getDataFolder(), path);
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                var s = c.createStatement()) {
            s.executeUpdate(
                    "CREATE TABLE player_balances(uuid TEXT PRIMARY KEY,player_name TEXT,ecus"
                        + " INTEGER,gemmes_sombres INTEGER,cendres INTEGER)");
            s.executeUpdate("INSERT INTO player_balances VALUES('a','Alice',1000,0,0)");
            s.executeUpdate(
                    "CREATE TABLE donor_points(name_key TEXT PRIMARY KEY,name TEXT,units INTEGER)");
            s.executeUpdate("INSERT INTO donor_points VALUES('alice','Alice',3999)");
            s.executeUpdate(
                    "CREATE TABLE donor_point_events(receipt TEXT PRIMARY KEY,name_key TEXT,delta"
                        + " INTEGER,actor TEXT,created_at TEXT)");
            s.executeUpdate(
                    "INSERT INTO donor_point_events"
                        + " VALUES('purchase','alice',5000,'console','2024-03-30"
                        + " 23:30:00'),('refund','alice',-1001,'console','2024-03-31 22:15:00')");
        }
        db = new DatabaseManager(plugin, path);
        db.initialize();
        store = new RankingStore(db);
        assertEquals(1000, value(RankingMetric.ECUS, RankingPeriod.ALL, LocalDate.now(zone)));
        assertEquals(0, value(RankingMetric.ECUS, RankingPeriod.DAY, LocalDate.now(zone)));
        assertEquals(
                5000, value(RankingMetric.DONORS, RankingPeriod.DAY, LocalDate.of(2024, 3, 31)));
        assertEquals(
                LocalDate.of(2024, 3, 31),
                store.board(RankingMetric.DONORS, RankingPeriod.ALL, LocalDate.now(zone))
                        .join()
                        .since());
        db.initialize();
        assertEquals(
                5000, value(RankingMetric.DONORS, RankingPeriod.DAY, LocalDate.of(2024, 3, 31)));
        assertEquals(3999, value(RankingMetric.DONORS, RankingPeriod.ALL, LocalDate.now(zone)));
    }
}
