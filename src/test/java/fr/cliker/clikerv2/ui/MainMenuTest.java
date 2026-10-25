package fr.cliker.clikerv2.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class MainMenuTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private PlayerMock player;
    private MainMenu menu;
    private final AtomicInteger commands = new AtomicInteger();
    private final Command entry =
            new Command("menu") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return false;
                }
            };

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
        player.addAttachment(plugin, "cliker.menu", true);
        player.addAttachment(plugin, "cliker.daily.use", true);
        var path = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "gui/menu.yml").toPath();
        Files.createDirectories(path.getParent());
        try (var input = getClass().getResourceAsStream("/gui/menu.yml")) {
            Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
        }
        menu = new MainMenu(plugin);
        server.getPluginManager().registerEvents(menu, plugin);
        server.getCommandMap()
                .register(
                        "test",
                        new Command("daily") {
                            @Override
                            public boolean execute(
                                    CommandSender sender, String label, String[] args) {
                                commands.incrementAndGet();
                                return true;
                            }
                        });
        menu.onCommand(player, entry, "menu", new String[0]);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    private InventoryClickEvent click(int slot) {
        var event =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        slot,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void mainMenuCannotBypassOutpostInventoryIsolationToOpenAuction() {
        player.addAttachment(plugin, "cliker.ah.use", true);
        server.getCommandMap()
                .register(
                        "test",
                        new Command("ah") {
                            @Override
                            public boolean execute(
                                    CommandSender sender, String label, String[] args) {
                                commands.incrementAndGet();
                                return true;
                            }
                        });
        server.getServicesManager()
                .register(
                        fr.cliker.clikerv2.outpost.WorldInventories.class,
                        new fr.cliker.clikerv2.outpost.WorldInventories(
                                plugin, player.getWorld().getName()),
                        plugin,
                        org.bukkit.plugin.ServicePriority.Normal);
        click(15);
        server.getScheduler().performOneTick();
        assertEquals(
                0,
                commands.get(),
                "The menu must not execute the auction command in an isolated inventory");
    }

    @Test
    void readableNavigationAndNoRepeatedCommandOnDoubleClick() {
        assertEquals(36, player.getOpenInventory().getTopInventory().getSize());
        assertEquals(
                Material.CHEST, player.getOpenInventory().getTopInventory().getItem(10).getType());
        for (int i = 0; i < 10; i++) assertTrue(click(10).isCancelled());
        server.getScheduler().performOneTick();
        assertEquals(1, commands.get());
    }

    @Test
    void lockedEntryAndPermissionRevocationAreEnforced() {
        player.addAttachment(plugin, "cliker.daily.use", false);
        menu.onCommand(player, entry, "menu", new String[0]);
        assertEquals(
                Material.BARRIER,
                player.getOpenInventory().getTopInventory().getItem(10).getType());
        click(10);
        server.getScheduler().performOneTick();
        assertEquals(0, commands.get());
        player.addAttachment(plugin, "cliker.daily.use", true);
        menu.onCommand(player, entry, "menu", new String[0]);
        click(10);
        player.addAttachment(plugin, "cliker.daily.use", false);
        server.getScheduler().performOneTick();
        assertEquals(0, commands.get());
    }

    @Test
    void closeAndInventoryProtections() {
        var shift =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        36,
                        ClickType.SHIFT_LEFT,
                        InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(shift);
        assertTrue(shift.isCancelled());
        var drag =
                new InventoryDragEvent(
                        player.getOpenInventory(),
                        new ItemStack(Material.DIAMOND),
                        new ItemStack(Material.DIAMOND, 2),
                        false,
                        Map.of(10, new ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(drag);
        assertTrue(drag.isCancelled());
        click(31);
        server.getScheduler().performOneTick();
        var top = player.getOpenInventory().getTopInventory();
        assertTrue(top == null || top.getSize() != 36);
        assertEquals(0, commands.get());
    }

    @Test
    void shutdownClosesMenuBeforeRemovingItsProtections() {
        menu.shutdown();
        var top = player.getOpenInventory().getTopInventory();
        assertTrue(top == null || top.getSize() != 36);
        assertEquals(0, commands.get());
    }

    @Test
    void duplicatedSlotsAreRejected() throws Exception {
        var file =
                fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "gui/menu.yml")
                        .toPath()
                        .toFile();
        var config = YamlConfiguration.loadConfiguration(file);
        config.set("boutons.sacs.slot", 10);
        config.save(file);
        assertThrows(IllegalArgumentException.class, () -> new MainMenu(plugin));
    }
}
