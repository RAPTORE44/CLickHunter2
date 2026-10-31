package fr.cliker.clikerv2.rankings;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.donors.DonorPointsStore;
import fr.cliker.clikerv2.hunting.HuntUI;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

class RankingsModuleTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private RankingsModule module;
    private HuntUI ui;
    private PlayerMock player;
    private final Command command =
            new Command("top") {
                @Override
                public boolean execute(CommandSender s, String l, String[] a) {
                    return false;
                }
            };

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
        player.addAttachment(plugin, RankingsModule.USE, true);
        db = new DatabaseManager(plugin, "ranks-" + UUID.randomUUID() + ".db");
        db.initialize();
        ui = new HuntUI(plugin);
        module = new RankingsModule(plugin, db);
        module.start();
    }

    @AfterEach
    void cleanup() {
        try {
            module.shutdown();
            ui.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void drain() {
        for (int i = 0; i < 3; i++) {
            db.submit(c -> null).join();
            server.getScheduler().performTicks(2);
        }
    }

    private void click(int slot) {
        var e =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        slot,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(e);
        assertTrue(e.isCancelled());
        drain();
    }

    private String info() {
        return player.getOpenInventory().getTopInventory().getItem(15).getItemMeta().lore().stream()
                .map(
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                        .plainText()
                                ::serialize)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    @Test
    void publicDonorRankingContainsNamesButNeverPointAmounts() {
        new DonorPointsStore(db).change("Alice", 1234, null, "console").join();
        module.onCommand(player, command, "classement", new String[] {"donateurs"});
        drain();
        assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
        var meta = player.getOpenInventory().getTopInventory().getItem(19).getItemMeta();
        var plain =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertTrue(plain.serialize(meta.displayName()).contains("Alice"));
        String lore =
                meta.lore().stream()
                        .map(plain::serialize)
                        .collect(java.util.stream.Collectors.joining(" "));
        assertFalse(lore.contains("12.34"));
        assertFalse(lore.contains("1234"));
        for (int slot : new int[] {20, 21, 22, 23, 24, 25, 28, 29, 30})
            assertNotNull(player.getOpenInventory().getTopInventory().getItem(slot));
    }

    @Test
    void periodButtonsNavigateDaysMonthsAndYearsWithoutAllowingFutureDates() {
        var today = LocalDate.now(db.rankingZone());
        var format = DateTimeFormatter.ofPattern("dd/MM/uuuu");
        db.submit(
                        c -> {
                            try (var s = c.prepareStatement("UPDATE ranking_meta SET since=?")) {
                                s.setString(1, today.minusYears(3).toString());
                                s.executeUpdate();
                                return null;
                            } catch (java.sql.SQLException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        module.open(player);
        drain();
        click(9);
        assertTrue(info().contains(today.format(format)));
        assertEquals(
                Material.GRAY_DYE,
                player.getOpenInventory().getTopInventory().getItem(53).getType());
        click(45);
        assertTrue(info().contains(today.minusDays(1).format(format)));
        click(53);
        assertTrue(info().contains(today.format(format)));
        click(11);
        click(45);
        assertTrue(info().contains(today.withDayOfMonth(1).minusMonths(1).format(format)));
        click(49);
        assertTrue(info().contains(today.withDayOfMonth(1).format(format)));
        click(12);
        click(45);
        assertTrue(info().contains(today.withDayOfYear(1).minusYears(1).format(format)));
        click(13);
        assertTrue(info().contains("Depuis toujours"));
        assertEquals(
                Material.GRAY_DYE,
                player.getOpenInventory().getTopInventory().getItem(45).getType());
    }

    @Test
    void firstUntrackedPeriodsAndPermissionChangesCannotOpenInvalidMenus() {
        module.open(player);
        drain();
        click(9);
        assertEquals(
                Material.GRAY_DYE,
                player.getOpenInventory().getTopInventory().getItem(45).getType());
        var inventory = player.getOpenInventory().getTopInventory();
        player.addAttachment(plugin, RankingsModule.USE, false);
        click(10);
        assertSame(inventory, player.getOpenInventory().getTopInventory());
        module.onCommand(player, command, "top", new String[] {"pnj", "spawn"});
        assertFalse(
                new fr.cliker.clikerv2.features.Settings(plugin, "pnj/classements.yml")
                        .yaml.getBoolean("pnj.actif"));
    }

    @Test
    void closedMenusDoNotReopenWhenAnAsynchronousReadCompletes() {
        module.open(player);
        player.openInventory(Bukkit.createInventory(null, 9));
        player.closeInventory();
        drain();
        var top = player.getOpenInventory().getTopInventory();
        assertTrue(top == null || !(top.getHolder() instanceof HuntUI.View));
    }
}
