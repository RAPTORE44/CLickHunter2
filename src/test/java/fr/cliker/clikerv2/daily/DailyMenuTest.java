package fr.cliker.clikerv2.daily;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

class DailyMenuTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private PlayerMock player;
    private DailyModule module;
    private final AtomicInteger rewards = new AtomicInteger();

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        Files.createDirectories(plugin.getDataFolder().toPath());
        try (var resource = getClass().getResourceAsStream("/daily.yml")) {
            Files.copy(
                    resource,
                    fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "daily.yml").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        // Chaque test utilise une base indépendante, comme une installation fraîche.
        Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("data/daily.db"));
        module = new DailyModule(plugin);
        player = server.addPlayer();
        player.addAttachment(plugin, DailyModule.USE_PERMISSION, true);
        server.getPluginManager().registerEvents(new DailyMenu(module), plugin);
        server.getCommandMap()
                .register(
                        "test",
                        new Command("testdaily") {
                            @Override
                            public boolean execute(
                                    CommandSender sender, String label, String[] args) {
                                assertSame(server.getConsoleSender(), sender);
                                assertEquals(player.getName(), args[0]);
                                assertEquals("1", args[1]);
                                rewards.incrementAndGet();
                                return true;
                            }
                        });
    }

    @AfterEach
    void cleanup() {
        try {
            if (module != null) module.shutdown();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void await(BooleanSupplier done) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
            server.getScheduler().performOneTick();
        }
        assertTrue(done.getAsBoolean());
    }

    private void open() throws Exception {
        module.open(player);
        await(
                () ->
                        player.getOpenInventory().getTopInventory() != null
                                && player.getOpenInventory().getTopInventory().getSize() == 54);
    }

    private InventoryClickEvent click(int slot, ClickType type, InventoryAction action) {
        var event =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        slot,
                        type,
                        action);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void completeFrenchMenuAndTestRewardsAreConfigured() throws Exception {
        open();
        var inventory = player.getOpenInventory().getTopInventory();
        assertEquals(Material.CYAN_STAINED_GLASS_PANE, inventory.getItem(0).getType());
        assertEquals(Material.BLUE_STAINED_GLASS_PANE, inventory.getItem(9).getType());
        assertEquals(Material.BOOK, inventory.getItem(4).getType());
        assertEquals(Material.CHEST, inventory.getItem(DailyMenu.slot(1)).getType());
        for (int day = 1; day <= 31; day++) {
            int currentDay = day;
            var item = inventory.getItem(DailyMenu.slot(currentDay));
            var lore =
                    item.getItemMeta().lore().stream()
                            .map(PlainTextComponentSerializer.plainText()::serialize)
                            .toList();
            assertFalse(lore.isEmpty());
            assertFalse(module.settings().commands(currentDay).isEmpty());
            if (currentDay <= 28)
                assertTrue(
                        module.settings()
                                .commands(currentDay)
                                .getFirst()
                                .startsWith("coffre {joueur} "));
            else
                assertTrue(
                        module.settings()
                                .commands(currentDay)
                                .getFirst()
                                .startsWith("tellraw {joueur}"));
            if (currentDay > 1)
                assertEquals(
                        currentDay == 1 ? Material.CHEST : Material.TRIPWIRE_HOOK, item.getType());
        }
    }

    @Test
    void doubleClickExecutesConsoleRewardOnceAndPersistsAfterReopen() throws Exception {
        module.settings()
                .config()
                .set("recompenses.1.commandes", java.util.List.of("testdaily {joueur} {jour}"));
        open();
        for (int i = 0; i < 12; i++)
            assertTrue(
                    click(DailyMenu.slot(1), ClickType.LEFT, InventoryAction.PICKUP_ALL)
                            .isCancelled());
        await(() -> rewards.get() == 1);
        assertEquals(
                Material.MINECART,
                player.getOpenInventory().getTopInventory().getItem(DailyMenu.slot(1)).getType());
        player.closeInventory();
        open();
        click(DailyMenu.slot(1), ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getScheduler().performOneTick();
        assertEquals(1, rewards.get());
    }

    @Test
    void lockedDaysShiftClicksNumberKeysAndDragsCannotStealOrReward() throws Exception {
        open();
        assertTrue(
                click(DailyMenu.slot(2), ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        assertTrue(
                click(54, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY)
                        .isCancelled());
        assertTrue(
                click(4, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled());
        var hotbar =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        4,
                        ClickType.NUMBER_KEY,
                        InventoryAction.HOTBAR_SWAP,
                        0);
        server.getPluginManager().callEvent(hotbar);
        assertTrue(hotbar.isCancelled());
        var drag =
                new InventoryDragEvent(
                        player.getOpenInventory(),
                        new ItemStack(Material.DIAMOND),
                        new ItemStack(Material.DIAMOND, 2),
                        false,
                        Map.of(4, new ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(drag);
        assertTrue(drag.isCancelled());
        assertEquals(0, rewards.get());
    }

    @Test
    void midnightUpdatesAnOpenMenuWithoutRewritingItsBackground() throws Exception {
        var clock = DailyModule.class.getDeclaredField("clock");
        clock.setAccessible(true);
        var date = java.time.LocalDate.of(2026, 10, 24);
        var zone = java.time.ZoneId.of("Europe/Paris");
        clock.set(
                module, java.time.Clock.fixed(date.atTime(23, 59).atZone(zone).toInstant(), zone));
        var menuField = DailyModule.class.getDeclaredField("menu");
        menuField.setAccessible(true);
        var calendar = (DailyMenu) menuField.get(module);
        calendar.open(player, new DailyProgress(date, 1));
        var top = player.getOpenInventory().getTopInventory();
        ItemStack background = top.getItem(0);
        var probe = background.getItemMeta();
        probe.displayName(net.kyori.adventure.text.Component.text("Décoration déjà posée"));
        background.setItemMeta(probe);
        top.setItem(0, background);
        ItemStack claimed = top.getItem(DailyMenu.slot(1));
        calendar.refreshOpenMenus();
        assertEquals(background, top.getItem(0));
        assertEquals(claimed, top.getItem(DailyMenu.slot(1)));
        clock.set(
                module,
                java.time.Clock.fixed(
                        date.plusDays(1).atTime(0, 1).atZone(zone).toInstant(), zone));
        calendar.refreshOpenMenus();
        assertEquals(background, top.getItem(0));
        assertTrue(top.getItem(DailyMenu.slot(2)).getItemMeta().getEnchantmentGlintOverride());
    }

    @Test
    void npcManagementRequiresAdminAndDoesNotBreakWithoutCitizens() {
        player.addAttachment(plugin, DailyModule.ADMIN_PERMISSION, false);
        assertTrue(
                module.onCommand(
                        player,
                        new Command("daily") {
                            @Override
                            public boolean execute(CommandSender s, String l, String[] a) {
                                return false;
                            }
                        },
                        "daily",
                        new String[] {"pnj", "spawn"}));
        assertFalse(module.settings().config().getBoolean("pnj.actif"));
        player.addAttachment(plugin, DailyModule.ADMIN_PERMISSION, true);
        assertTrue(
                module.onCommand(
                        player,
                        new Command("daily") {
                            @Override
                            public boolean execute(CommandSender s, String l, String[] a) {
                                return false;
                            }
                        },
                        "daily",
                        new String[] {"pnj", "spawn"}));
        assertFalse(module.settings().config().getBoolean("pnj.actif"));
    }
}
