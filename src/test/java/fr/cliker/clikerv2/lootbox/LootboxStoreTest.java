package fr.cliker.clikerv2.lootbox;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.hunting.*;
import fr.cliker.clikerv2.rewards.*;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class LootboxStoreTest {
    private DatabaseManager db;
    private RewardLedger ledger;
    private LootboxStore boxes;
    private OutgoingStore outgoing;
    private MailStore mail;
    private EconomyService economy;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "lootbox-journal-" + UUID.randomUUID() + ".db");
        db.initialize();
        ledger = new RewardLedger(plugin, db);
        boxes = new LootboxStore(ledger);
        outgoing = new OutgoingStore(db);
        mail = new MailStore(db);
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

    @Test
    void committedOpeningSurvivesRecoveryWithoutReturningTheBoxOrPayingTwice() {
        outgoing.prepare("saved-removal", owner, "box-payload").join();
        boxes.consume(owner, "A", "saved-removal", "box-payload", Gains.of(100, 10, 3, 25)).join();
        assertThrows(
                Exception.class,
                () ->
                        boxes.consume(
                                        owner,
                                        "A",
                                        "saved-removal",
                                        "box-payload",
                                        Gains.of(100, 10, 3, 25))
                                .join());
        new OutgoingStore(db).recover(owner, "saved-removal").join();
        assertEquals(100, economy.getBalances(owner).join().ecus());
        assertEquals(10, economy.getBalances(owner).join().gemmesSombres());
        assertEquals(3, economy.getBalances(owner).join().cendres());
        assertEquals(0, mail.page(owner, 0).join().total());
    }

    @Test
    void refusalRollsBackReceiptAndEveryGainThenRefundsTheOriginalExactlyOnce() {
        economy.set(owner, "A", CurrencyType.CENDRES, Long.MAX_VALUE).join();
        outgoing.prepare("refused", owner, "box-payload").join();
        assertThrows(
                Exception.class,
                () ->
                        boxes.consume(
                                        owner,
                                        "A",
                                        "refused",
                                        "box-payload",
                                        Gains.of(100, 10, 3, 25))
                                .join());
        assertEquals(0, economy.getBalances(owner).join().ecus());
        assertEquals(0, economy.getBalances(owner).join().gemmesSombres());
        new OutgoingStore(db).recover(owner, "refused").join();
        new OutgoingStore(db).recover(owner, "refused").join();
        assertEquals(1, mail.page(owner, 0).join().total());
        assertEquals("box-payload", mail.page(owner, 0).join().entries().getFirst().payload());
    }

    @Test
    void interruptionBeforeOrAfterSavedRemovalCannotCreateAGain() {
        outgoing.prepare("not-removed", owner, "box").join();
        outgoing.recover(owner, null).join();
        assertThrows(
                Exception.class,
                () ->
                        boxes.consume(owner, "A", "not-removed", "box", Gains.of(100, 0, 0, 0))
                                .join());
        assertEquals(0, mail.page(owner, 0).join().total());
        outgoing.prepare("removed-no-commit", owner, "box").join();
        outgoing.recover(owner, "removed-no-commit").join();
        assertEquals(1, mail.page(owner, 0).join().total());
        assertEquals(0, economy.getBalances(owner).join().ecus());
    }

    @Test
    void aReceiptForAnotherOwnerOrPayloadCannotCreditTheOpening() {
        outgoing.prepare("receipt", owner, "correct").join();
        assertThrows(
                Exception.class,
                () ->
                        boxes.consume(
                                        UUID.randomUUID(),
                                        "B",
                                        "receipt",
                                        "correct",
                                        Gains.of(100, 0, 0, 0))
                                .join());
        assertThrows(
                Exception.class,
                () ->
                        boxes.consume(owner, "A", "receipt", "changed", Gains.of(100, 0, 0, 0))
                                .join());
        boxes.consume(owner, "A", "receipt", "correct", Gains.of(100, 0, 0, 0)).join();
        assertEquals(100, economy.getBalances(owner).join().ecus());
    }
}
