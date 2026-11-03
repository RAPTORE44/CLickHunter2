package fr.cliker.clikerv2.achievements;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.UUID;

class AchievementModuleTest {
    ServerMock server;
    org.bukkit.plugin.java.JavaPlugin plugin;
    DatabaseManager db;
    AchievementModule module;
    org.mockbukkit.mockbukkit.entity.PlayerMock player;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("ClikerV2");
        var constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        server.getCommandMap().register("clikerv2", constructor.newInstance("succes", plugin));
        db = new DatabaseManager(plugin, "gui-achievements-" + UUID.randomUUID() + ".db");
        db.initialize();
        module = new AchievementModule(plugin, db, null);
        module.start();
        player = server.addPlayer();
        player.addAttachment(plugin, "cliker.succes", true);
    }

    @AfterEach
    void close() {
        try {
            module.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    void open(String... args) throws Exception {
        module.onCommand(player, plugin.getCommand("succes"), "succes", args);
        for (int i = 0;
                i < 40
                        && (player.getOpenInventory().getTopInventory() == null
                                || player.getOpenInventory().getTopInventory().getSize() != 54);
                i++) {
            db.submit(c -> null).join();
            server.getScheduler().performOneTick();
            Thread.sleep(5);
        }
        assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
    }

    @Test
    void firstFilteredOpenPaginatesFiveHundredAndNeverLetsPlayerTakeIcons() throws Exception {
        open("a-faire");
        var inventory = player.getOpenInventory().getTopInventory();
        assertNotNull(inventory.getItem(53));
        var click =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        10,
                        ClickType.SHIFT_LEFT,
                        InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(click);
        assertTrue(click.isCancelled());
        assertEquals(0, player.getInventory().all(Material.GRAY_DYE).size());
        var next =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        53,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(next);
        server.getScheduler().performTicks(2);
        assertNotSame(inventory, player.getOpenInventory().getTopInventory());
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(45));
    }

    @Test
    void categoriesHaveVisibleProgressAndPermissionIsEnforced() throws Exception {
        open();
        assertEquals(
                Material.BOOK, player.getOpenInventory().getTopInventory().getItem(40).getType());
        player.closeInventory();
        player.addAttachment(plugin, "cliker.succes", false);
        module.onCommand(player, plugin.getCommand("succes"), "succes", new String[0]);
        assertTrue(
                player.getOpenInventory().getTopInventory() == null
                        || player.getOpenInventory().getTopInventory().getSize() != 54);
    }
}
