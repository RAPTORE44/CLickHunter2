package fr.cliker.clikerv2.social;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.hunting.*;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class CommunityStoreTest {
    private DatabaseManager db;
    private RewardLedger ledger;
    private CommunityStore store;
    private EconomyService economy;
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "community-" + UUID.randomUUID() + ".db");
        db.initialize();
        ledger = new RewardLedger(plugin, db);
        store = new CommunityStore(db, ledger);
        store.initialize().join();
        economy = new EconomyService(db, Map.of());
    }

    @AfterEach
    void clean() {
        try {
            ledger.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void welcomeIsPaidOnceEvenForConcurrentCommandsAndSurvivesRestart() {
        store.newcomer(new CommunityStore.Welcome(b, "B", 1000)).join();
        var one = store.welcome(a, "A", b, 2000, 300000, Gains.of(250, 0, 0, 50));
        var two = store.welcome(a, "A", b, 2000, 300000, Gains.of(250, 0, 0, 50));
        assertEquals(2, one.join());
        assertEquals(1, two.join());
        assertEquals(250, economy.getBalances(a).join().ecus());
        var restarted = new CommunityStore(db, ledger);
        assertEquals(b, restarted.initialize().join().owner());
        assertEquals(1, restarted.welcome(a, "A", b, 3000, 300000, Gains.of(250, 0, 0, 50)).join());
    }

    @Test
    void selfExpiredAndSupersededWelcomesNeverPay() {
        store.newcomer(new CommunityStore.Welcome(b, "B", 1000)).join();
        assertEquals(0, store.welcome(b, "B", b, 2000, 300000, Gains.of(250, 0, 0, 50)).join());
        assertEquals(0, store.welcome(a, "A", b, 301001, 300000, Gains.of(250, 0, 0, 50)).join());
        store.newcomer(new CommunityStore.Welcome(c, "C", 2000)).join();
        assertEquals(0, store.welcome(a, "A", b, 3000, 300000, Gains.of(250, 0, 0, 50)).join());
        assertEquals(2, store.welcome(a, "A", c, 3000, 300000, Gains.of(250, 0, 0, 50)).join());
        assertEquals(250, economy.getBalances(a).join().ecus());
    }

    @Test
    void failedCreditRollsBackWelcomeReceiptSoItCanBeRetried() {
        economy.set(a, "A", CurrencyType.ECUS, Long.MAX_VALUE).join();
        store.newcomer(new CommunityStore.Welcome(b, "B", 1000)).join();
        assertThrows(
                Exception.class,
                () -> store.welcome(a, "A", b, 2000, 300000, Gains.of(250, 0, 0, 50)).join());
        economy.set(a, "A", CurrencyType.ECUS, 0).join();
        assertEquals(2, store.welcome(a, "A", b, 2000, 300000, Gains.of(250, 0, 0, 50)).join());
    }

    @Test
    void preferencesAndValidatedBugCountPersistWithoutOverwritingOtherFields() {
        store.bugs(a, 7).join();
        store.preference(a, "bugs_7", null).join();
        store.preference(a, null, "gold").join();
        var restarted = new CommunityStore(db, ledger);
        restarted.initialize().join();
        var p = restarted.profiles(List.of(a, b)).join();
        assertEquals(7, p.get(a).bugs());
        assertEquals("bugs_7", p.get(a).tag());
        assertEquals("gold", p.get(a).color());
        assertEquals(0, p.get(b).bugs());
        assertEquals("white", p.get(b).color());
        assertThrows(IllegalArgumentException.class, () -> store.bugs(a, -1));
    }
}
