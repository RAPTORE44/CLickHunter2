package fr.cliker.clikerv2.upgrade;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;

import org.bukkit.*;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class UpgradeMenuTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private EconomyService economy;
    private UpgradeMenu menu;
    private org.mockbukkit.mockbukkit.entity.PlayerMock player;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "upgrade-ui-" + UUID.randomUUID() + ".db");
        db.initialize();
        economy = new EconomyService(db, Map.of());
        player = server.addPlayer();
        player.addAttachment(plugin, "cliker.upgrade.use", true);
        var manager = new UpgradeManager(plugin, new UpgradeStore(db), economy);
        var cache = UpgradeManager.class.getDeclaredField("cache");
        cache.setAccessible(true);
        ((Map<UUID, Map<UpgradeType, Integer>>) cache.get(manager))
                .put(player.getUniqueId(), new EnumMap<>(Map.of(UpgradeType.FORCE, 100)));
        menu = new UpgradeMenu(plugin, manager);
        server.getPluginManager().registerEvents(menu, plugin);
    }

    @AfterEach
    void cleanup() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void drain() {
        db.submit(c -> null).join();
        server.getScheduler().performTicks(2);
    }

    @Test
    void balanceNextCostAndMaximumAreReadable() {
        menu.open(player);
        drain();
        var inventory = player.getOpenInventory().getTopInventory();
        assertTrue(inventory.getItem(10).getItemMeta().lore().toString().contains("Il manque"));
        assertTrue(
                inventory.getItem(13).getItemMeta().lore().toString().contains("NIVEAU MAXIMUM"));
        assertEquals(Material.ARROW, inventory.getItem(26).getType());
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.GEMMES_SOMBRES, 500000)
                .join();
        menu.open(player);
        drain();
        var lore =
                player.getOpenInventory()
                        .getTopInventory()
                        .getItem(10)
                        .getItemMeta()
                        .lore()
                        .toString();
        assertTrue(lore.contains("50K"));
        assertTrue(lore.contains("Gauche : +1"));
    }

    @Test
    void revokingPermissionWhileMenuIsOpenPreventsPurchase() {
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.GEMMES_SOMBRES, 500000)
                .join();
        menu.open(player);
        drain();
        player.addAttachment(plugin, "cliker.upgrade.use", false);
        var click =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        10,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        drain();
        assertTrue(click.isCancelled());
        assertEquals(500000, economy.getBalances(player.getUniqueId()).join().gemmesSombres());
    }
}
