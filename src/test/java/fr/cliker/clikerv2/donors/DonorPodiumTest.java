package fr.cliker.clikerv2.donors;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.hunting.HuntUI;

import org.bukkit.command.*;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class DonorPodiumTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DonorPodium podium;
    private DatabaseManager db;
    private HuntUI ui;
    private final Command command =
            new Command("donateurs") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return false;
                }
            };

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "donors-" + java.util.UUID.randomUUID() + ".db");
        db.initialize();
        ui = new HuntUI(plugin);
        podium = new DonorPodium(plugin, db);
        podium.start();
        drain();
    }

    private void drain() {
        for (int i = 0; i < 5; i++) {
            db.submit(c -> null).join();
            server.getScheduler().performTicks(2);
        }
    }

    private void command(CommandSender sender, String... args) {
        podium.onCommand(sender, command, "donateurs", args);
        drain();
    }

    @AfterEach
    void cleanup() {
        try {
            podium.shutdown();
            ui.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void pointsAutomaticallyUpdateSkinsAndNamesWithoutMovingPodiumPositions() {
        for (int rank = 1; rank <= 3; rank++) {
            var settings = new Settings(plugin, "pnj/donateurs-" + rank + ".yml");
            assertEquals("RAPTORE44", settings.yaml.getString("pnj.skin"));
            assertEquals(2.0, settings.yaml.getDouble("pnj.echelle"));
            assertFalse(settings.yaml.getBoolean("pnj.actif"));
        }
        var settings = new Settings(plugin, "pnj/donateurs-1.yml");
        settings.yaml.set("pnj.position.x", 42);
        settings.save();
        podium.shutdown();
        podium = new DonorPodium(plugin, db);
        podium.start();
        drain();
        command(server.getConsoleSender(), "add", "Alice", "10", "receipt1");
        command(server.getConsoleSender(), "add", "Bob", "20");
        settings = new Settings(plugin, "pnj/donateurs-1.yml");
        assertEquals("Bob", settings.yaml.getString("joueur"));
        assertEquals("Bob", settings.yaml.getString("pnj.skin"));
        assertEquals("&fBob", settings.yaml.getString("pnj.sous-titre"));
        assertEquals(42, settings.yaml.getInt("pnj.position.x"));
        command(server.getConsoleSender(), "retirer", "Bob", "15");
        assertEquals("Alice", new Settings(plugin, "pnj/donateurs-1.yml").yaml.getString("joueur"));
        assertEquals("Bob", new Settings(plugin, "pnj/donateurs-2.yml").yaml.getString("joueur"));
        assertEquals(
                "RAPTORE44", new Settings(plugin, "pnj/donateurs-3.yml").yaml.getString("joueur"));
        podium.shutdown();
        podium = new DonorPodium(plugin, db);
        podium.start();
        drain();
        assertEquals(
                "Alice", new Settings(plugin, "pnj/donateurs-1.yml").yaml.getString("pnj.skin"));
    }

    @Test
    void unauthorizedAndInvalidCommandsCannotChangePointsOrThePodium() {
        var player = server.addPlayer();
        command(player, "add", "Other", "100");
        command(server.getConsoleSender(), "add", "bad-name!", "100");
        command(server.getConsoleSender(), "add", "Other", "-10");
        command(server.getConsoleSender(), "set", "1", "Other");
        assertEquals(0, new DonorPointsStore(db).find("Other").join().units());
        assertEquals(
                "RAPTORE44", new Settings(plugin, "pnj/donateurs-1.yml").yaml.getString("joueur"));
        assertTrue(podium.onTabComplete(player, command, "donateurs", new String[] {""}).isEmpty());
    }

    @Test
    void administratorMenuIsProtectedAndAdjustsPointsWhilePermissionsAreRechecked() {
        command(server.getConsoleSender(), "add", "OfflineBuyer", "9.99");
        var player = server.addPlayer();
        player.setOp(true);
        command(player);
        assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
        var click =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        0,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        assertTrue(click.isCancelled());
        drain();
        assertEquals(27, player.getOpenInventory().getTopInventory().getSize());
        click =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        10,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        drain();
        assertEquals(1099, new DonorPointsStore(db).find("OfflineBuyer").join().units());
        player.setOp(false);
        click =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        10,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        drain();
        assertEquals(1099, new DonorPointsStore(db).find("OfflineBuyer").join().units());
    }

    @Test
    void closingAQueuedMenuDoesNotReopenIt() {
        var player = server.addPlayer();
        player.setOp(true);
        podium.onCommand(player, command, "donateurs", new String[0]);
        player.openInventory(org.bukkit.Bukkit.createInventory(null, 9));
        player.closeInventory();
        drain();
        var top = player.getOpenInventory().getTopInventory();
        assertTrue(top == null || !(top.getHolder() instanceof HuntUI.View));
    }
}
