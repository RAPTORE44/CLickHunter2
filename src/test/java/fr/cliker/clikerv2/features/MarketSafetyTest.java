package fr.cliker.clikerv2.features;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.auction.*;
import fr.cliker.clikerv2.economy.*;
import fr.cliker.clikerv2.hunting.*;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class MarketSafetyTest {
    DatabaseManager db;
    EconomyService economy;
    org.bukkit.plugin.java.JavaPlugin plugin;
    UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "features-" + UUID.randomUUID() + ".db");
        db.initialize();
        economy = new EconomyService(db, Map.of());
        economy.set(seller, "Seller", CurrencyType.ECUS, 100000).join();
        economy.set(buyer, "Buyer", CurrencyType.ECUS, 100000).join();
    }

    @AfterEach
    void cleanup() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    long scalar(String query) {
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

    AuctionListing listing() {
        return new AuctionListing(
                UUID.randomUUID().toString(),
                seller,
                "Seller",
                "item: ignored",
                10000,
                System.currentTimeMillis());
    }

    @Test
    void publicationAndAdAreChargedOnceAndNeverRefundedOnWithdrawal() {
        var store = new AuctionStore(db);
        var l = listing();
        store.publish(l, true).join();
        assertEquals(99400, economy.getBalances(seller).join().ecus());
        assertTrue(store.returnToOwner(l.id()).join());
        assertEquals(99400, economy.getBalances(seller).join().ecus());
        assertEquals(0, scalar("SELECT COUNT(*) FROM auctions"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM claim_items"));
    }

    @Test
    void saleWithPrepaidFeeCreditsFullPriceWithoutSecondTax() {
        var store = new AuctionStore(db);
        var l = listing();
        store.publish(l, false).join();
        assertEquals(99900, economy.getBalances(seller).join().ecus());
        assertEquals(
                EconomyService.AuctionPurchase.PURCHASED,
                economy.purchaseAuction(buyer, "Buyer", l.id()).join());
        assertEquals(109900, economy.getBalances(seller).join().ecus());
        assertEquals(90000, economy.getBalances(buyer).join().ecus());
    }

    @Test
    void failedPublicationRollsBackFee() {
        var store = new AuctionStore(db);
        var l = listing();
        store.publish(l, false).join();
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> store.publish(l, true).join());
        assertEquals(99900, economy.getBalances(seller).join().ecus());
    }

    @Test
    void concurrentPurchasesCannotExceedFiveAndDoNotChargeSixth() {
        var store = new ShopStore(db);
        var futures = new ArrayList<java.util.concurrent.CompletableFuture<Boolean>>();
        for (int i = 0; i < 6; i++)
            futures.add(store.buy(seller, "Seller", "rotation:1:egg", 5, 1000, "item: ignored"));
        assertEquals(
                5, futures.stream().filter(java.util.concurrent.CompletableFuture::join).count());
        assertEquals(95000, economy.getBalances(seller).join().ecus());
        assertEquals(5, scalar("SELECT sold FROM market_stock WHERE offer='rotation:1:egg'"));
        assertEquals(5, scalar("SELECT COUNT(*) FROM claim_items"));
    }

    @Test
    void insufficientFundsPreservesStockAndNoDelivery() {
        var store = new ShopStore(db);
        assertFalse(
                store.buy(seller, "Seller", "rotation:2:egg", 5, 100001, "item: ignored").join());
        assertEquals(0, scalar("SELECT COUNT(*) FROM market_stock"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM claim_items"));
        assertEquals(100000, economy.getBalances(seller).join().ecus());
    }

    @Test
    void killCounterUsesConfirmedIdempotentNormalKillsOnly() {
        var ledger = new RewardLedger(plugin, db);
        var hunts = new HuntingStore(ledger);
        assertTrue(
                hunts.kill(
                                "same",
                                seller,
                                "Seller",
                                "vache",
                                false,
                                Gains.of(0, 0, 0, 0),
                                List.of())
                        .join());
        assertFalse(
                hunts.kill(
                                "same",
                                seller,
                                "Seller",
                                "vache",
                                false,
                                Gains.of(0, 0, 0, 0),
                                List.of())
                        .join());
        assertTrue(
                hunts.kill("boss", seller, "Seller", "vache", true, Gains.of(0, 0, 0, 0), List.of())
                        .join());
        assertEquals(1, scalar("SELECT value FROM server_counters WHERE id='mob_kills'"));
        ledger.shutdown();
    }

    @Test
    void deliveryInsertFailureRollsBackStockAndDebit() {
        var store = new ShopStore(db);
        store.stocks().join();
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.execute(
                                        "CREATE TRIGGER reject_market_mail BEFORE INSERT ON"
                                                + " claim_items BEGIN SELECT"
                                                + " RAISE(ABORT,'injected');END");
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> store.buy(seller, "Seller", "failed", 5, 1000, "item: ignored").join());
        assertEquals(100000, economy.getBalances(seller).join().ecus());
        assertEquals(0, scalar("SELECT COUNT(*) FROM market_stock"));
    }
}
