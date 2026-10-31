package fr.cliker.clikerv2.features;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class MenuActionReplacementTest {
    @Test
    void replacingAButtonWithAnInactiveItemAlsoRemovesItsOldAction() {
        var server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            var player = server.addPlayer();
            var menus = new Menus(plugin);
            var view = menus.create(player, 9, "Choix");
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            view.put(4, Menus.item(Material.EMERALD, "Acheter"), (p, r) -> calls.incrementAndGet());
            view.put(4, Menus.item(Material.BARRIER, "Indisponible"), null);
            view.show(player);
            var event =
                    new InventoryClickEvent(
                            player.getOpenInventory(),
                            InventoryType.SlotType.CONTAINER,
                            4,
                            ClickType.LEFT,
                            InventoryAction.PICKUP_ALL);
            server.getPluginManager().callEvent(event);
            server.getScheduler().performTicks(2);
            assertTrue(event.isCancelled());
            assertEquals(0, calls.get());
        } finally {
            MockBukkit.unmock();
        }
    }
}
