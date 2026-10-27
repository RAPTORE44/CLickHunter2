package fr.cliker.clikerv2.hunting;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class AtmStoreTest {
    private DatabaseManager db;
    private RewardLedger ledger;

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var p = MockBukkit.createMockPlugin();
        db = new DatabaseManager(p, "atm-" + UUID.randomUUID() + ".db");
        db.initialize();
        ledger = new RewardLedger(p, db);
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

    @Test
    void concurrentClaimsRetainRemainderAndDoNotRepayOldTime() {
        var store = new AtmStore(ledger);
        var owner = UUID.randomUUID();
        var first = store.collect(owner, "Test", 2500, 1200, 100);
        var second = store.collect(owner, "Test", 2500, 1200, 100);
        assertEquals(200, first.join() + second.join());
        assertEquals(0L, store.collect(owner, "Test", 3500, 1200, 100).join());
        assertEquals(100L, store.collect(owner, "Test", 3600, 1200, 100).join());
        assertEquals(0L, store.collect(owner, "Test", 0, 1200, 100).join());
        assertEquals(300, new EconomyService(db, Map.of()).getBalances(owner).join().ecus());
    }
}
