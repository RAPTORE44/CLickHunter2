package fr.cliker.clikerv2.hunting;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class HuntUITest {
    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void rightClickInvokesDedicatedPinActionWhileLeftClickKeepsPrimaryAction() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var player = server.addPlayer();
        int[] called = {-1};
        var ui = new HuntUI(plugin);
        var view =
                HuntUI.view(
                        player,
                        "test",
                        27,
                        slot -> called[0] = slot,
                        slot -> called[0] = slot + 100);
        player.openInventory(view.inventory);
        var right =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        3,
                        ClickType.RIGHT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(right);
        server.getScheduler().performOneTick();
        assertTrue(right.isCancelled());
        assertEquals(103, called[0]);
        var left =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        4,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(left);
        server.getScheduler().performOneTick();
        assertEquals(4, called[0]);
        ui.shutdown();
    }
}
