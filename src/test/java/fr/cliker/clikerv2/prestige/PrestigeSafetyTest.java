package fr.cliker.clikerv2.prestige;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.mobs.MobKillStore;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class PrestigeSafetyTest {
    private DatabaseManager db;
    private PrestigeStore store;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var p = MockBukkit.createMockPlugin();
        db = new DatabaseManager(p, "prestige-" + UUID.randomUUID() + ".db");
        db.initialize();
        store = new PrestigeStore(db);
    }

    @AfterEach
    void cleanup() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void concurrentRequestsCannotGrantSamePrestigeTwiceAndResetPersists() {
        new MobKillStore(db).add(owner, 10000).join();
        var a = store.advance(owner, 0, 100);
        var b = store.advance(owner, 0, 100);
        assertTrue(a.join());
        assertFalse(b.join());
        assertEquals(1, store.getLevel(owner).join());
        var reset = new PrestigeStore(db).pendingReset(owner).join();
        assertEquals(1, reset.level());
        store.acknowledgeReset(owner, 2).join();
        assertNotNull(store.pendingReset(owner).join());
        store.acknowledgeReset(owner, 1).join();
        assertNull(store.pendingReset(owner).join());
    }

    @Test
    void missingKillsAndMaximumPrestigeNeverAdvance() {
        assertFalse(store.advance(owner, 0, 100).join());
        assertNull(store.pendingReset(owner).join());
        store.setLevel(owner, 100).join();
        new MobKillStore(db).add(owner, Long.MAX_VALUE).join();
        assertFalse(store.advance(owner, 100, 1).join());
        assertEquals(100, store.getLevel(owner).join());
    }
}
