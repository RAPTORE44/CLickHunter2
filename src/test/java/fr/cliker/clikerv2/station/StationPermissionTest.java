package fr.cliker.clikerv2.station;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.gear.*;

import org.bukkit.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class StationPermissionTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private StationManager station;
    private org.mockbukkit.mockbukkit.entity.PlayerMock player;
    private org.bukkit.block.Block block;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "stations-" + UUID.randomUUID() + ".db");
        db.initialize();
        TierGear.init(plugin);
        ItemMods.init(plugin);
        var economy = new EconomyService(db, Map.of());
        station =
                new StationManager(
                        plugin,
                        new StationStore(db),
                        new EnchantMenu(plugin, economy),
                        new ReforgeMenu(plugin, economy),
                        new RuneMenu(plugin, economy));
        player = server.addPlayer();
        block = player.getWorld().getBlockAt(1, 65, 1);
        station.addStation(StationType.ENCHANT, block.getLocation());
        player.getInventory().setItemInMainHand(TierGear.createSword(MobTier.VACHE));
        server.getPluginManager().registerEvents(station, plugin);
    }

    @AfterEach
    void cleanup() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private PlayerInteractEvent click(EquipmentSlot hand) {
        return new PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(),
                block,
                org.bukkit.block.BlockFace.UP,
                hand);
    }

    @Test
    void physicalStationCannotBypassCommandPermission() {
        player.addAttachment(plugin, "cliker.gear.use", false);
        station.onInteract(click(EquipmentSlot.HAND));
        server.getScheduler().performOneTick();
        assertNotNull(player.getInventory().getItemInMainHand());
        assertTrue(player.nextMessage().contains("station"));
    }

    @Test
    void offhandEventCannotTakeMainHandItem() {
        player.addAttachment(plugin, "cliker.gear.use", true);
        station.onInteract(click(EquipmentSlot.OFF_HAND));
        assertFalse(player.getInventory().getItemInMainHand().getType().isAir());
    }

    @Test
    void cancelledInteractionCannotRemoveEquipment() {
        var event = click(EquipmentSlot.HAND);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        assertFalse(player.getInventory().getItemInMainHand().getType().isAir());
    }

    @Test
    void repeatedStationClicksOpenOnceAndOnlyTransferOneEquipmentItem() {
        player.addAttachment(plugin, "cliker.gear.use", true);
        var equipment = player.getInventory().getItemInMainHand().clone();
        for (int i = 0; i < 4; i++) station.onInteract(click(EquipmentSlot.HAND));
        assertTrue(player.getInventory().getItemInMainHand().isSimilar(equipment));
        server.getScheduler().performOneTick();
        var inventory = player.getOpenInventory().getTopInventory();
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());
        assertTrue(inventory.getItem(4).isSimilar(equipment));
        for (int i = 0; i < 4; i++) station.onInteract(click(EquipmentSlot.HAND));
        server.getScheduler().performOneTick();
        assertSame(inventory, player.getOpenInventory().getTopInventory());
        assertEquals(1, inventory.getItem(4).getAmount());
    }
}
