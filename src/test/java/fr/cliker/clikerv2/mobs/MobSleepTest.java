package fr.cliker.clikerv2.mobs;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.performance.ZoneActivity;

import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class MobSleepTest {
    ServerMock server;
    ZoneActivity zones;
    CustomMobManager mobs;

    @SuppressWarnings("unchecked")
    <T> T field(String name) throws Exception {
        var f = CustomMobManager.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(mobs);
    }

    void run(String name) throws Exception {
        var method = CustomMobManager.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(mobs);
    }

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        zones = new ZoneActivity(plugin);
        zones.start();
        mobs = new CustomMobManager(plugin, null, null, null, null, null, null, null);
    }

    @AfterEach
    void end() {
        mobs.shutdown();
        zones.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void orphanBodiesAreRemovedOnlyAfterSpawnRecordsHaveLoaded() throws Exception {
        var world = server.addSimpleWorld("world");
        var at = new Location(world, 0, 65, 0);
        var orphan = world.spawnEntity(at, org.bukkit.entity.EntityType.COW);
        orphan.getPersistentDataContainer()
                .set(
                        CustomMobManager.MOB_ID_KEY,
                        org.bukkit.persistence.PersistentDataType.STRING,
                        "vache");
        orphan.getPersistentDataContainer()
                .set(
                        new NamespacedKey(
                                CustomMobManager.MOB_ID_KEY.getNamespace(), "cliker_spawn_id"),
                        org.bukkit.persistence.PersistentDataType.STRING,
                        "deleted-point");
        var event =
                new org.bukkit.event.world.EntitiesLoadEvent(
                        world.getChunkAt(at), java.util.List.of(orphan));
        mobs.entitiesLoaded(event);
        assertTrue(orphan.isValid());
        var ready = CustomMobManager.class.getDeclaredField("pointsLoaded");
        ready.setAccessible(true);
        ready.setBoolean(mobs, true);
        mobs.entitiesLoaded(event);
        assertFalse(orphan.isValid());
    }

    @Test
    void noPlayerMeansNoSpawnAndReturningPlayerRestoresHealthWithoutDuplicates() throws Exception {
        var world = server.addSimpleWorld("world");
        var at = new Location(world, 10000, 65, 0);
        Map<String, Location> points = field("spawnLocation");
        Map<String, String> types = field("spawnMobId");
        Map<String, UUID> alive = field("aliveBySpawnId");
        Map<String, Long> health = field("currentHealth");
        points.put("test", at);
        types.put("test", "vache");
        var spawn = CustomMobManager.class.getDeclaredMethod("spawn", String.class);
        spawn.setAccessible(true);
        spawn.invoke(mobs, "test");
        assertTrue(alive.isEmpty());
        assertFalse(world.isChunkLoaded(625, 0));
        var player = server.addPlayer();
        player.teleport(at);
        world.getChunkAt(at).load();
        zones.refresh();
        var initial = world.spawnEntity(at, org.bukkit.entity.EntityType.COW);
        initial.getPersistentDataContainer()
                .set(
                        new NamespacedKey(
                                CustomMobManager.MOB_ID_KEY.getNamespace(), "cliker_spawn_id"),
                        org.bukkit.persistence.PersistentDataType.STRING,
                        "test");
        alive.put("test", initial.getUniqueId());
        health.put("test", 37L);
        run("recoverMissingMobs");
        assertEquals(1, alive.size());
        var old = alive.get("test");
        player.teleport(new Location(world, 0, 65, 0));
        zones.refresh();
        run("recoverMissingMobs");
        assertTrue(alive.isEmpty());
        assertEquals(37L, health.get("test"));
        player.teleport(at);
        zones.refresh();
        var restored = world.spawnEntity(at, org.bukkit.entity.EntityType.COW);
        restored.getPersistentDataContainer()
                .set(
                        new NamespacedKey(
                                CustomMobManager.MOB_ID_KEY.getNamespace(), "cliker_spawn_id"),
                        org.bukkit.persistence.PersistentDataType.STRING,
                        "test");
        run("recoverMissingMobs");
        assertEquals(1, alive.size());
        assertNotEquals(old, alive.get("test"));
        assertEquals(37L, health.get("test"));
        run("recoverMissingMobs");
        assertEquals(1, alive.size());
    }
}
