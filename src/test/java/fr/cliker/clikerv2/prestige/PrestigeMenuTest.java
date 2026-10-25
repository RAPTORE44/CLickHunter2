package fr.cliker.clikerv2.prestige;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.mobs.MobKillStore;

import org.bukkit.Material;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

class PrestigeMenuTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private PlayerMock player;
    private DatabaseManager db;
    private PrestigeStore store;
    private MobKillStore kills;
    private PrestigeManager manager;
    private PrestigeMenu menu;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
        player.addAttachment(plugin, "cliker.prestige.use", true);
        db = new DatabaseManager(plugin, "prestige-menu-" + UUID.randomUUID() + ".db");
        db.initialize();
        store = new PrestigeStore(db);
        kills = new MobKillStore(db);
        manager = new PrestigeManager(plugin, store, kills);
        manager.loadOnlinePlayers();
        drain();
        menu = new PrestigeMenu(plugin, manager, kills, null);
        kills.add(player.getUniqueId(), 100000).join();
        player.setLevel(200);
        menu.open(player);
        drain();
    }

    private void drain() throws Exception {
        for (int i = 0; i < 6; i++) {
            db.submit(c -> null).join();
            server.getScheduler().performOneTick();
        }
    }

    private void click(int slot, ClickType type) {
        var event =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        slot,
                        type,
                        InventoryAction.PICKUP_ALL);
        menu.onClick(event);
        assertTrue(event.isCancelled());
    }

    @AfterEach
    void cleanup() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void firstClickOnlyPreparesAndSecondConfirmsOnePrestige() throws Exception {
        click(13, ClickType.LEFT);
        drain();
        assertEquals(0, store.getLevel(player.getUniqueId()).join());
        assertEquals(
                Material.LIME_DYE,
                player.getOpenInventory().getTopInventory().getItem(13).getType());
        click(13, ClickType.LEFT);
        drain();
        assertEquals(1, store.getLevel(player.getUniqueId()).join());
    }

    @Test
    void bottomInventoryShiftAndPermissionRevocationNeverConfirm() throws Exception {
        int bottomSlot = player.getOpenInventory().getTopInventory().getSize() + 13;
        click(bottomSlot, ClickType.LEFT);
        click(13, ClickType.SHIFT_LEFT);
        drain();
        assertEquals(
                Material.ORANGE_DYE,
                player.getOpenInventory().getTopInventory().getItem(13).getType());
        click(13, ClickType.LEFT);
        player.addAttachment(plugin, "cliker.prestige.use", false);
        click(13, ClickType.LEFT);
        drain();
        assertEquals(0, store.getLevel(player.getUniqueId()).join());
    }

    @Test
    void closingAndReopeningDiscardsConfirmation() throws Exception {
        click(13, ClickType.LEFT);
        player.closeInventory();
        menu.open(player);
        drain();
        click(13, ClickType.LEFT);
        drain();
        assertEquals(0, store.getLevel(player.getUniqueId()).join());
    }
}
