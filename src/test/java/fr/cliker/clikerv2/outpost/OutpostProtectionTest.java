package fr.cliker.clikerv2.outpost;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.rewards.ClaimTestFactory;

import org.bukkit.*;
import org.bukkit.event.block.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.nio.file.*;
import java.util.*;

class OutpostProtectionTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private OutpostModule module;
    private org.bukkit.World world;
    private org.mockbukkit.mockbukkit.entity.PlayerMock owner, guest;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("plots");
        owner = server.addPlayer();
        guest = server.addPlayer();
        Files.createDirectories(plugin.getDataFolder().toPath());
        try (var in = getClass().getResourceAsStream("/avantpostes.yml")) {
            Files.copy(
                    in,
                    plugin.getDataFolder().toPath().resolve("avantpostes.yml"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        db = new DatabaseManager(plugin, "acl-" + UUID.randomUUID() + ".db");
        db.initialize();
        module = new OutpostModule(plugin, db, ClaimTestFactory.create(plugin, db));
        set("world", world);
        set("ready", true);
        var cache = OutpostModule.class.getDeclaredMethod("cache", OutpostStore.Plot.class);
        cache.setAccessible(true);
        cache.invoke(module, new OutpostStore.Plot(1, owner.getUniqueId(), owner.getName(), 0));
        server.getPluginManager().registerEvents(module, plugin);
    }

    private void set(String name, Object value) throws Exception {
        var f = OutpostModule.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(module, value);
    }

    @AfterEach
    void cleanup() {
        try {
            org.bukkit.event.HandlerList.unregisterAll(module);
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void visitorsCannotBreakAndOwnerCannotBuildBeyondBoughtBoundary() {
        var inside = new Location(world, 256, 65, 0);
        assertTrue(module.canBuild(owner.getUniqueId(), inside));
        assertFalse(module.canBuild(guest.getUniqueId(), inside));
        assertFalse(module.canBuild(owner.getUniqueId(), new Location(world, 273, 65, 0)));
        var denied = new BlockBreakEvent(inside.getBlock(), guest);
        server.getPluginManager().callEvent(denied);
        assertTrue(denied.isCancelled());
        var allowed = new BlockBreakEvent(inside.getBlock(), owner);
        server.getPluginManager().callEvent(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test
    void fluidCannotCrossClaimBoundary() {
        var from = world.getBlockAt(272, 65, 0);
        var e = new BlockFromToEvent(from, org.bukkit.block.BlockFace.EAST);
        server.getPluginManager().callEvent(e);
        assertTrue(e.isCancelled());
        var same =
                new BlockFromToEvent(world.getBlockAt(256, 65, 0), org.bukkit.block.BlockFace.EAST);
        server.getPluginManager().callEvent(same);
        assertFalse(same.isCancelled());
    }
}
