package fr.cliker.clikerv2.outpost;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.concurrent.*;

class OutpostStoreTest {
    private DatabaseManager db;
    private OutpostStore store;
    private EconomyService economy;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "plots-" + UUID.randomUUID() + ".db");
        db.initialize();
        store = new OutpostStore(db);
        economy = new EconomyService(db, Map.of());
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
    void onePlotPerOwnerEvenWithConcurrentCreationAndNameChange() {
        List<CompletableFuture<OutpostStore.Plot>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) calls.add(store.create(owner, "Joueur"));
        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();
        assertEquals(
                1,
                calls.stream()
                        .map(CompletableFuture::join)
                        .map(OutpostStore.Plot::id)
                        .distinct()
                        .count());
        assertEquals("Nouveau", store.create(owner, "Nouveau").join().name());
        assertEquals(1, store.load().join().plots().size());
    }

    @Test
    void expansionCostsAreAtomicAndBounded() {
        store.create(owner, "Joueur").join();
        long[] prices = {0, 100, 250};
        assertEquals("insuffisant", store.expand(owner, prices).join().status());
        economy.set(owner, "Joueur", CurrencyType.ECUS, 500).join();
        assertEquals(1, store.expand(owner, prices).join().plot().tier());
        assertEquals(400, economy.getBalances(owner).join().ecus());
        assertEquals(2, store.expand(owner, prices).join().plot().tier());
        assertEquals("maximum", store.expand(owner, prices).join().status());
        assertEquals(150, economy.getBalances(owner).join().ecus());
    }

    @Test
    void permissionListsAreIsolatedAndSurviveReload() {
        UUID guest = UUID.randomUUID(), other = UUID.randomUUID();
        store.create(owner, "Joueur").join();
        store.create(other, "Voisin").join();
        store.trust(owner, guest, true).join();
        var loaded = new OutpostStore(db).load().join();
        assertTrue(loaded.trusted().get(owner).contains(guest));
        assertFalse(loaded.trusted().containsKey(other));
        store.trust(owner, guest, false).join();
        assertFalse(store.load().join().trusted().containsKey(owner));
    }

    @Test
    void boundsDoNotOverlapAtMaximumSize() {
        List<OutpostStore.Plot> plots = new ArrayList<>();
        for (int id = 1; id <= 205; id++)
            plots.add(new OutpostStore.Plot(id, UUID.randomUUID(), "test", 0));
        for (var a : plots) {
            assertTrue(a.contains(a.x() - 96, a.z() - 96, 96));
            assertFalse(a.contains(a.x() + 97, a.z(), 96));
            for (var b : plots)
                if (a.id() != b.id()) {
                    assertFalse(a.contains(b.x() - 96, b.z() - 96, 96));
                    assertTrue(Math.abs(a.x() - b.x()) >= 256 || Math.abs(a.z() - b.z()) >= 256);
                }
        }
    }

    @Test
    void failedExpansionRollsBackDebit() {
        store.create(owner, "Joueur").join();
        economy.set(owner, "Joueur", CurrencyType.ECUS, 500).join();
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.execute(
                                        "CREATE TRIGGER reject_expansion BEFORE UPDATE ON outposts"
                                                + " BEGIN SELECT RAISE(ABORT,'failure'); END");
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertThrows(
                CompletionException.class, () -> store.expand(owner, new long[] {0, 100}).join());
        assertEquals(500, economy.getBalances(owner).join().ecus());
        assertEquals(0, store.load().join().plots().getFirst().tier());
    }
}
