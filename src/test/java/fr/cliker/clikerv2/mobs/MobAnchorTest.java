package fr.cliker.clikerv2.mobs;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class MobAnchorTest {
    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @SuppressWarnings("unchecked")
    private <T> T field(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    @Test
    @SuppressWarnings("deprecation")
    void zoneBossDamageIsCancelledWithoutAffectingOtherEntities() throws Exception {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var world = server.addSimpleWorld("world");
        var player = server.addPlayer();
        var boss = world.spawnEntity(new Location(world, 0, 65, 0), EntityType.COW);
        var manager = new CustomMobManager(plugin, null, null, null, null, null, null, null);
        Set<String> ids = field(manager, "bossSpawnIds");
        ids.add("zone-boss");
        boss.getPersistentDataContainer()
                .set(
                        new NamespacedKey(plugin, "cliker_spawn_id"),
                        org.bukkit.persistence.PersistentDataType.STRING,
                        "zone-boss");
        var hit =
                new org.bukkit.event.entity.EntityDamageByEntityEvent(
                        boss,
                        player,
                        org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                        6);
        manager.harmlessZoneBoss(hit);
        assertTrue(hit.isCancelled());
        boss.getPersistentDataContainer().remove(new NamespacedKey(plugin, "cliker_spawn_id"));
        var other =
                new org.bukkit.event.entity.EntityDamageByEntityEvent(
                        boss,
                        player,
                        org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                        6);
        manager.harmlessZoneBoss(other);
        assertFalse(other.isCancelled());
    }

    @Test
    void externallyTeleportedMobReturnsToItsOwnWorldWithoutBreakingTheAnchorLoop()
            throws Exception {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var origin = server.addSimpleWorld("origin");
        var other = server.addSimpleWorld("other");
        var mob = (LivingEntity) other.spawnEntity(new Location(other, 20, 70, 20), EntityType.COW);
        var manager = new CustomMobManager(plugin, null, null, null, null, null, null, null);
        Map<String, Location> locations = field(manager, "spawnLocation");
        Map<String, UUID> alive = field(manager, "aliveBySpawnId");
        locations.put("mob", new Location(origin, 1, 65, 1));
        alive.put("mob", mob.getUniqueId());
        var anchor = CustomMobManager.class.getDeclaredMethod("anchorAll");
        anchor.setAccessible(true);
        assertDoesNotThrow(() -> anchor.invoke(manager));
        assertEquals(origin, mob.getWorld());
        assertEquals(new Location(origin, 1, 65, 1), mob.getLocation());
        mob.setVelocity(new Vector(1, .4, 1));
        assertDoesNotThrow(() -> anchor.invoke(manager));
        assertEquals(0, mob.getVelocity().lengthSquared());
    }
}
