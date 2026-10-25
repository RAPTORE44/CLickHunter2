package fr.cliker.clikerv2.economy;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.auction.*;
import fr.cliker.clikerv2.rewards.MailStore;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.concurrent.*;

class EconomySafetyTest {
    private DatabaseManager db;
    private EconomyService economy;
    private AuctionStore auctions;
    private final UUID buyer = UUID.randomUUID(), seller = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var p = MockBukkit.createMockPlugin();
        db = new DatabaseManager(p, "safe-" + UUID.randomUUID() + ".db");
        db.initialize();
        economy = new EconomyService(db, Map.of());
        auctions = new AuctionStore(db);
        economy.set(buyer, "Acheteur", CurrencyType.ECUS, 1000).join();
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
    void overflowNeverConvertsBalanceToFloatingPoint() {
        economy.set(buyer, "Acheteur", CurrencyType.ECUS, Long.MAX_VALUE).join();
        assertThrows(
                CompletionException.class,
                () -> economy.change(buyer, "Acheteur", CurrencyType.ECUS, 1).join());
        assertEquals(Long.MAX_VALUE, economy.getBalances(buyer).join().ecus());
        assertThrows(
                CompletionException.class,
                () -> economy.change(buyer, "Acheteur", CurrencyType.ECUS, Long.MIN_VALUE).join());
    }

    @Test
    void itemPurchaseQueuesOnceAndInsufficientFundsDoNotCreateItem() {
        assertTrue(
                economy.purchaseItem(buyer, "Acheteur", "one", CurrencyType.ECUS, 200, "objet")
                        .join());
        assertTrue(
                economy.purchaseItem(buyer, "Acheteur", "one", CurrencyType.ECUS, 200, "objet")
                        .join());
        assertFalse(
                economy.purchaseItem(buyer, "Acheteur", "two", CurrencyType.ECUS, 900, "objet")
                        .join());
        assertEquals(800, economy.getBalances(buyer).join().ecus());
        assertEquals(1, new MailStore(db).page(buyer, 0).join().total());
    }

    @Test
    void transformationReturnsOriginalOrModifiedObjectAndCannotDebitTwice() {
        assertFalse(
                economy.transformItem(
                                buyer,
                                "Acheteur",
                                "transform1",
                                CurrencyType.CENDRES,
                                10,
                                "original",
                                "modified")
                        .join());
        assertEquals(
                "original", new MailStore(db).page(buyer, 0).join().entries().getFirst().payload());
        assertTrue(
                economy.transformItem(
                                buyer,
                                "Acheteur",
                                "transform2",
                                CurrencyType.ECUS,
                                200,
                                "original",
                                "modified")
                        .join());
        assertTrue(
                economy.transformItem(
                                buyer,
                                "Acheteur",
                                "transform2",
                                CurrencyType.ECUS,
                                200,
                                "original",
                                "modified")
                        .join());
        assertEquals(800, economy.getBalances(buyer).join().ecus());
        assertEquals(2, new MailStore(db).page(buyer, 0).join().total());
    }

    private void listing(String id, long price) {
        auctions.save(
                        new AuctionListing(
                                id, seller, "Vendeur", "sword", price, System.currentTimeMillis()))
                .join();
    }

    @Test
    void concurrentAuctionPurchasesCreditSellerAndBuyerOnlyOnce() {
        listing("vente", 250);
        List<CompletableFuture<EconomyService.AuctionPurchase>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) calls.add(economy.purchaseAuction(buyer, "Acheteur", "vente"));
        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();
        assertEquals(
                1,
                calls.stream()
                        .map(CompletableFuture::join)
                        .filter(r -> r == EconomyService.AuctionPurchase.PURCHASED)
                        .count());
        assertEquals(750, economy.getBalances(buyer).join().ecus());
        assertEquals(247, economy.getBalances(seller).join().ecus());
        assertEquals(1, new MailStore(db).page(buyer, 0).join().total());
        assertTrue(auctions.loadActive().join().isEmpty());
    }

    @Test
    void auctionFailureRollsBackBuyerSellerItemAndListing() {
        listing("max", 250);
        economy.set(seller, "Vendeur", CurrencyType.ECUS, Long.MAX_VALUE).join();
        assertThrows(
                CompletionException.class,
                () -> economy.purchaseAuction(buyer, "Acheteur", "max").join());
        assertEquals(1000, economy.getBalances(buyer).join().ecus());
        assertEquals(1, auctions.loadActive().join().size());
        assertEquals(0, new MailStore(db).page(buyer, 0).join().total());
    }

    @Test
    void adminCancellationReturnsToOriginalSellerAndCannotBeClaimedTwice() {
        listing("annulee", 250);
        assertTrue(auctions.returnToOwner("annulee").join());
        assertFalse(auctions.returnToOwner("annulee").join());
        assertEquals(1, new MailStore(db).page(seller, 0).join().total());
        assertEquals(0, new MailStore(db).page(buyer, 0).join().total());
        assertEquals(
                EconomyService.AuctionPurchase.UNAVAILABLE,
                economy.purchaseAuction(buyer, "Acheteur", "annulee").join());
    }
}
