package fr.cliker.clikerv2.bag;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class BagTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private DatabaseManager database;
    private BagStore store;
    private BagManager manager;
    private BagMenu menu;
    private PlayerMock player;
    private String databaseUrl;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        ConfigurationSerialization.registerClass(ItemStackMock.class);
        plugin = MockBukkit.createMockPlugin();
        String filename = "test-" + UUID.randomUUID() + ".db";
        database = new DatabaseManager(plugin, filename);
        databaseUrl =
                "jdbc:sqlite:"
                        + new java.io.File(plugin.getDataFolder(), filename).getAbsolutePath();
        database.initialize();
        store = new BagStore(database);
        manager = new BagManager(plugin, store);
        menu = new BagMenu(plugin, manager);
        server.getPluginManager().registerEvents(menu, plugin);
        player = server.addPlayer();
    }

    @AfterEach
    void cleanup() {
        if (menu != null) menu.shutdown();
        if (database != null) {
            database.submit(connection -> null).join();
            database.close();
        }
        MockBukkit.unmock();
    }

    private void load() throws Exception {
        manager.loadOnlinePlayers();
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!manager.isLoaded(player.getUniqueId()) && System.nanoTime() < deadline) {
            Thread.sleep(10);
            server.getScheduler().performOneTick();
        }
        assertTrue(manager.isLoaded(player.getUniqueId()));
    }

    private InventoryClickEvent click(int rawSlot, ClickType click, InventoryAction action) {
        InventoryClickEvent event =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        rawSlot,
                        click,
                        action);
        server.getPluginManager().callEvent(event);
        server.getScheduler().performOneTick();
        return event;
    }

    @Test
    void selectorHasBlackEndsSevenRanksAndNoPermissionText() throws Exception {
        load();
        menu.open(player);
        var inventory = player.getOpenInventory().getTopInventory();
        assertEquals(9, inventory.getSize());
        for (int slot : new int[] {0, 8}) {
            assertEquals(Material.BLACK_STAINED_GLASS_PANE, inventory.getItem(slot).getType());
            assertEquals(
                    Component.text(" ")
                            .decoration(
                                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    inventory.getItem(slot).getItemMeta().displayName());
        }
        assertEquals(Material.NETHER_STAR, inventory.getItem(1).getType());
        for (int slot = 2; slot <= 7; slot++)
            assertEquals(Material.BARRIER, inventory.getItem(slot).getType());
        String[] ranks = {"Joueur", "Mini-VIP", "VIP", "VIP+", "MVP", "MVP+", "Legend"};
        for (int slot = 1; slot <= 7; slot++) {
            String lore = inventory.getItem(slot).getItemMeta().lore().toString();
            assertTrue(lore.contains(ranks[slot - 1]));
            assertFalse(lore.contains("cliker.sac"));
        }
    }

    @Test
    void menuCannotBeLootedAndLockedBagDoesNotOpen() throws Exception {
        load();
        menu.open(player);
        var selector = player.getOpenInventory().getTopInventory();
        assertTrue(click(2, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        assertSame(selector, player.getOpenInventory().getTopInventory());
        InventoryDragEvent drag =
                new InventoryDragEvent(
                        player.getOpenInventory(),
                        new ItemStack(Material.DIAMOND),
                        new ItemStack(Material.DIAMOND, 2),
                        false,
                        Map.of(1, new ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(drag);
        assertTrue(drag.isCancelled());
        assertTrue(
                click(9, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY)
                        .isCancelled());
        assertTrue(click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        assertSame(selector, player.getOpenInventory().getTopInventory());
    }

    @Test
    void storageKeepsExactSlotsAcrossCloseReloadAndSeparatesPlayersAndBags() throws Exception {
        load();
        player.addAttachment(plugin, "cliker.sac.2", true);
        menu.open(player);
        assertTrue(click(2, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        var chest = player.getOpenInventory().getTopInventory();
        assertEquals(54, chest.getSize());
        assertFalse(click(53, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        ItemStack diamond = new ItemStack(Material.DIAMOND, 13);
        chest.setItem(53, diamond);
        player.closeInventory();
        String yaml = store.load(player.getUniqueId()).join();
        var loaded = BagManager.deserialize(yaml);
        assertNull(loaded.get(1)[0]);
        assertEquals(diamond, loaded.get(1)[53]);
        assertNull(loaded.getFirst()[53]);
        assertNull(store.load(UUID.randomUUID()).join());
        menu.open(player);
        click(2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertEquals(diamond, player.getOpenInventory().getTopInventory().getItem(53));
    }

    @Test
    void oldItemsMigrateIntoFirstChestAndRoundTripWithoutMovingSlots() throws Exception {
        var old = new YamlConfiguration();
        old.set(
                "items",
                List.of(new ItemStack(Material.EMERALD, 3), new ItemStack(Material.DIAMOND, 2)));
        var migrated = BagManager.deserialize(old.saveToString());
        assertEquals(7, migrated.size());
        assertEquals(54, migrated.getFirst().length);
        assertEquals(Material.EMERALD, migrated.getFirst()[0].getType());
        assertEquals(Material.DIAMOND, migrated.getFirst()[1].getType());
        migrated.get(6)[53] = new ItemStack(Material.GOLD_INGOT, 8);
        var restored = BagManager.deserialize(BagManager.serialize(migrated));
        for (int i = 0; i < 7; i++) assertArrayEquals(migrated.get(i), restored.get(i));
    }

    @Test
    void allRanksCanUnlockAndCorruptDataIsRejected() throws Exception {
        for (int n = 2; n <= 9; n++) {
            var attachment = player.addAttachment(plugin, "cliker.sac." + n, true);
            assertEquals(Math.min(n, 7), manager.capacity(player));
            player.removeAttachment(attachment);
        }
        assertThrows(
                InvalidConfigurationException.class,
                () -> BagManager.deserialize("items: [not-an-item]"));
        assertThrows(
                InvalidConfigurationException.class, () -> BagManager.deserialize("version: 99"));
        assertThrows(IllegalStateException.class, () -> manager.contents(player.getUniqueId(), 0));
        assertFalse(manager.isLoaded(player.getUniqueId()));
        menu.open(player);
        var top = player.getOpenInventory().getTopInventory();
        assertTrue(top == null || top.getSize() != 9);
    }

    @Test
    void shutdownSavesOpenChestAndDatabaseDrainsPendingWrites() throws Exception {
        load();
        menu.open(player);
        click(1, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        ItemStack item = new ItemStack(Material.EMERALD, 11);
        player.getOpenInventory().getTopInventory().setItem(27, item);
        menu.shutdown();
        database.close();
        // Relire depuis une nouvelle connexion, sans dépendre du cache du gestionnaire.
        // La base d'origine reste lisible après fermeture de sa file de travail.
        try (var connection = java.sql.DriverManager.getConnection(databaseUrl);
                var query = connection.prepareStatement("SELECT items FROM bags WHERE uuid = ?")) {
            query.setString(1, player.getUniqueId().toString());
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(item, BagManager.deserialize(rows.getString(1)).getFirst()[27]);
            }
        } finally {
            database = null;
        }
    }

    @Test
    void derivedTalismanCacheChangesOnlyWithActualBagContents() throws Exception {
        TalismanItem.init(plugin);
        load();
        var owner = player.getUniqueId();
        var contents = manager.contents(owner, 0);
        contents[0] = TalismanItem.createMobTalisman(fr.cliker.clikerv2.MobTier.VACHE);
        contents[1] = TalismanItem.createTreasureTalisman();
        manager.setContents(owner, 0, contents);
        assertTrue(manager.hasTreasureTalisman(owner));
        assertEquals(
                TalismanItem.mobBonusPercent(fr.cliker.clikerv2.MobTier.VACHE),
                manager.mobDamageBonusPercent(owner, fr.cliker.clikerv2.MobTier.VACHE));
        contents[0] = null;
        contents[1] = null;
        assertTrue(manager.hasTreasureTalisman(owner));
        manager.setContents(owner, 0, contents);
        assertFalse(manager.hasTreasureTalisman(owner));
        assertEquals(0, manager.mobDamageBonusPercent(owner, fr.cliker.clikerv2.MobTier.VACHE));
    }
}
