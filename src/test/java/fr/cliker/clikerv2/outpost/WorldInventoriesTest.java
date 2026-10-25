package fr.cliker.clikerv2.outpost;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class WorldInventoriesTest {
    ServerMock server;
    PlayerMock player;
    org.bukkit.World clicker, builder;
    WorldInventories profiles;
    org.bukkit.plugin.java.JavaPlugin plugin;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        clicker = server.addSimpleWorld("clicker");
        builder = server.addSimpleWorld("plots");
        player =
                new PlayerMock(server, "Builder") {
                    @Override
                    public void saveData() {}
                };
        server.addPlayer(player);
        player.teleport(clicker.getSpawnLocation());
        profiles = new WorldInventories(plugin, "plots");
        profiles.start();
    }

    @AfterEach
    void cleanup() {
        profiles.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void inventoryArmorOffhandAndXpAreRestoredAndBuilderBlocksStayInBuilder() {
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        player.getInventory().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        player.getInventory().setItemInOffHand(new ItemStack(Material.TORCH));
        player.setLevel(50);
        player.setExp(.5f);
        player.setTotalExperience(5000);
        player.teleport(builder.getSpawnLocation());
        profiles.reconcile(player);
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());
        assertEquals(0, player.getLevel());
        player.getInventory().setItem(0, new ItemStack(Material.STONE, 64));
        player.setLevel(3);
        player.teleport(clicker.getSpawnLocation());
        profiles.reconcile(player);
        assertEquals(Material.DIAMOND_SWORD, player.getInventory().getItem(0).getType());
        assertEquals(Material.CARVED_PUMPKIN, player.getInventory().getHelmet().getType());
        assertEquals(Material.TORCH, player.getInventory().getItemInOffHand().getType());
        assertEquals(50, player.getLevel());
        assertEquals(.5f, player.getExp());
        player.teleport(builder.getSpawnLocation());
        profiles.reconcile(player);
        assertEquals(Material.STONE, player.getInventory().getItem(0).getType());
        assertEquals(64, player.getInventory().getItem(0).getAmount());
        assertEquals(3, player.getLevel());
    }

    @Test
    void exchangeCommandsAndEnderChestAreBlockedOnlyInsideBuilder() {
        assertTrue(WorldInventories.commandAllowed(player, "ah"));
        player.teleport(builder.getSpawnLocation());
        profiles.reconcile(player);
        for (String command :
                new String[] {"ah", "sell", "sac", "/essentials:trade Bob", "prestige"})
            assertFalse(WorldInventories.commandAllowed(player, command), command);
        assertTrue(WorldInventories.commandAllowed(player, "/spawn"));
        assertTrue(WorldInventories.commandAllowed(player, "/is pnj deplacer"));
        player.openInventory(player.getEnderChest());
        var top = player.getOpenInventory().getTopInventory();
        assertTrue(
                top == null
                        || top.getType() != org.bukkit.event.inventory.InventoryType.ENDER_CHEST);
    }

    @Test
    void droppedItemsCannotBeTeleportedOut() {
        Item item =
                builder.dropItem(builder.getSpawnLocation(), new ItemStack(Material.DIAMOND_BLOCK));
        var event = new EntityTeleportEvent(item, item.getLocation(), clicker.getSpawnLocation());
        profiles.entityTeleport(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void corruptTargetNeverReplacesOriginalInventory() {
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        player.getPersistentDataContainer()
                .set(
                        new NamespacedKey(plugin, "inventory_builder"),
                        org.bukkit.persistence.PersistentDataType.STRING,
                        "items: invalid");
        player.teleport(builder.getSpawnLocation());
        assertEquals(Material.DIAMOND_SWORD, player.getInventory().getItem(0).getType());
        assertFalse(WorldInventories.readyForBuilder(player));
    }
}
