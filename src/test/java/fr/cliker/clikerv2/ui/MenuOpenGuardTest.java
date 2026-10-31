package fr.cliker.clikerv2.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.atomic.*;

class MenuOpenGuardTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private PlayerMock player;
    private MenuOpenGuard guard;
    private AtomicLong clock;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
        clock = new AtomicLong();
        guard = new MenuOpenGuard(plugin, clock::get);
    }

    @AfterEach
    void cleanup() {
        try {
            guard.shutdown();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void repeatedNpcAndBlockRequestsOpenOnlyOneInventoryAndIgnoreLateWorldClicks() {
        var openings = new AtomicInteger();
        var closings = new AtomicInteger();
        var inventory = Bukkit.createInventory(null, 54);
        server.getPluginManager()
                .registerEvents(
                        new Listener() {
                            @EventHandler
                            public void open(InventoryOpenEvent e) {
                                if (e.getInventory() == inventory) openings.incrementAndGet();
                            }

                            @EventHandler
                            public void close(InventoryCloseEvent e) {
                                if (e.getInventory() == inventory) closings.incrementAndGet();
                            }
                        },
                        plugin);
        for (int i = 0; i < 4; i++)
            MenuOpenGuard.request(plugin, player, () -> player.openInventory(inventory));
        server.getScheduler().performOneTick();
        assertEquals(1, openings.get());
        assertEquals(0, closings.get());
        clock.set(2_000_000_000L);
        MenuOpenGuard.request(plugin, player, () -> player.openInventory(inventory));
        server.getScheduler().performOneTick();
        assertEquals(1, openings.get());
        assertEquals(0, closings.get());
    }

    @Test
    void requestsAreIndependentAndTheCooldownDoesNotLockAPlayer() {
        var count = new AtomicInteger();
        var other = server.addPlayer();
        guard.submit(player, count::incrementAndGet);
        guard.submit(other, count::incrementAndGet);
        server.getScheduler().performOneTick();
        assertEquals(2, count.get());
        clock.set(100_000_000L);
        guard.submit(player, count::incrementAndGet);
        server.getScheduler().performOneTick();
        assertEquals(2, count.get());
        clock.set(250_000_001L);
        guard.submit(player, count::incrementAndGet);
        server.getScheduler().performOneTick();
        assertEquals(3, count.get());
    }

    @Test
    void changedViewOrWorldCancelsTheOldQueuedOpen() {
        var count = new AtomicInteger();
        guard.submit(player, count::incrementAndGet);
        player.openInventory(Bukkit.createInventory(null, 9));
        player.closeInventory();
        server.getScheduler().performOneTick();
        assertEquals(0, count.get());
        clock.set(1_000_000_000L);
        guard.submit(player, count::incrementAndGet);
        player.teleport(server.addSimpleWorld("other").getSpawnLocation());
        server.getScheduler().performOneTick();
        assertEquals(0, count.get());
    }

    @Test
    void quittingAndShutdownDiscardQueuedCallbacks() {
        var count = new AtomicInteger();
        guard.submit(player, count::incrementAndGet);
        guard.quit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        guard.submit(player, count::incrementAndGet);
        server.getScheduler().performOneTick();
        assertEquals(1, count.get());
        clock.set(1_000_000_000L);
        guard.submit(player, count::incrementAndGet);
        guard.shutdown();
        server.getScheduler().performOneTick();
        assertEquals(1, count.get());
    }
}
