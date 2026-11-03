package fr.cliker.clikerv2.features;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.bag.*;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

class TalismansTest {
    ServerMock server;
    org.bukkit.plugin.java.JavaPlugin plugin;
    DatabaseManager db;
    BagManager bags;
    BagStore bagStore;
    ClaimService claims;
    OutgoingItems outgoing;
    Talismans module;
    PlayerMock player;
    AtomicBoolean failSave = new AtomicBoolean();

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("ClikerV2");
        ConfigurationSerialization.registerClass(ItemStackMock.class);
        TalismanItem.init(plugin);
        var constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        server.getCommandMap().register("clikerv2", constructor.newInstance("tallisement", plugin));
        db = new DatabaseManager(plugin, "registered-talismans-" + UUID.randomUUID() + ".db");
        db.initialize();
        claims = ClaimTestFactory.create(plugin, db);
        server.getServicesManager()
                .register(ClaimService.class, claims, plugin, ServicePriority.Normal);
        server.getPluginManager().registerEvents(claims, plugin);
        outgoing =
                new OutgoingItems(
                        plugin,
                        db,
                        claims,
                        p -> {
                            if (failSave.getAndSet(false))
                                throw new IllegalStateException("Simulation disque");
                        });
        outgoing.start();
        bagStore = new BagStore(db);
        bags = new BagManager(plugin, bagStore, p -> {});
        server.getPluginManager().registerEvents(bags, plugin);
        player = server.addPlayer("TalismanPlayer");
        new Menus(plugin);
        module = new Talismans(plugin, db, bags, new Menus(plugin));
        await(
                () ->
                        bags.isLoaded(player.getUniqueId())
                                && !claims.isDelivering(player.getUniqueId()));
    }

    @AfterEach
    void cleanup() {
        try {
            module.shutdown();
            outgoing.shutdown();
            claims.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    void await(BooleanSupplier condition) throws Exception {
        long until = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < until) {
            db.submit(c -> null).join();
            server.getScheduler().performOneTick();
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean());
    }

    ItemStack tagged(String id) {
        var item = new ItemStack(Material.NAUTILUS_SHELL);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer()
                .set(
                        new NamespacedKey(plugin, "collection_talisman"),
                        PersistentDataType.STRING,
                        id);
        item.setItemMeta(meta);
        return item;
    }

    PlayerInteractEvent click(EquipmentSlot hand) {
        var event =
                new PlayerInteractEvent(
                        player,
                        Action.RIGHT_CLICK_AIR,
                        player.getInventory().getItemInMainHand(),
                        null,
                        org.bukkit.block.BlockFace.UP,
                        hand);
        module.activate(event);
        return event;
    }

    void activate(ItemStack item, String id) throws Exception {
        player.getInventory().setItemInMainHand(item);
        click(EquipmentSlot.HAND);
        await(
                () ->
                        bags.registeredTalismans(player.getUniqueId()).contains(id)
                                && !claims.isDelivering(player.getUniqueId()));
    }

    @Test
    void rightClickConsumesOneItemOnceForNonOpAndDuplicateIsKept() throws Exception {
        assertFalse(player.isOp());
        var item = TalismanItem.createMobTalisman(MobTier.VACHE);
        item.setAmount(2);
        player.getInventory().setItemInMainHand(item);
        assertTrue(click(EquipmentSlot.HAND).isCancelled());
        click(EquipmentSlot.HAND);
        await(
                () ->
                        bags.registeredTalismans(player.getUniqueId()).contains("mob:vache")
                                && !claims.isDelivering(player.getUniqueId()));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        click(EquipmentSlot.HAND);
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(
                TalismanItem.mobBonusPercent(MobTier.VACHE),
                bags.mobDamageBonusPercent(player.getUniqueId(), MobTier.VACHE));
        assertEquals(Set.of("mob:vache"), bagStore.registered(player.getUniqueId()).join());
    }

    @Test
    void registrationSurvivesReloadAndRemovingBagObjectsCannotRemoveItsBonus() throws Exception {
        activate(TalismanItem.createMobTalisman(MobTier.VACHE), "mob:vache");
        bags.setContents(player.getUniqueId(), 0, new ItemStack[54]);
        assertTrue(bags.registeredTalismans(player.getUniqueId()).contains("mob:vache"));
        bags.onQuit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        bags.onJoin(new PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()));
        await(() -> bags.isLoaded(player.getUniqueId()));
        assertTrue(bags.registeredTalismans(player.getUniqueId()).contains("mob:vache"));
        assertEquals(
                TalismanItem.mobBonusPercent(MobTier.VACHE),
                bags.mobDamageBonusPercent(player.getUniqueId(), MobTier.VACHE));
    }

    @Test
    void generalBossTreasureAndAfkBonusesAreActiveWithoutAnyBagItem() throws Exception {
        activate(tagged("general:lame:3"), "general:lame:3");
        activate(tagged("general:lame:1"), "general:lame:1");
        activate(tagged("boss:vache"), "boss:vache");
        activate(TalismanItem.createTreasureTalisman(), "treasure");
        activate(tagged("afk:4"), "afk:4");
        assertEquals(3, bags.generalTier(player.getUniqueId(), "lame"));
        assertEquals(4, bags.generalTier(player.getUniqueId(), "afk"));
        assertTrue(bags.hasBossTalisman(player.getUniqueId(), MobTier.VACHE));
        assertTrue(bags.hasTreasureTalisman(player.getUniqueId()));
        assertTrue(Arrays.stream(bags.contents(player.getUniqueId(), 0)).allMatch(Objects::isNull));
    }

    @Test
    void offHandAndMalformedTagsDoNotConsumeOrGrantAnything() throws Exception {
        player.getInventory().setItemInMainHand(TalismanItem.createMobTalisman(MobTier.VACHE));
        click(EquipmentSlot.OFF_HAND);
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertTrue(bags.registeredTalismans(player.getUniqueId()).isEmpty());
        player.getInventory().setItemInMainHand(tagged("boss:missing_mob"));
        click(EquipmentSlot.HAND);
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertTrue(bagStore.registered(player.getUniqueId()).join().isEmpty());
    }

    @Test
    void failedPlayerSaveCannotGrantPermanentBonusAndItemIsRecovered() throws Exception {
        failSave.set(true);
        player.getInventory().setItemInMainHand(TalismanItem.createMobTalisman(MobTier.VACHE));
        click(EquipmentSlot.HAND);
        await(() -> !claims.isDelivering(player.getUniqueId()));
        assertTrue(bagStore.registered(player.getUniqueId()).join().isEmpty());
        assertEquals(0, bags.mobDamageBonusPercent(player.getUniqueId(), MobTier.VACHE));
        assertTrue(
                player.getInventory().contains(Material.NAUTILUS_SHELL)
                        || claims.store().page(player.getUniqueId(), 0).join().total() > 0);
    }

    @Test
    void collectionMenuIsReadOnlyAndUsesSpacedContentSlots() throws Exception {
        activate(TalismanItem.createMobTalisman(MobTier.VACHE), "mob:vache");
        player.performCommand("tallisement");
        await(
                () ->
                        player.getOpenInventory().getTopInventory() != null
                                && player.getOpenInventory().getTopInventory().getSize() == 54);
        var inventory = player.getOpenInventory().getTopInventory();
        assertNotNull(inventory.getItem(10));
        assertTrue(inventory.getItem(10).getItemMeta().lore().toString().contains("permanent"));
        var event =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        10,
                        ClickType.SHIFT_LEFT,
                        InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertFalse(player.getInventory().contains(Material.NAUTILUS_SHELL));
        assertTrue(bags.registeredTalismans(player.getUniqueId()).contains("mob:vache"));
    }
}
