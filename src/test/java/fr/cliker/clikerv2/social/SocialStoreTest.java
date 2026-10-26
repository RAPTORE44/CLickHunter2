package fr.cliker.clikerv2.social;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.hunting.RewardLedger;
import fr.cliker.clikerv2.rewards.*;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class SocialStoreTest {
    private DatabaseManager db;
    private RewardLedger ledger;
    private SocialStore store;
    private EconomyService economy;
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "social-" + UUID.randomUUID() + ".db");
        db.initialize();
        ledger = new RewardLedger(plugin, db);
        store = new SocialStore(db, ledger);
        store.initialize().join();
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
    void marriagesAreReciprocalExclusiveAndPersistent() {
        assertFalse(store.marry(a, a, 1).join());
        var first = store.marry(a, b, 1);
        var rival = store.marry(c, b, 1);
        assertTrue(first.join());
        assertFalse(rival.join());
        var one = store.bond(a).join();
        var two = store.bond(b).join();
        assertEquals(b, one.partner());
        assertEquals(a, two.partner());
        assertEquals(one.id(), two.id());
        assertNull(store.bond(c).join());
        var restarted = new SocialStore(db, ledger);
        restarted.initialize().join();
        assertEquals(one, restarted.bond(a).join());
        assertFalse(restarted.marry(b, c, 2).join());
    }

    @Test
    void divorceCannotDebitTwiceOrRemoveANewMarriage() {
        economy.set(a, "A", CurrencyType.ECUS, 100_000_010).join();
        store.marry(a, b, 1).join();
        String old = store.bond(a).join().id();
        var one = store.divorce(a, old, 100_000_000);
        var two = store.divorce(a, old, 100_000_000);
        assertEquals(2, one.join());
        assertEquals(0, two.join());
        assertEquals(10, economy.getBalances(a).join().ecus());
        assertNull(store.bond(b).join());
        assertTrue(store.marry(a, c, 2).join());
        assertEquals(0, store.divorce(a, old, 0).join());
        assertEquals(c, store.bond(a).join().partner());
    }

    @Test
    void insolventDivorceLeavesBothLinksAndBalanceIntact() {
        economy.set(a, "A", CurrencyType.ECUS, 100).join();
        store.marry(a, b, 1).join();
        assertEquals(1, store.divorce(a, store.bond(a).join().id(), 101).join());
        assertNotNull(store.bond(b).join());
        assertEquals(100, economy.getBalances(a).join().ecus());
        assertThrows(IllegalArgumentException.class, () -> store.divorce(a, "x", -1));
    }

    @Test
    void oneRecommendationPerHourGloballyAndOnlyRecipientGets500XP() {
        assertFalse(
                store.recommend(a, a, "A", 360000, 360000, 1000, 3600000, 500).join().accepted());
        assertFalse(
                store.recommend(a, b, "B", 359999, 360000, 1000, 3600000, 500).join().accepted());
        assertTrue(
                store.recommend(a, b, "B", 360000, 360000, 1000, 3600000, 500).join().accepted());
        var result = store.recommend(a, c, "C", 360000, 360000, 1001, 3600000, 500).join();
        assertFalse(result.accepted());
        assertEquals(3599999, result.waitMillis());
        assertEquals(1, store.points(b).join());
        assertEquals(0, store.points(a).join());
        assertEquals(0, store.points(c).join());
        assertEquals(500, earnedXP(b));
        assertEquals(0, earnedXP(a));
        assertFalse(
                store.recommend(a, b, "B", 360000, 360000, 900, 3600000, 500).join().accepted());
        var restarted = new SocialStore(db, ledger);
        restarted.initialize().join();
        assertFalse(
                restarted
                        .recommend(a, b, "B", 360000, 360000, 3600999, 3600000, 500)
                        .join()
                        .accepted());
        assertTrue(
                restarted
                        .recommend(a, c, "C", 360000, 360000, 3601000, 3600000, 500)
                        .join()
                        .accepted());
    }

    @Test
    void concurrentRecommendationsCannotBypassCooldownOrLosePoints() {
        var one = store.recommend(a, b, "B", 360000, 360000, 1000, 3600000, 500);
        var two = store.recommend(a, b, "B", 360000, 360000, 1000, 3600000, 500);
        var other = store.recommend(c, b, "B", 360000, 360000, 1000, 3600000, 500);
        assertTrue(one.join().accepted());
        assertFalse(two.join().accepted());
        assertTrue(other.join().accepted());
        assertEquals(2, store.points(b).join());
        assertEquals(1000, earnedXP(b));
    }

    @Test
    void xpOverflowRollsBackRecommendationAndCooldownTogether() {
        db.submit(
                        connection -> {
                            try (var s =
                                    connection.prepareStatement(
                                            "INSERT INTO earned_xp(owner,total) VALUES(?,?)")) {
                                s.setString(1, b.toString());
                                s.setLong(2, Long.MAX_VALUE);
                                s.executeUpdate();
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertThrows(
                Exception.class,
                () -> store.recommend(a, b, "B", 360000, 360000, 1000, 3600000, 500).join());
        assertEquals(0, store.points(b).join());
        assertTrue(
                store.recommend(a, c, "C", 360000, 360000, 1000, 3600000, 500).join().accepted());
    }

    @Test
    void giftsAreJournaledOnceAndDivorceRollsBackTheOutgoingReceipt() {
        var outgoing = new OutgoingStore(db);
        var mail = new MailStore(db);
        store.marry(a, b, 1).join();
        String bond = store.bond(a).join().id();
        outgoing.prepare("gift", a, "payload").join();
        store.gift(a, b, bond, "gift", "payload").join();
        assertEquals(1, mail.page(b, 0).join().total());
        assertThrows(Exception.class, () -> store.gift(a, b, bond, "gift", "payload").join());
        outgoing.recover(a, "gift").join();
        assertEquals(0, mail.page(a, 0).join().total());
        outgoing.prepare("late", a, "other").join();
        assertEquals(2, store.divorce(a, bond, 0).join());
        assertThrows(Exception.class, () -> store.gift(a, b, bond, "late", "other").join());
        outgoing.recover(a, "late").join();
        outgoing.recover(a, "late").join();
        assertEquals(1, mail.page(a, 0).join().total());
        assertEquals(1, mail.page(b, 0).join().total());
    }

    @Test
    void lastDeathSurvivesRestartAndUsesTheMostRecentLocation() {
        assertNull(store.death(a).join());
        store.death(a, new SocialStore.Death("world", 1, 65, 3, 4, 5)).join();
        var newest = new SocialStore.Death("world", 20, 70, 30, 40, 50);
        store.death(a, newest).join();
        var restarted = new SocialStore(db, ledger);
        restarted.initialize().join();
        assertEquals(newest, restarted.death(a).join());
    }

    private long earnedXP(UUID owner) {
        return db.submit(
                        connection -> {
                            try (var s =
                                    connection.prepareStatement(
                                            "SELECT total FROM earned_xp WHERE owner=?")) {
                                s.setString(1, owner.toString());
                                try (var r = s.executeQuery()) {
                                    return r.next() ? r.getLong(1) : 0L;
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
    }
}
