package fr.cliker.clikerv2.combat;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.upgrade.UpgradeManager;
import fr.cliker.clikerv2.upgrade.UpgradeStore;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

class AutoClickStateTest {
    private ServerMock server;
    private DatabaseManager db;
    private AutoClickManager manager;
    private StatePlayer player;
    private TracingWorld world;

    private static final class StatePlayer extends PlayerMock {
        boolean forcedDead;

        StatePlayer(ServerMock server) {
            super(server, "AutoState");
        }

        @Override
        public boolean isDead() {
            return forcedDead || super.isDead();
        }
    }

    private static final class TracingWorld extends WorldMock {
        int traces;

        TracingWorld() {
            super(new WorldCreator("autoclick-state"));
        }

        @Override
        public RayTraceResult rayTraceEntities(
                Location start,
                Vector direction,
                double distance,
                double size,
                Predicate<? super Entity> filter) {
            traces++;
            return null;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "autoclick-" + UUID.randomUUID() + ".db");
        db.initialize();
        var upgrades =
                new UpgradeManager(plugin, new UpgradeStore(db), new EconomyService(db, Map.of()));
        world = new TracingWorld();
        server.addWorld(world);
        player = new StatePlayer(server);
        server.addPlayer(player);
        player.teleport(new Location(world, 0, 65, 0));
        player.addAttachment(plugin, "cliker.autoclick.use", true);
        manager = new AutoClickManager(plugin, upgrades);
        manager.onCommand(player, null, "autoclick", new String[0]);
        manager.start();
    }

    @AfterEach
    void cleanup() {
        try {
            manager.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void deathScreenPausesAutomaticTargetingAndRespawnResumesTheSetting() {
        player.forcedDead = true;
        server.getScheduler().performTicks(50);
        assertEquals(0, world.traces, "No search or scripted damage while the player is dead");
        player.forcedDead = false;
        server.getScheduler().performTicks(10);
        assertTrue(world.traces > 0, "Autoclick remains enabled after respawn");
    }

    @Test
    void spectatorCannotAttackAutomaticallyButLeavingTheModeResumesTheSetting() {
        player.setGameMode(GameMode.SPECTATOR);
        server.getScheduler().performTicks(50);
        assertEquals(0, world.traces);
        player.setGameMode(GameMode.SURVIVAL);
        server.getScheduler().performTicks(10);
        assertTrue(world.traces > 0);
    }
}
