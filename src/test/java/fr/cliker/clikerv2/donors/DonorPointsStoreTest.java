package fr.cliker.clikerv2.donors;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.concurrent.CompletionException;

class DonorPointsStoreTest {
    private DatabaseManager db;
    private DonorPointsStore store;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private String path;

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        path = "donors-" + UUID.randomUUID() + ".db";
        db = new DatabaseManager(plugin, path);
        db.initialize();
        store = new DonorPointsStore(db);
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
    void decimalAmountsAreExactAndMalformedAmountsAreRejected() {
        assertEquals(999, DonorPointsStore.parse("9.99"));
        assertEquals(999, DonorPointsStore.parse("9,99"));
        assertEquals(1, DonorPointsStore.parse("0.01"));
        assertEquals("9.99", DonorPointsStore.format(999));
        for (String invalid :
                List.of(
                        "-1",
                        "0",
                        "0.00",
                        "NaN",
                        "Infinity",
                        "1e3",
                        "1.234",
                        "9 99",
                        "99999999999999999999999999999"))
            assertThrows(IllegalArgumentException.class, () -> DonorPointsStore.parse(invalid));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.change("invalid!", 100, null, "console"));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.change("Test", 100, "{unexpanded}", "console"));
    }

    @Test
    void repeatedShopReceiptsCannotCreditOrDebitTwice() {
        assertTrue(store.change("OfflineBuyer", 999, "purchase-1", "console").join().applied());
        assertFalse(store.change("offlinebuyer", 999, "purchase-1", "console").join().applied());
        assertEquals(999, store.find("OFFLINEBUYER").join().units());
        assertThrows(
                CompletionException.class,
                () -> store.change("OfflineBuyer", 100, "purchase-1", "console").join());
        assertThrows(
                CompletionException.class,
                () -> store.change("Other", 999, "purchase-1", "console").join());
        assertTrue(store.change("OfflineBuyer", -99, "refund-1", "console").join().applied());
        assertFalse(store.change("OfflineBuyer", -99, "refund-1", "console").join().applied());
        assertEquals(900, store.find("offlinebuyer").join().units());
    }

    @Test
    void insufficientRemovalsAndMaximumBalanceLeaveNoPartialChanges() {
        store.change("Test", 100, null, "console").join();
        assertThrows(
                CompletionException.class,
                () -> store.change("Test", -101, "refundable", "console").join());
        assertEquals(100, store.find("Test").join().units());
        store.change("Test", 1, null, "console").join();
        assertTrue(store.change("Test", -101, "refundable", "console").join().applied());
        assertEquals(0, store.find("Test").join().units());
        store.change("Test", DonorPointsStore.MAX_UNITS, null, "console").join();
        assertThrows(
                CompletionException.class, () -> store.change("Test", 1, null, "console").join());
        assertEquals(DonorPointsStore.MAX_UNITS, store.find("Test").join().units());
    }

    @Test
    void concurrentChangesAccumulateAndReorderThePodiumWithStableTies() {
        var operations =
                new ArrayList<java.util.concurrent.CompletableFuture<DonorPointsStore.Change>>();
        for (int i = 0; i < 30; i++)
            operations.add(store.change(i % 2 == 0 ? "Alice" : "alice", 100, null, "console"));
        operations.forEach(java.util.concurrent.CompletableFuture::join);
        assertEquals(3000, store.find("Alice").join().units());
        store.change("Bob", 3000, null, "console").join();
        store.change("Chris", 4000, null, "console").join();
        store.change("David", 2000, null, "console").join();
        assertEquals(
                List.of("Chris", "alice", "Bob"),
                store.top().join().stream().map(DonorPointsStore.Donor::name).toList());
        store.change("Chris", -4000, null, "console").join();
        assertEquals(
                List.of("alice", "Bob", "David"),
                store.top().join().stream().map(DonorPointsStore.Donor::name).toList());
    }

    @Test
    void failedHistoryInsertRollsBackBothAccountAndReceipt() {
        store.change("Test", 100, null, "console").join();
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.executeUpdate(
                                        "CREATE TRIGGER donor_failure BEFORE INSERT ON"
                                            + " donor_point_events BEGIN SELECT RAISE(ABORT,'test"
                                            + " failure'); END");
                                return null;
                            } catch (java.sql.SQLException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertThrows(
                CompletionException.class,
                () -> store.change("Test", 999, "retry", "console").join());
        assertEquals(100, store.find("Test").join().units());
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.executeUpdate("DROP TRIGGER donor_failure");
                                return null;
                            } catch (java.sql.SQLException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertTrue(store.change("Test", 999, "retry", "console").join().applied());
        assertEquals(1099, store.find("Test").join().units());
    }

    @Test
    void paginationAndRestartRetainAllPointsAndReceipts() throws Exception {
        for (int i = 0; i < 46; i++)
            store.change("Buyer" + i, 100 + i, "purchase" + i, "console").join();
        assertEquals(45, store.page(0).join().donors().size());
        assertTrue(store.page(0).join().hasNext());
        assertEquals(1, store.page(1).join().donors().size());
        assertFalse(store.page(1).join().hasNext());
        db.close();
        db = new DatabaseManager(plugin, path);
        db.initialize();
        store = new DonorPointsStore(db);
        assertEquals(145, store.find("Buyer45").join().units());
        assertFalse(store.change("Buyer45", 145, "purchase45", "console").join().applied());
    }
}
