package fr.cliker.clikerv2.rewards;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.auction.*;
import fr.cliker.clikerv2.economy.CurrencyType;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.concurrent.CompletionException;

class OutgoingStoreTest {
    private DatabaseManager db;
    private OutgoingStore store;
    private UUID owner;
    private EconomyService economy;

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "outgoing-" + UUID.randomUUID() + ".db");
        db.initialize();
        owner = UUID.randomUUID();
        store = new OutgoingStore(db);
        economy = new EconomyService(db, Map.of());
        economy.set(owner, "Seller", CurrencyType.ECUS, 10000).join();
    }

    @AfterEach
    void cleanup() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private long scalar(String query) {
        return db.submit(
                        c -> {
                            try (var s = c.createStatement();
                                    var rows = s.executeQuery(query)) {
                                assertTrue(rows.next());
                                return rows.getLong(1);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
    }

    private AuctionListing listing(String id, String payload) {
        return new AuctionListing(id, owner, "Seller", payload, 10000, System.currentTimeMillis());
    }

    @Test
    void crashBeforeSavedRemovalCancelsWithoutInventingRefund() {
        store.prepare("before", owner, "original").join();
        new OutgoingStore(db).recover(owner, null).join();
        assertEquals(3, scalar("SELECT state FROM outgoing_items"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM claim_items"));
        assertEquals(10000, economy.getBalances(owner).join().ecus());
    }

    @Test
    void savedRemovalWithoutPublicationRefundsOnceAcrossRepeatedRecovery() {
        store.prepare("removed", owner, "original").join();
        new OutgoingStore(db).recover(owner, "removed").join();
        new OutgoingStore(db).recover(owner, "removed").join();
        assertEquals(2, scalar("SELECT state FROM outgoing_items"));
        var mail = new MailStore(db).page(owner, 0).join();
        assertEquals(1, mail.total());
        assertEquals("original", mail.entries().getFirst().payload());
    }

    @Test
    void committedPublicationNeverRefundsEvenAfterWithdrawalAndRestart() {
        store.prepare("sold", owner, "original").join();
        var auction = new AuctionStore(db);
        auction.publish(listing("listing", "original"), true, "sold").join();
        assertEquals(9400, economy.getBalances(owner).join().ecus());
        assertEquals(1, scalar("SELECT state FROM outgoing_items"));
        store.recover(owner, "sold").join();
        assertEquals(0, scalar("SELECT COUNT(*) FROM claim_items"));
        assertTrue(auction.returnToOwner("listing").join());
        store.recover(owner, "sold").join();
        assertEquals(1, scalar("SELECT COUNT(*) FROM claim_items"));
        assertEquals(9400, economy.getBalances(owner).join().ecus());
    }

    @Test
    void failureRollsBackEscrowFeeAndListingThenAllowsExactlyOneRefund() {
        store.prepare("poor", owner, "original").join();
        economy.set(owner, "Seller", CurrencyType.ECUS, 0).join();
        assertThrows(
                CompletionException.class,
                () ->
                        new AuctionStore(db)
                                .publish(listing("failed", "original"), true, "poor")
                                .join());
        assertEquals(0, scalar("SELECT state FROM outgoing_items"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM auctions"));
        store.recover(owner, "poor").join();
        store.recover(owner, "poor").join();
        assertEquals(1, scalar("SELECT COUNT(*) FROM claim_items"));
    }

    @Test
    void wrongOwnerPayloadOrDuplicateCommitCannotPublishOrChargeAgain() {
        store.prepare("unique", owner, "original").join();
        var auction = new AuctionStore(db);
        assertThrows(
                CompletionException.class,
                () -> auction.publish(listing("wrong", "modified"), true, "unique").join());
        var other = new AuctionListing("other", UUID.randomUUID(), "Other", "original", 10000, 1);
        assertThrows(
                CompletionException.class, () -> auction.publish(other, true, "unique").join());
        var a = auction.publish(listing("first", "original"), true, "unique");
        var b = auction.publish(listing("second", "original"), true, "unique");
        a.join();
        assertThrows(CompletionException.class, b::join);
        assertEquals(1, scalar("SELECT COUNT(*) FROM auctions"));
        assertEquals(9400, economy.getBalances(owner).join().ecus());
    }

    @Test
    void unknownReceiptRetainsPendingDataAndFailsClosed() {
        store.prepare("known", owner, "original").join();
        assertThrows(CompletionException.class, () -> store.recover(owner, "missing").join());
        assertEquals(0, scalar("SELECT state FROM outgoing_items"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM claim_items"));
    }

    @Test
    void atMostOnePendingTransferPerPlayerButFinalizedHistoryDoesNotBlockNextSale() {
        store.prepare("first", owner, "a").join();
        assertThrows(CompletionException.class, () -> store.prepare("second", owner, "b").join());
        store.recover(owner, null).join();
        store.prepare("second", owner, "b").join();
        assertEquals(2, scalar("SELECT COUNT(*) FROM outgoing_items"));
    }
}
