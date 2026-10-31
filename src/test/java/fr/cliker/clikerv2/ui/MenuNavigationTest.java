package fr.cliker.clikerv2.ui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.hunting.HuntUI;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.concurrent.atomic.AtomicInteger;

class MenuNavigationTest {
    @Test
    void backButtonSwitchesDirectlyWithoutClosingBeforeTheMenuCommand() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var ui = new HuntUI(plugin);
        try {
            var player = server.addPlayer();
            new MenuNavigation(plugin);
            var old = HuntUI.view(player, "Ancien menu", 27, slot -> {}).getInventory();
            MenuNavigation.back(plugin, old, 26);
            player.openInventory(old);
            var count = new AtomicInteger();
            server.getCommandMap()
                    .register(
                            "test",
                            new Command("menu") {
                                @Override
                                public boolean execute(
                                        CommandSender sender, String label, String[] args) {
                                    assertSame(old, player.getOpenInventory().getTopInventory());
                                    count.incrementAndGet();
                                    player.openInventory(Bukkit.createInventory(null, 54));
                                    return true;
                                }
                            });
            for (int i = 0; i < 4; i++)
                server.getPluginManager()
                        .callEvent(
                                new InventoryClickEvent(
                                        player.getOpenInventory(),
                                        InventoryType.SlotType.CONTAINER,
                                        26,
                                        ClickType.LEFT,
                                        InventoryAction.PICKUP_ALL));
            server.getScheduler().performOneTick();
            assertEquals(1, count.get());
            assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
        } finally {
            ui.shutdown();
            MockBukkit.unmock();
        }
    }
}
