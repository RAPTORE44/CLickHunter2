package fr.cliker.clikerv2.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.concurrent.atomic.AtomicInteger;

class ViewRequestsTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private org.mockbukkit.mockbukkit.entity.PlayerMock player;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void completedRequestCannotReopenAnOldMenuAfterNavigation() {
        var requests = new ViewRequests(plugin);
        var old = requests.begin(player);
        var current = requests.begin(player);
        assertFalse(requests.complete(player, old));
        assertTrue(requests.complete(player, current));
        var moved = requests.begin(player);
        player.openInventory(Bukkit.createInventory(null, 27));
        assertFalse(requests.complete(player, moved));
    }

    @Test
    void returnButtonRunsOnceAfterInventoryEvent() {
        new MenuNavigation(plugin);
        var commands = new AtomicInteger();
        server.getCommandMap()
                .register(
                        "test",
                        new Command("menu") {
                            public boolean execute(CommandSender s, String l, String[] a) {
                                commands.incrementAndGet();
                                return true;
                            }
                        });
        var holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, 27);
        MenuNavigation.back(plugin, holder.inventory, 26);
        player.openInventory(holder.inventory);
        for (int i = 0; i < 10; i++) {
            var e =
                    new InventoryClickEvent(
                            player.getOpenInventory(),
                            InventoryType.SlotType.CONTAINER,
                            26,
                            ClickType.LEFT,
                            InventoryAction.PICKUP_ALL);
            server.getPluginManager().callEvent(e);
            assertTrue(e.isCancelled());
        }
        assertEquals(0, commands.get());
        server.getScheduler().performOneTick();
        assertEquals(1, commands.get());
    }

    private static final class Holder implements InventoryHolder {
        Inventory inventory;

        public Inventory getInventory() {
            return inventory;
        }
    }
}
