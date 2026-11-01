package fr.cliker.clikerv2.community;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.rewards.*;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class CommunityStoreTest {
    private DatabaseManager db;
    private CommunityStore store;
    private final UUID sender = UUID.randomUUID(), owner = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        db =
                new DatabaseManager(
                        MockBukkit.createMockPlugin(), "community-" + UUID.randomUUID() + ".db");
        db.initialize();
        store = new CommunityStore(db);
    }

    @AfterEach
    void close() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void letterAndAttachmentArePrivateAndCanOnlyBeTakenOnce() {
        store.send("one", owner, sender, "Sender", "Bonjour", "serialized-item", null, 100).join();
        assertEquals(1, store.unread(owner).join());
        assertEquals(0, store.unread(sender).join());
        assertFalse(store.take(sender, "one").join());
        assertFalse(store.delete(owner, "one").join());
        assertTrue(store.take(owner, "one").join());
        assertFalse(store.take(owner, "one").join());
        assertEquals(1, new MailStore(db).page(owner, 0).join().total());
        assertEquals(0, store.unread(owner).join());
        assertTrue(store.delete(owner, "one").join());
        assertEquals(1, new MailStore(db).page(owner, 0).join().total());
    }

    @Test
    void blockedOrFullMailboxDoesNotConsumeTheOutgoingReceipt() {
        var outgoing = new OutgoingStore(db);
        outgoing.prepare("receipt", sender, "item").join();
        store.block(owner, sender, true).join();
        assertThrows(
                Exception.class,
                () ->
                        store.send("one", owner, sender, "Sender", "Letter", "item", "receipt", 1)
                                .join());
        store.block(owner, sender, false).join();
        store.send("one", owner, sender, "Sender", "Letter", "item", "receipt", 1).join();
        assertThrows(
                Exception.class,
                () -> store.send("two", owner, sender, "Sender", "Other", null, null, 1).join());
        assertEquals(1, store.inbox(owner, 0).join().size());
    }

    @Test
    void itemPublicationAndReceiptRollbackTogetherOnDuplicateMail() {
        store.send("one", owner, sender, "Sender", "Letter", null, null, 100).join();
        var outgoing = new OutgoingStore(db);
        outgoing.prepare("receipt", sender, "item").join();
        assertThrows(
                Exception.class,
                () ->
                        store.send("one", owner, sender, "Sender", "Letter", "item", "receipt", 100)
                                .join());
        store.send("two", owner, sender, "Sender", "Letter", "item", "receipt", 100).join();
        assertEquals(2, store.inbox(owner, 0).join().size());
    }

    @Test
    void afkPointsUseOnlyCreditedTimeAndReceiptsPreventDoubleCredit() {
        assertEquals(0L, store.accrue("first", owner, 599, 600).join());
        assertEquals(0L, store.points(owner).join());
        assertEquals(1L, store.accrue("second", owner, 1, 600).join());
        assertEquals(0L, store.accrue("second", owner, 1, 600).join());
        assertEquals(1L, store.points(owner).join());
        assertEquals(6L, store.accrue("third", owner, 3600, 600).join());
        assertEquals(7L, store.points(owner).join());
    }

    @Test
    void afkSessionWatermarkIgnoresRetriesAndOldOutOfOrderUpdates() {
        assertEquals(0L, store.accrueSession("session", owner, 599, 600).join());
        assertEquals(1L, store.accrueSession("session", owner, 600, 600).join());
        assertEquals(0L, store.accrueSession("session", owner, 599, 600).join());
        assertEquals(0L, store.accrueSession("session", owner, 600, 600).join());
        assertEquals(1L, store.points(owner).join());
        assertEquals(1L, store.accrueSession("session", owner, 1200, 600).join());
        assertEquals(2L, store.points(owner).join());
        assertThrows(
                Exception.class, () -> store.accrueSession("session", sender, 1800, 600).join());
    }

    @Test
    void shopCannotOverdrawOrDebitTheSamePurchaseTwice() {
        assertFalse(store.buy("purchase", owner, 1, "item").join());
        store.accrue("time", owner, 1200, 600).join();
        assertTrue(store.buy("purchase", owner, 1, "item").join());
        assertFalse(store.buy("purchase", owner, 1, "item").join());
        assertEquals(1L, store.points(owner).join());
        assertEquals(1, new MailStore(db).page(owner, 0).join().total());
    }

    @Test
    void failedClaimInsertDoesNotLosePoints() {
        store.accrue("time", owner, 600, 600).join();
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.execute(
                                        "CREATE TRIGGER refuse_afk BEFORE INSERT ON claim_items"
                                                + " BEGIN SELECT RAISE(ABORT,'test'); END");
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertThrows(Exception.class, () -> store.buy("purchase", owner, 1, "item").join());
        assertEquals(1L, store.points(owner).join());
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.execute("DROP TRIGGER refuse_afk");
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertTrue(store.buy("purchase", owner, 1, "item").join());
        assertEquals(0L, store.points(owner).join());
    }
}
