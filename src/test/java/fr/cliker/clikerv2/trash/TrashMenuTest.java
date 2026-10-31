package fr.cliker.clikerv2.trash;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class TrashMenuTest {
    ServerMock server;
    org.mockbukkit.mockbukkit.entity.PlayerMock player;
    TrashMenu menu;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        org.bukkit.configuration.serialization.ConfigurationSerialization.registerClass(
                org.mockbukkit.mockbukkit.inventory.ItemStackMock.class);
        menu = new TrashMenu(plugin, p -> {});
        menu.start();
        player = server.addPlayer();
        player.setOp(true);
    }

    @AfterEach
    void end() {
        try {
            if (menu != null) menu.shutdown();
        } finally {
            MockBukkit.unmock();
        }
    }

    void click(int slot, ClickType type) {
        var e =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        slot,
                        type,
                        InventoryAction.NOTHING) {
                    @Override
                    public ItemStack getCurrentItem() {
                        return slot < 54
                                ? player.getOpenInventory().getTopInventory().getItem(slot)
                                : player.getInventory()
                                        .getItem(player.getOpenInventory().convertSlot(slot));
                    }

                    @Override
                    public void setCurrentItem(ItemStack item) {
                        if (slot < 54)
                            player.getOpenInventory().getTopInventory().setItem(slot, item);
                        else
                            player.getInventory()
                                    .setItem(player.getOpenInventory().convertSlot(slot), item);
                    }
                };
        server.getPluginManager().callEvent(e);
        assertTrue(e.isCancelled());
    }

    @Test
    void oneOrStackStaysRecoverableUntilClose() {
        player.getInventory().setItem(9, new ItemStack(Material.COBBLESTONE, 20));
        menu.open(player);
        click(54, ClickType.LEFT);
        assertEquals(19, player.getInventory().getItem(9).getAmount());
        assertEquals(1, player.getOpenInventory().getTopInventory().getItem(0).getAmount());
        click(54, ClickType.RIGHT);
        assertNull(player.getInventory().getItem(9));
        assertEquals(20, player.getOpenInventory().getTopInventory().getItem(0).getAmount());
        click(0, ClickType.LEFT);
        assertEquals(19, player.getOpenInventory().getTopInventory().getItem(0).getAmount());
        player.closeInventory(InventoryCloseEvent.Reason.PLAYER);
        assertEquals(
                1,
                player.getInventory().all(Material.COBBLESTONE).values().stream()
                        .mapToInt(ItemStack::getAmount)
                        .sum());
        assertTrue(menu.recover(player));
        assertEquals(
                1,
                player.getInventory().all(Material.COBBLESTONE).values().stream()
                        .mapToInt(ItemStack::getAmount)
                        .sum());
    }

    @Test
    void shutdownRestoresStagedItemsExactlyOnce() {
        player.getInventory().setItem(9, new ItemStack(Material.DIRT, 32));
        menu.open(player);
        click(54, ClickType.RIGHT);
        menu.shutdown();
        assertTrue(menu.recover(player));
        assertTrue(menu.recover(player));
        assertEquals(
                32,
                player.getInventory().all(Material.DIRT).values().stream()
                        .mapToInt(ItemStack::getAmount)
                        .sum());
    }

    @Test
    void shiftAndNumberKeysCannotBypassTransferRules() {
        player.getInventory().setItem(9, new ItemStack(Material.STONE, 64));
        menu.open(player);
        click(54, ClickType.SHIFT_LEFT);
        click(54, ClickType.NUMBER_KEY);
        assertEquals(64, player.getInventory().getItem(9).getAmount());
        assertTrue(player.getOpenInventory().getTopInventory().isEmpty());
    }
}
