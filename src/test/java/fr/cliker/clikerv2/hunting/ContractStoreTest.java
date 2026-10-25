package fr.cliker.clikerv2.hunting;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.concurrent.*;

class ContractStoreTest {
    private DatabaseManager db;
    private RewardLedger ledger;
    private HuntingStore hunts;
    private ContractStore store;
    private EconomyService economy;
    private final UUID owner = UUID.randomUUID();
    private final ContractStore.Contract normal =
            new ContractStore.Contract("vache-mobs", "vache", false, 2, Gains.of(100, 10, 5, 50));
    private final ContractStore.Contract boss =
            new ContractStore.Contract("vache-boss", "vache", true, 1, Gains.of(200, 20, 10, 100));

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var p = MockBukkit.createMockPlugin();
        db = new DatabaseManager(p, "contracts-" + UUID.randomUUID() + ".db");
        db.initialize();
        ledger = new RewardLedger(p, db);
        hunts = new HuntingStore(ledger);
        store = new ContractStore(ledger);
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

    private void kill(boolean boss) {
        hunts.kill(
                        UUID.randomUUID().toString(),
                        owner,
                        "Joueur",
                        "vache",
                        boss,
                        Gains.of(0, 0, 0, 0),
                        List.of())
                .join();
    }

    private String interact(ContractStore.Contract def, String day) {
        return store.interact(owner, "Joueur", def, day).join();
    }

    @Test
    void discoveryRequiresNormalMobAndEarlierKillsDoNotCompleteContract() {
        assertEquals("inconnu", interact(normal, "jour1"));
        kill(true);
        assertEquals("inconnu", interact(boss, "jour1"));
        kill(false);
        kill(false);
        assertEquals("accepte", interact(normal, "jour1"));
        assertEquals("incomplet", interact(normal, "jour1"));
        kill(false);
        assertEquals(1, store.progress(owner, normal, "jour1").join().done());
        kill(false);
        assertEquals("recompense", interact(normal, "jour1"));
        assertEquals("deja", interact(normal, "jour1"));
        assertEquals(100, economy.getBalances(owner).join().ecus());
    }

    @Test
    void bossAndNormalGoalsRemainIndependentAndNewCalendarDayStartsFresh() {
        kill(false);
        assertEquals("accepte", interact(boss, "2026-10-24"));
        kill(false);
        assertEquals("incomplet", interact(boss, "2026-10-24"));
        kill(true);
        assertEquals("recompense", interact(boss, "2026-10-24"));
        assertFalse(store.progress(owner, boss, "2026-10-25").join().accepted());
        assertEquals("accepte", interact(boss, "2026-10-25"));
        assertEquals(0, store.progress(owner, boss, "2026-10-25").join().done());
    }

    @Test
    void rapidClaimsPayOnlyOnceAndBatchProgressAgrees() {
        kill(false);
        interact(boss, "today");
        kill(true);
        List<CompletableFuture<String>> calls = new ArrayList<>();
        for (int i = 0; i < 30; i++) calls.add(store.interact(owner, "Joueur", boss, "today"));
        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();
        assertEquals(
                1,
                calls.stream().map(CompletableFuture::join).filter("recompense"::equals).count());
        assertEquals(200, economy.getBalances(owner).join().ecus());
        assertEquals(
                store.progress(owner, boss, "today").join(),
                store.progress(owner, List.of(normal, boss), "today").join().get(boss.id()));
    }

    @Test
    void failedRewardDoesNotConsumeContract() {
        kill(false);
        interact(boss, "today");
        kill(true);
        economy.set(owner, "Joueur", fr.cliker.clikerv2.economy.CurrencyType.ECUS, Long.MAX_VALUE)
                .join();
        assertThrows(CompletionException.class, () -> interact(boss, "today"));
        assertFalse(store.progress(owner, boss, "today").join().claimed());
        economy.set(owner, "Joueur", fr.cliker.clikerv2.economy.CurrencyType.ECUS, 0).join();
        assertEquals("recompense", interact(boss, "today"));
    }
}
