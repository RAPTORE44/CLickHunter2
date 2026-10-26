package fr.cliker.clikerv2.pinata;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.hunting.*;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class PinataStoreTest {
    private DatabaseManager db;
    private RewardLedger ledger;
    private PinataStore store;
    private UUID first = UUID.randomUUID(), second = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var p = MockBukkit.createMockPlugin();
        db = new DatabaseManager(p, "pinata-" + UUID.randomUUID() + ".db");
        db.initialize();
        ledger = new RewardLedger(p, db);
        store = new PinataStore(db, ledger);
        store.initialize().join();
    }

    @AfterEach
    void close() {
        try {
            ledger.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void oneTypePerRoundForEveryPlayerAndDuplicateHitCannotPayTwice() {
        store.begin("round", "ecus").join();
        var a = store.hit("round", "same", first, "A", Gains.of(30, 0, 0, 0));
        var b = store.hit("round", "same", first, "A", Gains.of(30, 0, 0, 0));
        assertTrue(a.join());
        assertFalse(b.join());
        assertFalse(store.hit("round", "mixed", first, "A", Gains.of(1, 1, 0, 0)).join());
        assertFalse(store.hit("round", "xp", second, "B", Gains.of(0, 0, 0, 50)).join());
        assertTrue(store.hit("round", "other", second, "B", Gains.of(60, 0, 0, 0)).join());
        assertTrue(store.pending(first).join().isEmpty());
        store.finish("round").join();
        var one = store.pending(first).join().getFirst();
        var two = store.pending(second).join().getFirst();
        assertEquals(1, one.hits());
        assertEquals(30, one.coins());
        assertEquals(60, two.coins());
        assertEquals(0, one.gems());
        assertEquals(0, one.xp());
        assertFalse(store.hit("round", "late", first, "A", Gains.of(10, 0, 0, 0)).join());
    }

    @Test
    void allThreeTypesRemainSeparateAndTotalsMatchPersistentBalances() {
        var economy = new EconomyService(db, Map.of());
        for (String type : List.of("ecus", "xp", "gemmes")) store.begin(type, type).join();
        assertTrue(store.hit("ecus", "1", first, "A", Gains.of(50, 0, 0, 0)).join());
        assertTrue(store.hit("xp", "1", first, "A", Gains.of(0, 0, 0, 25)).join());
        assertTrue(store.hit("gemmes", "1", first, "A", Gains.of(0, 20, 0, 0)).join());
        for (String type : List.of("ecus", "xp", "gemmes")) store.finish(type).join();
        assertEquals(3, store.pending(first).join().size());
        assertEquals(50, economy.getBalances(first).join().ecus());
        assertEquals(20, economy.getBalances(first).join().gemmesSombres());
        assertEquals(0, economy.getBalances(first).join().cendres());
    }

    @Test
    void restartClosesInterruptedRoundAndOfflineSummarySurvivesUntilRead() {
        store.begin("interrupted", "xp").join();
        store.hit("interrupted", "1", first, "Offline", Gains.of(0, 0, 0, 25)).join();
        var recovered = new PinataStore(db, ledger);
        recovered.initialize().join();
        assertEquals(25, recovered.pending(first).join().getFirst().xp());
        assertEquals(1, recovered.pending(first).join().size());
        recovered.reported("interrupted", first).join();
        assertTrue(recovered.pending(first).join().isEmpty());
        assertFalse(
                recovered.hit("interrupted", "2", first, "Offline", Gains.of(0, 0, 0, 25)).join());
    }

    @Test
    void failedCreditRollsBackHitAndSummary() {
        new EconomyService(db, Map.of())
                .set(first, "Full", fr.cliker.clikerv2.economy.CurrencyType.ECUS, Long.MAX_VALUE)
                .join();
        store.begin("overflow", "ecus").join();
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> store.hit("overflow", "retry", first, "Full", Gains.of(1, 0, 0, 0)).join());
        store.finish("overflow").join();
        assertTrue(store.pending(first).join().isEmpty());
    }
}
