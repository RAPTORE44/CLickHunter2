package fr.cliker.clikerv2.spawn;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.hunting.RewardLedger;
import fr.cliker.clikerv2.raid.RaidModule;

import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.nio.file.*;
import java.util.*;

class SpawnSafetyTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private SpawnManager spawn;
    private org.mockbukkit.mockbukkit.entity.PlayerMock player;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "spawn-" + UUID.randomUUID() + ".db");
        db.initialize();
        spawn = new SpawnManager(plugin, new SpawnStore(db));
        player = server.addPlayer();
    }

    @AfterEach
    void cleanup() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void drain() {
        db.submit(c -> null).join();
        server.getScheduler().performTicks(3);
    }

    @Test
    void failedSaveKeepsPreviouslyConfirmedSpawn() {
        var original = player.getLocation();
        var first = spawn.setSpawn(original);
        drain();
        assertTrue(first.join());
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.execute(
                                        "CREATE TRIGGER reject_spawn BEFORE INSERT ON server_spawn"
                                                + " BEGIN SELECT RAISE(ABORT,'failure'); END");
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        var failed = spawn.setSpawn(original.clone().add(50, 0, 0));
        drain();
        assertFalse(failed.join());
        assertEquals(original, spawn.spawn());
    }

    @Test
    void cancelledTeleportIsNotReportedAsSuccessful() {
        var set = spawn.setSpawn(player.getLocation().clone().add(5, 0, 0));
        drain();
        assertTrue(set.join());
        server.getPluginManager()
                .registerEvents(
                        new Listener() {
                            @EventHandler
                            public void teleport(
                                    org.bukkit.event.player.PlayerTeleportEvent event) {
                                event.setCancelled(true);
                            }
                        },
                        plugin);
        assertFalse(spawn.teleport(player));
    }

    @Test
    void raidRespawnWinsOverGenericSpawnDespiteListenerRegistrationOrder() throws Exception {
        var set = spawn.setSpawn(player.getLocation());
        drain();
        assertTrue(set.join());
        var arena = server.addSimpleWorld("raid-respawn");
        player.teleport(new Location(arena, 0, 65, 0));
        Files.createDirectories(plugin.getDataFolder().toPath());
        try (var input = getClass().getResourceAsStream("/raid.yml")) {
            Files.copy(
                    input,
                    fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "raid.yml").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        var ledger = new RewardLedger(plugin, db);
        var raid = new RaidModule(plugin, ledger, null);
        var world = RaidModule.class.getDeclaredField("world");
        world.setAccessible(true);
        world.set(raid, arena);
        server.getPluginManager().registerEvents(raid, plugin);
        server.getPluginManager().registerEvents(new RespawnListener(spawn), plugin);
        var event = new PlayerRespawnEvent(player, player.getLocation(), false);
        server.getPluginManager().callEvent(event);
        assertEquals(arena, event.getRespawnLocation().getWorld());
        assertEquals(24.5, event.getRespawnLocation().getZ());
        ledger.shutdown();
        HandlerList.unregisterAll(raid);
    }
}
