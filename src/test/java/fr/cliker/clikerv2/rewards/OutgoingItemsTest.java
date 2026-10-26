package fr.cliker.clikerv2.rewards;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.auction.*;
import fr.cliker.clikerv2.economy.CurrencyType;

import org.bukkit.*;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class OutgoingItemsTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private ClaimService claims;
    private OutgoingItems outgoing;
    private PlayerMock player;
    private EconomyService economy;
    private final AtomicInteger saves = new AtomicInteger();
    private final AtomicBoolean failingSave = new AtomicBoolean();

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        ConfigurationSerialization.registerClass(ItemStackMock.class);
        db = new DatabaseManager(plugin, "outgoing-player-" + UUID.randomUUID() + ".db");
        db.initialize();
        claims = ClaimTestFactory.create(plugin, db);
        Bukkit.getServicesManager()
                .register(ClaimService.class, claims, plugin, ServicePriority.Normal);
        server.getPluginManager().registerEvents(claims, plugin);
        player = server.addPlayer();
        economy = new EconomyService(db, Map.of());
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.ECUS, 10000).join();
        outgoing =
                new OutgoingItems(
                        plugin,
                        db,
                        claims,
                        p -> {
                            assertSame(
                                    p,
                                    Bukkit.getPlayer(p.getUniqueId()),
                                    "Never save an obsolete connection");
                            saves.incrementAndGet();
                            if (failingSave.get())
                                throw new IllegalStateException("Simulated disk error");
                        });
        outgoing.start();
        drain();
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 8));
    }

    @AfterEach
    void cleanup() {
        try {
            outgoing.shutdown();
            claims.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void drain() {
        for (int i = 0; i < 12; i++) {
            db.submit(c -> null).join();
            server.getScheduler().performOneTick();
        }
    }

    private AuctionListing listing(ItemStack item) {
        return new AuctionListing(
                UUID.randomUUID().toString(),
                player.getUniqueId(),
                player.getName(),
                ClaimService.serialize(item.clone()),
                10000,
                System.currentTimeMillis());
    }

    @Test
    void realManagerPublishesNamedHeldItemAndRefusesInvalidRequestsWithoutRemoval() {
        var manager = new AuctionManager(plugin, new AuctionStore(db), economy);
        var item = player.getInventory().getItemInMainHand();
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Diamant de test"));
        meta.lore(java.util.List.of(net.kyori.adventure.text.Component.text("Données conservées")));
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
        assertFalse(manager.createListing(player, item, -1));
        assertEquals(8, player.getInventory().getItemInMainHand().getAmount());
        assertTrue(manager.createListing(player, item, 10000));
        drain();
        assertEquals(1, manager.activeListings().size());
        var stored = AuctionManager.deserialize(manager.activeListings().getFirst().itemYaml());
        assertEquals(meta.displayName(), stored.getItemMeta().displayName());
        assertEquals(meta.lore(), stored.getItemMeta().lore());
        assertEquals(8, stored.getAmount());
        assertEquals(9900, economy.getBalances(player.getUniqueId()).join().ecus());
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());
    }

    @Test
    void publicationTakesExactQuantityAndLocksCommandsOnlyDuringTransfer() {
        var sale = listing(new ItemStack(Material.DIAMOND, 3));
        var completed = new AtomicInteger();
        assertTrue(
                outgoing.consumeHeld(
                        player,
                        new ItemStack(Material.DIAMOND, 3),
                        id -> new AuctionStore(db).publish(sale, false, id),
                        e -> {
                            assertNull(e);
                            completed.incrementAndGet();
                        }));
        assertTrue(claims.isDelivering(player.getUniqueId()));
        var command = new PlayerCommandPreprocessEvent(player, "/spawn");
        server.getPluginManager().callEvent(command);
        assertTrue(command.isCancelled());
        assertFalse(
                outgoing.consumeHeld(
                        player,
                        new ItemStack(Material.DIAMOND),
                        id -> CompletableFuture.completedFuture(null),
                        e -> fail()));
        drain();
        assertEquals(1, completed.get());
        assertEquals(5, player.getInventory().getItemInMainHand().getAmount());
        assertFalse(claims.isDelivering(player.getUniqueId()));
        assertEquals(1, new AuctionStore(db).loadActive().join().size());
        assertEquals(9900, economy.getBalances(player.getUniqueId()).join().ecus());
        assertEquals(0, claims.store().page(player.getUniqueId(), 0).join().total());
        assertTrue(saves.get() >= 2);
    }

    @Test
    void changedHeldItemCancelsWithoutChargingOrRefundingAnotherItem() {
        var sale = listing(new ItemStack(Material.DIAMOND, 3));
        var failures = new AtomicInteger();
        outgoing.consumeHeld(
                player,
                new ItemStack(Material.DIAMOND, 3),
                id -> new AuctionStore(db).publish(sale, false, id),
                e -> {
                    assertNotNull(e);
                    failures.incrementAndGet();
                });
        // Simulate another plugin editing the slot while the SQL intention is queued.
        player.getInventory().setItemInMainHand(new ItemStack(Material.EMERALD, 2));
        drain();
        assertEquals(1, failures.get());
        assertEquals(Material.EMERALD, player.getInventory().getItemInMainHand().getType());
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(0, new AuctionStore(db).loadActive().join().size());
        assertEquals(10000, economy.getBalances(player.getUniqueId()).join().ecus());
        assertEquals(0, claims.store().page(player.getUniqueId(), 0).join().total());
    }

    @Test
    void failedSqlRefundIsDeliveredDirectlyOnceAndNeverPublishes() {
        outgoing.consumeHeld(
                player,
                new ItemStack(Material.DIAMOND, 3),
                id ->
                        CompletableFuture.failedFuture(
                                new IllegalStateException("simulated SQL failure")),
                e -> assertNotNull(e));
        drain();
        assertEquals(
                8,
                Arrays.stream(player.getInventory().getStorageContents())
                        .filter(Objects::nonNull)
                        .filter(i -> i.getType() == Material.DIAMOND)
                        .mapToInt(ItemStack::getAmount)
                        .sum());
        outgoing.recover(player);
        drain();
        assertEquals(
                8,
                Arrays.stream(player.getInventory().getStorageContents())
                        .filter(Objects::nonNull)
                        .filter(i -> i.getType() == Material.DIAMOND)
                        .mapToInt(ItemStack::getAmount)
                        .sum());
        assertEquals(0, claims.store().page(player.getUniqueId(), 0).join().total());
        assertFalse(claims.isDelivering(player.getUniqueId()));
    }

    @Test
    void failedPlayerSaveNeverPublishesAndRetriesBeforeRefunding() {
        failingSave.set(true);
        var commits = new AtomicInteger();
        outgoing.consumeHeld(
                player,
                new ItemStack(Material.DIAMOND, 3),
                id -> {
                    commits.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                },
                e -> assertNotNull(e));
        drain();
        assertEquals(0, commits.get());
        assertTrue(outgoing.locked(player.getUniqueId()));
        assertEquals(0, claims.store().page(player.getUniqueId(), 0).join().total());
        failingSave.set(false);
        server.getScheduler().performTicks(100);
        drain();
        assertEquals(0, commits.get());
        assertFalse(claims.isDelivering(player.getUniqueId()));
        assertEquals(
                8,
                Arrays.stream(player.getInventory().getStorageContents())
                        .filter(Objects::nonNull)
                        .filter(i -> i.getType() == Material.DIAMOND)
                        .mapToInt(ItemStack::getAmount)
                        .sum());
    }

    @Test
    void disconnectBeforeRemovalDoesNotTouchNewPlayerOrInventRefund() {
        var original = player;
        var sale = listing(new ItemStack(Material.DIAMOND, 3));
        var commits = new AtomicInteger();
        outgoing.consumeHeld(
                original,
                new ItemStack(Material.DIAMOND, 3),
                id -> {
                    commits.incrementAndGet();
                    return new AuctionStore(db).publish(sale, false, id);
                },
                e -> {});
        assertTrue(original.disconnect());
        player = new PlayerMock(server, original.getName(), original.getUniqueId());
        server.addPlayer(player);
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 8));
        drain();
        assertEquals(0, commits.get());
        assertEquals(8, original.getInventory().getItemInMainHand().getAmount());
        assertEquals(8, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(0, claims.store().page(player.getUniqueId(), 0).join().total());
        var top = player.getOpenInventory().getTopInventory();
        assertTrue(top == null || top.getType() == InventoryType.CRAFTING);
    }
}
