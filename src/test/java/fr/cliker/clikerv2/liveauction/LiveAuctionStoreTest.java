package fr.cliker.clikerv2.liveauction;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.rewards.MailStore;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.concurrent.CompletionException;

class LiveAuctionStoreTest {
    private DatabaseManager db;
    private EconomyService economy;
    private LiveAuctionStore store;
    private UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "live-" + UUID.randomUUID() + ".db");
        db.initialize();
        economy = new EconomyService(db, Map.of());
        economy.set(a, "A", CurrencyType.ECUS, 100000).join();
        economy.set(b, "B", CurrencyType.ECUS, 100000).join();
        store = new LiveAuctionStore(db);
        assertNull(store.initialize().join());
    }

    @AfterEach
    void cleanup() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void sql(String query) {
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.executeUpdate(query);
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
    }

    private long count(String query) {
        return db.submit(
                        c -> {
                            try (var s = c.createStatement();
                                    var r = s.executeQuery(query)) {
                                r.next();
                                return r.getLong(1);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
    }

    private void create() {
        store.create("event", List.of("one", "two", "three"), 180000).join();
    }

    private void active() {
        create();
        assertTrue(store.join("event", a).join());
        assertTrue(store.join("event", b).join());
        assertEquals("ACTIVE", store.advance("event", 30000, true).join().state().phase());
    }

    private void expire() {
        sql("UPDATE live_auctions SET deadline=0 WHERE id='event'");
    }

    @Test
    void registrationClosesForNewPlayersAndOnlyOneEventCanExist() {
        create();
        assertThrows(
                CompletionException.class,
                () -> store.create("other", List.of("a", "b", "c"), 180000).join());
        assertEquals(3, count("SELECT COUNT(*) FROM live_auction_lots"));
        assertTrue(store.join("event", a).join());
        store.leaveRegistration("event", a).join();
        assertTrue(store.join("event", b).join());
        store.advance("event", 30000, true).join();
        assertFalse(store.join("event", a).join());
        assertTrue(store.join("event", b).join());
        assertEquals(
                LiveAuctionStore.Bid.NOT_REGISTERED,
                store.bid("event", 0, a, "A", 1000, 30000).join().result());
    }

    @Test
    void concurrentSamePriceProducesOneLeaderNoImmediateDebitAndResetsTimer() {
        active();
        sql(
                "UPDATE live_auctions SET deadline="
                        + (System.currentTimeMillis() + 1000)
                        + " WHERE id='event'");
        var first = store.bid("event", 0, a, "A", 1000, 30000);
        var second = store.bid("event", 0, b, "B", 1000, 30000);
        assertEquals(LiveAuctionStore.Bid.ACCEPTED, first.join().result());
        assertEquals(LiveAuctionStore.Bid.TOO_LOW, second.join().result());
        var s = store.state("event").join();
        assertEquals(a, s.bidder());
        assertTrue(s.deadline() >= System.currentTimeMillis() + 29000);
        assertEquals(100000, economy.getBalances(a).join().ecus());
        assertEquals(1, count("SELECT COUNT(*) FROM live_auction_bids"));
        assertEquals(
                LiveAuctionStore.Bid.FUNDS,
                store.bid("event", 0, b, "B", 100001, 30000).join().result());
        assertEquals(
                LiveAuctionStore.Bid.CLOSED,
                store.bid("event", 1, b, "B", 1200, 30000).join().result());
    }

    @Test
    void settlementAndDuplicateCallbackCannotDebitOrDeliverTwice() {
        active();
        store.bid("event", 0, a, "A", 1000, 30000).join();
        store.bid("event", 0, b, "B", 1200, 30000).join();
        expire();
        var first = store.advance("event", 30000, false);
        var duplicate = store.advance("event", 30000, false);
        assertEquals(b, first.join().sale().winner());
        assertNull(duplicate.join().sale());
        assertEquals(1, first.join().state().lot());
        assertEquals(100000, economy.getBalances(a).join().ecus());
        assertEquals(98800, economy.getBalances(b).join().ecus());
        assertEquals("one", new MailStore(db).page(b, 0).join().entries().getFirst().payload());
        assertEquals(1, count("SELECT COUNT(*) FROM claim_items"));
    }

    @Test
    void highestInsolventOfferIsSkippedAndHighestSolventOfferWins() {
        active();
        store.bid("event", 0, a, "A", 1000, 30000).join();
        store.bid("event", 0, b, "B", 1500, 30000).join();
        economy.set(b, "B", CurrencyType.ECUS, 0).join();
        expire();
        var sale = store.advance("event", 30000, false).join().sale();
        assertEquals(a, sale.winner());
        assertEquals(1000, sale.price());
        assertEquals(99000, economy.getBalances(a).join().ecus());
        assertEquals(0, economy.getBalances(b).join().ecus());
    }

    @Test
    void persistedStateSurvivesServiceRestartAndThreeLotsAreSequential() {
        active();
        store = new LiveAuctionStore(db);
        assertEquals("ACTIVE", store.initialize().join().phase());
        for (int lot = 0; lot < 3; lot++) {
            assertEquals(
                    LiveAuctionStore.Bid.ACCEPTED,
                    store.bid("event", lot, a, "Offline", 1000, 30000).join().result());
            expire();
            var result = store.advance("event", 30000, false).join();
            assertEquals(lot, result.sale().lot());
        }
        assertEquals("DONE", store.state("event").join().phase());
        assertEquals(97000, economy.getBalances(a).join().ecus());
        assertEquals(3, new MailStore(db).page(a, 0).join().total());
        assertNull(store.advance("event", 30000, false).join().sale());
        assertEquals(
                "REGISTERING", store.create("next", List.of("a", "b", "c"), 180000).join().phase());
    }

    @Test
    void emptyRegistrationAndUnsoldLotsNeverChargeAnyone() {
        create();
        assertEquals("DONE", store.advance("event", 30000, true).join().state().phase());
        assertEquals(3, count("SELECT COUNT(*) FROM live_auction_lots WHERE state='UNSOLD'"));
        assertEquals(100000, economy.getBalances(a).join().ecus());
        assertEquals(0, count("SELECT COUNT(*) FROM claim_items"));
    }

    @Test
    void corruptLotStateRollsBackPaymentAndDeliveryTogether() {
        active();
        store.bid("event", 0, a, "A", 1000, 30000).join();
        sql("UPDATE live_auction_lots SET state='SOLD' WHERE event='event' AND slot=0");
        expire();
        assertThrows(CompletionException.class, () -> store.advance("event", 30000, false).join());
        assertEquals(100000, economy.getBalances(a).join().ecus());
        assertEquals(0, count("SELECT COUNT(*) FROM claim_items"));
        assertEquals(0, store.state("event").join().lot());
    }

    @Test
    void anInsolventLatestOfferCannotFallBackToItsOwnersOwnOlderOffer() {
        active();
        store.bid("event", 0, a, "A", 1000, 30000).join();
        store.bid("event", 0, a, "A", 2000, 30000).join();
        economy.set(a, "A", CurrencyType.ECUS, 1500).join();
        expire();
        assertNull(store.advance("event", 30000, false).join().sale().winner());
        assertEquals(1500, economy.getBalances(a).join().ecus());
        assertEquals(0, count("SELECT COUNT(*) FROM claim_items"));
    }

    @Test
    void maximumPriceUsesExactLongArithmeticAndRejectsInvalidOffers() {
        active();
        long max = 1_000_000_000_000_000L;
        economy.set(a, "A", CurrencyType.ECUS, Long.MAX_VALUE).join();
        assertEquals(
                LiveAuctionStore.Bid.ACCEPTED,
                store.bid("event", 0, a, "A", max, 30000).join().result());
        assertThrows(
                IllegalArgumentException.class,
                () -> store.bid("event", 0, a, "A", max + 1, 30000));
        assertThrows(IllegalArgumentException.class, () -> store.bid("event", 0, a, "A", 0, 30000));
        expire();
        assertEquals(max, store.advance("event", 30000, false).join().sale().price());
        assertEquals(Long.MAX_VALUE - max, economy.getBalances(a).join().ecus());
    }

    @Test
    void snakeIsContiguousAndPricesIncreaseToExactMaximumWithoutOverflow() {
        var seen = new HashSet<Integer>();
        for (int i = 0; i < 45; i++) {
            int slot = BidPrices.slot(i);
            assertTrue(seen.add(slot));
            if (i > 0) {
                int before = BidPrices.slot(i - 1);
                assertEquals(1, Math.abs(slot / 9 - before / 9) + Math.abs(slot % 9 - before % 9));
            }
        }
        assertEquals(0, BidPrices.slot(0));
        assertEquals(45, seen.size());
        var prices = BidPrices.generate(1000, 1_000_000_000_000_000L, 20);
        assertEquals(List.of(1000L, 1200L, 1440L), prices.subList(0, 3));
        assertEquals(1_000_000_000_000_000L, prices.getLast());
        for (int i = 1; i < prices.size(); i++) assertTrue(prices.get(i) > prices.get(i - 1));
        assertThrows(IllegalArgumentException.class, () -> BidPrices.generate(0, 1000, 20));
        assertThrows(IllegalArgumentException.class, () -> BidPrices.generate(1000, 10000, 0));
    }
}
