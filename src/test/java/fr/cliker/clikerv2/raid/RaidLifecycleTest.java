package fr.cliker.clikerv2.raid;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class RaidLifecycleTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private RaidModule raid;
    private World lobby, arena;

    @BeforeEach
    void before() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        lobby = server.addSimpleWorld("lobby");
        arena = server.addSimpleWorld("arena");
        raid = new RaidModule(plugin, null, null);
        field("world", arena);
        field("bar", Bukkit.createBossBar("Raid", BarColor.RED, BarStyle.SOLID));
        server.getPluginManager().registerEvents(raid, plugin);
    }

    @AfterEach
    void after() {
        raid.shutdown();
        MockBukkit.unmock();
    }

    private void field(String name, Object value) throws Exception {
        var f = RaidModule.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(raid, value);
    }

    private Object field(String name) throws Exception {
        var f = RaidModule.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(raid);
    }

    @Test
    void deniedExitPreservesObservationAndOriginalReturnUntilTeleportSucceeds() throws Exception {
        var player = server.addPlayer();
        player.teleport(new Location(lobby, 7, 65, 4));
        var original = player.getLocation();
        player.addAttachment(plugin, "cliker.raid.admin", true);
        raid.observe(player);
        assertEquals(arena, player.getWorld());
        Listener blocker =
                new Listener() {
                    @EventHandler
                    public void tp(PlayerTeleportEvent e) {
                        if (e.getTo().getWorld() == lobby) e.setCancelled(true);
                    }
                };
        server.getPluginManager().registerEvents(blocker, plugin);
        raid.exit(player);
        assertEquals(arena, player.getWorld());
        assertTrue(((Set<?>) field("observers")).contains(player.getUniqueId()));
        assertTrue(((Map<?, ?>) field("returns")).containsKey(player.getUniqueId()));
        HandlerList.unregisterAll(blocker);
        raid.exit(player);
        assertEquals(original, player.getLocation());
        assertFalse(((Set<?>) field("observers")).contains(player.getUniqueId()));
    }

    @Test
    void refusedEntryDoesNotRetainAFalseReturnLocation() throws Exception {
        var player = server.addPlayer();
        player.teleport(lobby.getSpawnLocation());
        player.addAttachment(plugin, "cliker.raid", true);
        server.getPluginManager()
                .registerEvents(
                        new Listener() {
                            @EventHandler
                            public void tp(PlayerTeleportEvent e) {
                                if (e.getTo().getWorld() == arena) e.setCancelled(true);
                            }
                        },
                        plugin);
        raid.enter(player);
        assertEquals(lobby, player.getWorld());
        assertFalse(((Map<?, ?>) field("returns")).containsKey(player.getUniqueId()));
    }
}
