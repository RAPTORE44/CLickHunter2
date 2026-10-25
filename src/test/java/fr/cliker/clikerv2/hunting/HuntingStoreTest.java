package fr.cliker.clikerv2.hunting;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.rewards.MailStore;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;
import java.util.concurrent.*;

class HuntingStoreTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private HuntingStore hunts;
    private RewardLedger ledger;
    private EconomyService economy;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "hunt-" + UUID.randomUUID() + ".db");
        db.initialize();
        ledger = new RewardLedger(plugin, db);
        hunts = new HuntingStore(ledger);
        economy = new EconomyService(db, Map.of());
    }

    @AfterEach
    void cleanup() {
        try {
            ledger.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private boolean kill(String event, boolean boss, Gains gains) {
        return hunts.kill(event, owner, "Chasseur", "vache", boss, gains, List.of()).join();
    }

    @Test
    void killIsIdempotentAndTracksActualNormalAndBossGains() {
        var normal = Gains.of(50, 2, 4, 10);
        assertTrue(kill("victim", false, normal));
        assertFalse(kill("victim", false, normal));
        assertTrue(kill("boss", true, Gains.of(1000, 50, 10, 100)));
        var stats = hunts.stats(owner).join();
        assertEquals(1, stats.get("vache").kills());
        assertEquals(1, stats.get("vache").bosses());
        assertEquals(1050, stats.get("vache").gains().amount(CurrencyType.ECUS));
        assertEquals(110, stats.get("vache").gains().xp());
        assertEquals(1050, economy.getBalances(owner).join().ecus());
        assertFalse(stats.containsKey("cochon"));
        assertEquals(2, new fr.cliker.clikerv2.mobs.MobKillStore(db).getTotal(owner).join());
    }

    @Test
    void simultaneousDuplicateDeathsCreditOnlyOnce() {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++)
            futures.add(
                    hunts.kill(
                            "same",
                            owner,
                            "Chasseur",
                            "vache",
                            false,
                            Gains.of(10, 1, 0, 2),
                            List.of()));
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        assertEquals(
                1,
                futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Boolean::booleanValue)
                        .count());
        assertEquals(10, economy.getBalances(owner).join().ecus());
    }

    @Test
    void failedCreditRollsBackStatsReceiptAndItems() {
        economy.change(owner, "Chasseur", CurrencyType.ECUS, Long.MAX_VALUE).join();
        assertThrows(
                CompletionException.class,
                () ->
                        hunts.kill(
                                        "overflow",
                                        owner,
                                        "Chasseur",
                                        "vache",
                                        false,
                                        Gains.of(1, 0, 0, 2),
                                        List.of("item"))
                                .join());
        assertTrue(hunts.stats(owner).join().isEmpty());
        assertEquals(0, new MailStore(db).page(owner, 0).join().total());
        economy.change(owner, "Chasseur", CurrencyType.ECUS, -Long.MAX_VALUE).join();
        assertTrue(kill("overflow", false, Gains.of(1, 0, 0, 2)));
    }

    @Test
    void unknownOwnerCanReceivePersistentOfflineXpAndMoney() {
        UUID offline = UUID.randomUUID();
        assertTrue(ledger.grant("raid1", offline, "Absent", Gains.of(100, 25, 5, 30)).join());
        assertFalse(ledger.grant("raid1", offline, "Absent", Gains.of(100, 25, 5, 30)).join());
        assertEquals(100, economy.getBalances(offline).join().ecus());
        assertEquals(
                30,
                db.submit(
                                c -> {
                                    try (var s = c.createStatement();
                                            var r = s.executeQuery("SELECT total FROM earned_xp")) {
                                        return r.next() ? r.getLong(1) : 0;
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .join());
    }

    @Test
    void xpDeliveryBatchesAndRespectsPersistentReceiptAcrossRestart() throws Exception {
        var player = server.addPlayer();
        UUID id = player.getUniqueId();
        ledger.grant("xp1", id, player.getName(), Gains.of(0, 0, 0, 15)).join();
        ledger.flushXP(id);
        ledger.flushXP(id);
        assertEquals(0, player.getTotalExperience());
        server.getScheduler().performTicks(20);
        db.submit(c -> null).join();
        server.getScheduler().performOneTick();
        assertEquals(15, player.getTotalExperience());
        ledger.shutdown();
        ledger = new RewardLedger(plugin, db);
        ledger.flushXP(id);
        server.getScheduler().performTicks(20);
        db.submit(c -> null).join();
        server.getScheduler().performOneTick();
        assertEquals(15, player.getTotalExperience());
    }

    @Test
    void xpWaitsForPrestigeResetBeforeApplyingNewGains() {
        var player = server.addPlayer();
        UUID id = player.getUniqueId();
        ledger.grant("xp-before", id, player.getName(), Gains.of(0, 0, 0, 15)).join();
        ledger.flushXP(id);
        server.getScheduler().performTicks(20);
        db.submit(c -> null).join();
        server.getScheduler().performOneTick();
        assertEquals(15, player.getTotalExperience());
        db.submit(
                        c -> {
                            try (var statement =
                                    c.prepareStatement(
                                            "INSERT INTO prestige_resets(owner,level,earned_xp)"
                                                    + " VALUES(?,1,15)")) {
                                statement.setString(1, id.toString());
                                statement.executeUpdate();
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        ledger.grant("xp-after", id, player.getName(), Gains.of(0, 0, 0, 5)).join();
        ledger.flushXP(id);
        server.getScheduler().performTicks(20);
        db.submit(c -> null).join();
        server.getScheduler().performOneTick();
        assertEquals(15, player.getTotalExperience());
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
        new fr.cliker.clikerv2.prestige.PrestigeStore(db).acknowledgeReset(id, 1).join();
        server.getScheduler().performTicks(20);
        db.submit(c -> null).join();
        server.getScheduler().performOneTick();
        assertEquals(5, player.getTotalExperience());
    }

    @Test
    void xpArrivingBetweenDatabaseReadAndDeliveryDoesNotWaitForAnotherKill() {
        var player = server.addPlayer();
        UUID id = player.getUniqueId();
        ledger.grant("first", id, player.getName(), Gains.of(0, 0, 0, 15)).join();
        ledger.flushXP(id);
        server.getScheduler().performTicks(20);
        db.submit(c -> null).join();
        ledger.grant("late", id, player.getName(), Gains.of(0, 0, 0, 5)).join();
        ledger.flushXP(id);
        server.getScheduler().performOneTick();
        assertEquals(15, player.getTotalExperience());
        server.getScheduler().performTicks(20);
        db.submit(c -> null).join();
        server.getScheduler().performOneTick();
        assertEquals(20, player.getTotalExperience());
    }
}
