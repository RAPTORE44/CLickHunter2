package fr.cliker.clikerv2.rewards;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;

import org.bukkit.*;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;

import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

class ClaimServiceTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private PlayerMock player;
    private DatabaseManager database;
    private ClaimService claims;
    private String databaseFile;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
        ConfigurationSerialization.registerClass(ItemStackMock.class);
        player.addAttachment(plugin, "cliker.claim", true);
        Path file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "claim.yml").toPath();
        Files.createDirectories(file.getParent());
        try (var input = getClass().getResourceAsStream("/claim.yml")) {
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
        }
        databaseFile = "mail-" + UUID.randomUUID() + ".db";
        database = new DatabaseManager(plugin, databaseFile);
        database.initialize();
        // La frontière de sauvegarde disque est simulée, pas la livraison ni ses transactions.
        claims =
                new ClaimService(
                        plugin,
                        database,
                        p -> {},
                        (p, items) -> {
                            for (int i = 0; i < items.length; i++)
                                p.getInventory().setItem(i, items[i]);
                        });
        server.getPluginManager().registerEvents(claims, plugin);
    }

    @AfterEach
    void cleanup() {
        try {
            claims.shutdown();
            database.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 4_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
            server.getScheduler().performOneTick();
        }
        assertTrue(condition.getAsBoolean());
    }

    private MailStore.Entry enqueue(UUID owner, String source, ItemStack item) {
        return claims.store().enqueue(owner, source, ClaimService.serialize(item)).join();
    }

    private void drain() {
        database.submit(c -> null).join();
        server.getScheduler().performOneTick();
    }

    private void fillInventory() {
        for (int i = 0; i < 36; i++)
            player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        // Le simulateur addItem inspecte aussi les slots d'équipement, contrairement à Paper.
        player.getInventory().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        player.getInventory().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        player.getInventory().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        player.getInventory().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
    }

    @Test
    void concurrentOverflowSplitsConserveQuantityAndOnlyOneCanCommit() throws Exception {
        var entry =
                enqueue(
                        player.getUniqueId(),
                        "split-concurrent",
                        new ItemStack(Material.DIAMOND, 10));
        var secondDb = new DatabaseManager(plugin, databaseFile);
        try {
            var secondStore = new MailStore(secondDb);
            String part = ClaimService.serialize(new ItemStack(Material.DIAMOND, 4));
            String surplus = ClaimService.serialize(new ItemStack(Material.DIAMOND, 6));
            var first =
                    claims.store()
                            .split(
                                    player.getUniqueId(),
                                    entry.id(),
                                    entry.payload(),
                                    part,
                                    surplus);
            var second =
                    secondStore.split(
                            player.getUniqueId(), entry.id(), entry.payload(), part, surplus);
            java.util.concurrent.CompletableFuture.allOf(first, second).join();
            assertNotEquals(first.join(), second.join());
            var page = claims.store().page(player.getUniqueId(), 0).join();
            assertEquals(2, page.total());
            assertEquals(
                    10,
                    page.entries().stream()
                            .mapToInt(e -> ClaimService.deserialize(e.payload()).getAmount())
                            .sum());
        } finally {
            secondDb.close();
        }
    }

    @Test
    void automaticPurchaseDeliverySplitsOnlyTheOverflowWhenSomeSpaceRemains() throws Exception {
        fillInventory();
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 60));
        enqueue(player.getUniqueId(), "purchase-partial", new ItemStack(Material.DIAMOND, 10));
        claims.notifyAndDeliver(player.getUniqueId());
        await(
                () ->
                        player.getInventory().getItem(0).getAmount() == 64
                                && !claims.isDelivering(player.getUniqueId()));
        var mail = claims.store().page(player.getUniqueId(), 0).join();
        assertEquals(1, mail.total());
        assertEquals(6, ClaimService.deserialize(mail.entries().getFirst().payload()).getAmount());
    }

    @Test
    void automaticDeliveryWorksWithAnotherMenuOpenAndNoMailboxDetour() throws Exception {
        var shop = Bukkit.createInventory(null, 27);
        player.openInventory(shop);
        var entry = enqueue(player.getUniqueId(), "shop-open", new ItemStack(Material.DIAMOND, 4));
        claims.notifyAndDeliver(player.getUniqueId());
        await(
                () ->
                        player.getInventory().contains(Material.DIAMOND, 4)
                                && !claims.isDelivering(player.getUniqueId()));
        assertSame(shop, player.getOpenInventory().getTopInventory());
        assertEquals(0, claims.store().page(player.getUniqueId(), 0).join().total());
    }

    @Test
    void directDropsUseFreeSpaceAndOnlyOverflowRemainsInClaim() throws Exception {
        fillInventory();
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 60));
        claims.give(player.getUniqueId(), player.getName(), new ItemStack(Material.DIAMOND, 10));
        await(() -> claims.store().page(player.getUniqueId(), 0).join().total() == 1);
        drain();
        assertEquals(64, player.getInventory().getItem(0).getAmount());
        var mail = claims.store().page(player.getUniqueId(), 0).join();
        assertEquals(6, ClaimService.deserialize(mail.entries().getFirst().payload()).getAmount());
    }

    @Test
    void fullInventoryQueuesInsteadOfDroppingAndClaimDoesNotConsumeIt() throws Exception {
        fillInventory();
        claims.give(player.getUniqueId(), player.getName(), new ItemStack(Material.DIAMOND, 2));
        drain();
        var page = claims.store().page(player.getUniqueId(), 0).join();
        assertEquals(1, page.total());
        claims.deliver(player, page.entries().getFirst().id(), false);
        await(() -> !claims.isDelivering(player.getUniqueId()));
        assertEquals(1, claims.store().page(player.getUniqueId(), 0).join().total());
        player.getInventory().setItem(0, null);
        claims.deliver(player, page.entries().getFirst().id(), false);
        await(() -> claims.store().page(player.getUniqueId(), 0).join().total() == 0);
        server.getScheduler().performOneTick();
        assertEquals(2, player.getInventory().getItem(0).getAmount());
        assertEquals(Material.DIAMOND, player.getInventory().getItem(0).getType());
    }

    @Test
    void twoClicksCannotDeliverTwiceAndForeignPlayerCannotTakeItem() throws Exception {
        var item = enqueue(player.getUniqueId(), "reward", new ItemStack(Material.DIAMOND, 3));
        PlayerMock thief = server.addPlayer();
        claims.deliver(thief, item.id(), false);
        drain();
        assertTrue(thief.getInventory().isEmpty());
        for (int i = 0; i < 10; i++) claims.deliver(player, item.id(), false);
        await(() -> claims.store().page(player.getUniqueId(), 0).join().total() == 0);
        drain();
        int total =
                Arrays.stream(player.getInventory().getStorageContents())
                        .filter(Objects::nonNull)
                        .filter(s -> s.getType() == Material.DIAMOND)
                        .mapToInt(ItemStack::getAmount)
                        .sum();
        assertEquals(3, total);
    }

    @Test
    void deliveryCanMergeWithoutAnEmptySlotAndNeverPartiallyConsumesFullMailboxItem()
            throws Exception {
        fillInventory();
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 60));
        var one = enqueue(player.getUniqueId(), "fits", new ItemStack(Material.DIAMOND, 4));
        claims.deliver(player, one.id(), false);
        await(() -> claims.store().page(player.getUniqueId(), 0).join().total() == 0);
        drain();
        assertEquals(64, player.getInventory().getItem(0).getAmount());
        var tooMuch = enqueue(player.getUniqueId(), "full", new ItemStack(Material.DIAMOND, 2));
        claims.deliver(player, tooMuch.id(), false);
        await(() -> !claims.isDelivering(player.getUniqueId()));
        assertEquals(1, claims.store().page(player.getUniqueId(), 0).join().total());
        assertEquals(64, player.getInventory().getItem(0).getAmount());
    }

    @Test
    void offlineRewardAndPaginationRemainAfterReopeningDatabase() throws Exception {
        UUID offline = UUID.randomUUID();
        claims.give(offline, "Absent", new ItemStack(Material.DIAMOND, 70));
        drain();
        assertEquals(2, claims.store().page(offline, 0).join().total());
        for (int i = 0; i < 46; i++)
            enqueue(player.getUniqueId(), "page-" + i, new ItemStack(Material.EMERALD));
        assertEquals(45, claims.store().page(player.getUniqueId(), 0).join().entries().size());
        assertEquals(1, claims.store().page(player.getUniqueId(), 1).join().entries().size());
        claims.shutdown();
        database.close();
        database = new DatabaseManager(plugin, databaseFile);
        database.initialize();
        claims =
                new ClaimService(
                        plugin,
                        database,
                        p -> {},
                        (p, items) -> {
                            for (int i = 0; i < items.length; i++)
                                p.getInventory().setItem(i, items[i]);
                        });
        assertEquals(2, claims.store().page(offline, 0).join().total());
    }

    @Test
    void recoveryReceiptAcknowledgesWithoutGrantingAgain() throws Exception {
        var item = enqueue(player.getUniqueId(), "recovery", new ItemStack(Material.DIAMOND, 2));
        claims.store()
                .reserve(
                        player.getUniqueId(),
                        item.id(),
                        "interrupted-process",
                        System.currentTimeMillis())
                .join();
        var planned =
                claims.plan(
                        player.getInventory().getStorageContents(),
                        new ItemStack(Material.DIAMOND, 2),
                        item.id());
        for (int i = 0; i < planned.length; i++) player.getInventory().setItem(i, planned[i]);
        claims.shutdown();
        database.close();
        database = new DatabaseManager(plugin, databaseFile);
        database.initialize();
        claims = ClaimTestFactory.create(plugin, database);
        claims.join(
                new org.bukkit.event.player.PlayerJoinEvent(
                        player, net.kyori.adventure.text.Component.empty()));
        assertTrue(claims.isDelivering(player.getUniqueId()));
        await(() -> claims.store().page(player.getUniqueId(), 0).join().total() == 0);
        drain();
        assertEquals(2, player.getInventory().getItem(0).getAmount());
    }

    @Test
    void failedPlayerSaveRetriesWithoutReleasingOrDuplicatingItem() throws Exception {
        claims.shutdown();
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        claims =
                new ClaimService(
                        plugin,
                        database,
                        p -> {
                            if (attempts.getAndIncrement() == 0)
                                throw new IllegalStateException("Simulated save failure");
                        },
                        (p, items) -> {
                            for (int i = 0; i < items.length; i++)
                                p.getInventory().setItem(i, items[i]);
                        });
        var item = enqueue(player.getUniqueId(), "retry-save", new ItemStack(Material.DIAMOND, 2));
        claims.deliver(player, item.id(), false);
        database.submit(c -> null).join();
        server.getScheduler().performTicks(2);
        assertTrue(claims.isDelivering(player.getUniqueId()));
        assertEquals(1, claims.store().page(player.getUniqueId(), 0).join().total());
        server.getScheduler().performTicks(40);
        await(() -> claims.store().page(player.getUniqueId(), 0).join().total() == 0);
        drain();
        assertEquals(2, player.getInventory().getItem(0).getAmount());
        assertFalse(claims.isDelivering(player.getUniqueId()));
    }

    @Test
    void chestIconsCannotBeTakenOrDragged() throws Exception {
        enqueue(player.getUniqueId(), "gui", new ItemStack(Material.DIAMOND));
        claims.open(player, 0);
        await(
                () ->
                        player.getOpenInventory().getTopInventory() != null
                                && player.getOpenInventory().getTopInventory().getSize() == 54);
        var shift =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        54,
                        ClickType.SHIFT_LEFT,
                        InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(shift);
        assertTrue(shift.isCancelled());
        var drag =
                new InventoryDragEvent(
                        player.getOpenInventory(),
                        new ItemStack(Material.EMERALD),
                        new ItemStack(Material.EMERALD, 2),
                        false,
                        Map.of(0, new ItemStack(Material.EMERALD)));
        server.getPluginManager().callEvent(drag);
        assertTrue(drag.isCancelled());
        assertEquals(1, claims.store().page(player.getUniqueId(), 0).join().total());
    }

    private PlayerMock useUuidBasedOnlinePlayer() {
        UUID id = player.getUniqueId();
        String name = player.getName();
        assertTrue(player.disconnect());
        var replacement =
                new PlayerMock(server, name, id) {
                    @Override
                    public boolean isOnline() {
                        return Bukkit.getPlayer(getUniqueId()) != null;
                    }
                };
        server.addPlayer(replacement);
        player = replacement;
        for (int j = 0; j < 6; j++) drain();
        return replacement;
    }

    @Test
    void staleReservedClaimNeverWritesInventoryOfOldConnection() throws Exception {
        var old = useUuidBasedOnlinePlayer();
        var entry = enqueue(old.getUniqueId(), "stale-reserve", new ItemStack(Material.DIAMOND, 2));
        claims.deliver(old, entry.id(), true);
        database.submit(c -> null)
                .join(); // SQL complete, main-thread callback deliberately delayed.
        assertTrue(old.disconnect());
        var fresh = new PlayerMock(server, old.getName(), old.getUniqueId());
        server.addPlayer(fresh);
        assertTrue(old.isOnline(), "Reproduce a Player implementation reporting UUID presence");
        await(
                () ->
                        !claims.isDelivering(fresh.getUniqueId())
                                && claims.store().page(fresh.getUniqueId(), 0).join().total() == 0);
        assertNull(old.getInventory().getItem(0), "No item may enter the obsolete inventory");
        assertEquals(2, fresh.getInventory().getItem(0).getAmount());
    }

    @Test
    void staleNotificationCannotDeliverToOldPlayerOrReserveForIt() throws Exception {
        var old = useUuidBasedOnlinePlayer();
        enqueue(old.getUniqueId(), "stale-notify", new ItemStack(Material.EMERALD, 3));
        claims.notifyAndDeliver(old.getUniqueId());
        database.submit(c -> null).join();
        assertTrue(old.disconnect());
        var fresh = new PlayerMock(server, old.getName(), old.getUniqueId());
        server.addPlayer(fresh);
        await(
                () ->
                        !claims.isDelivering(fresh.getUniqueId())
                                && claims.store().page(fresh.getUniqueId(), 0).join().total() == 0);
        assertNull(old.getInventory().getItem(0));
        assertEquals(3, fresh.getInventory().getItem(0).getAmount());
    }

    @Test
    void reconnectDuringFailedSaveNeverWritesOldPlayerInstance() throws Exception {
        claims.shutdown();
        var saves = new java.util.concurrent.atomic.AtomicInteger();
        var stale = new java.util.concurrent.atomic.AtomicBoolean();
        claims =
                new ClaimService(
                        plugin,
                        database,
                        p -> {
                            if (Bukkit.getPlayer(p.getUniqueId()) != p) {
                                stale.set(true);
                                throw new AssertionError("Stale player save");
                            }
                            if (saves.getAndIncrement() == 0)
                                throw new IllegalStateException("First save failed");
                        },
                        (p, items) -> {
                            for (int i = 0; i < items.length; i++)
                                p.getInventory().setItem(i, items[i]);
                        });
        server.getPluginManager().registerEvents(claims, plugin);
        var entry = enqueue(player.getUniqueId(), "reconnect", new ItemStack(Material.DIAMOND, 2));
        claims.deliver(player, entry.id(), false);
        drain();
        assertTrue(claims.isDelivering(player.getUniqueId()));
        UUID owner = player.getUniqueId();
        String name = player.getName();
        assertTrue(player.disconnect());
        var fresh = new PlayerMock(server, name, owner);
        server.addPlayer(fresh);
        fresh.addAttachment(plugin, "cliker.claim", true);
        server.getScheduler().performTicks(45);
        await(
                () ->
                        !claims.isDelivering(owner)
                                && claims.store().page(owner, 0).join().total() == 0);
        assertFalse(stale.get());
        assertEquals(2, fresh.getInventory().getItem(0).getAmount());
    }

    @Test
    void receiptCleanupFailureRetriesWithoutFreezingOrGivingAgain() throws Exception {
        claims.shutdown();
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        claims =
                new ClaimService(
                        plugin,
                        database,
                        p -> {
                            if (attempts.getAndIncrement() == 1)
                                throw new IllegalStateException("Cleanup save failed");
                        },
                        (p, items) -> {
                            for (int i = 0; i < items.length; i++)
                                p.getInventory().setItem(i, items[i]);
                        });
        var entry = enqueue(player.getUniqueId(), "cleanup", new ItemStack(Material.DIAMOND, 2));
        claims.deliver(player, entry.id(), false);
        await(() -> attempts.get() >= 2);
        assertTrue(claims.isDelivering(player.getUniqueId()));
        assertEquals(0, claims.store().page(player.getUniqueId(), 0).join().total());
        server.getScheduler().performTicks(40);
        await(() -> !claims.isDelivering(player.getUniqueId()));
        assertEquals(2, player.getInventory().getItem(0).getAmount());
    }

    @Test
    void directRewardsWaitInClaimInsteadOfEnteringBuilderInventory() {
        var profiles =
                new fr.cliker.clikerv2.outpost.WorldInventories(
                        plugin, player.getWorld().getName());
        Bukkit.getServicesManager()
                .register(
                        fr.cliker.clikerv2.outpost.WorldInventories.class,
                        profiles,
                        plugin,
                        org.bukkit.plugin.ServicePriority.Normal);
        try {
            claims.give(player.getUniqueId(), player.getName(), new ItemStack(Material.DIAMOND, 3));
            drain();
            assertFalse(player.getInventory().contains(Material.DIAMOND));
            assertEquals(1, claims.store().page(player.getUniqueId(), 0).join().entries().size());
            claims.notifyAndDeliver(player.getUniqueId());
            drain();
            assertFalse(player.getInventory().contains(Material.DIAMOND));
        } finally {
            profiles.shutdown();
        }
    }

    @Test
    void aPurchasedItemBeyondTheFirstClaimPageUsesAvailableStackSpace() throws Exception {
        fillInventory();
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 60));
        for (int i = 0; i < 46; i++)
            enqueue(player.getUniqueId(), "old-unstackable-" + i, new ItemStack(Material.DIRT, 64));
        var economy = new fr.cliker.clikerv2.EconomyService(database, java.util.Map.of());
        economy.set(
                        player.getUniqueId(),
                        player.getName(),
                        fr.cliker.clikerv2.economy.CurrencyType.ECUS,
                        100)
                .join();
        assertTrue(
                economy.purchaseItem(
                                player.getUniqueId(),
                                player.getName(),
                                "purchase-new",
                                fr.cliker.clikerv2.economy.CurrencyType.ECUS,
                                10,
                                ClaimService.serialize(new ItemStack(Material.DIAMOND, 2)))
                        .join());
        claims.deliverPurchase(player.getUniqueId(), "purchase-new");
        await(
                () ->
                        player.getInventory().getItem(0).getAmount() == 62
                                && claims.store().page(player.getUniqueId(), 0).join().total()
                                        == 46);
        assertEquals(62, player.getInventory().getItem(0).getAmount());
    }

    @Test
    void purchaseOverflowAloneWaitsInClaimAndFreeSpaceIsFilledFirst() throws Exception {
        fillInventory();
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 60));
        enqueue(player.getUniqueId(), "purchase-overflow", new ItemStack(Material.DIAMOND, 10));
        claims.deliverPurchase(player.getUniqueId(), "purchase-overflow");
        await(
                () ->
                        player.getInventory().getItem(0).getAmount() == 64
                                && claims.store().page(player.getUniqueId(), 0).join().total()
                                        == 1);
        var rows = claims.store().page(player.getUniqueId(), 0).join();
        assertEquals(6, ClaimService.deserialize(rows.entries().getFirst().payload()).getAmount());
    }

    @Test
    void anEmptyInventoryReceivesThePurchaseWithoutLeavingItInClaim() throws Exception {
        enqueue(player.getUniqueId(), "purchase-empty", new ItemStack(Material.DIAMOND_SWORD));
        claims.deliverPurchase(player.getUniqueId(), "purchase-empty");
        await(
                () ->
                        player.getInventory().contains(Material.DIAMOND_SWORD)
                                && claims.store().page(player.getUniqueId(), 0).join().total()
                                        == 0);
    }
}
