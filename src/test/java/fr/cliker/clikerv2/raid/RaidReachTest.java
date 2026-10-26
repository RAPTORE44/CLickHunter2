package fr.cliker.clikerv2.raid;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.util.*;

class RaidReachTest {
    private ServerMock server;
    private World world;
    private RaidModule raid;

    @BeforeEach void setup() throws Exception {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("raid-tests");
        raid = new RaidModule(plugin, null, null);
        var field = RaidModule.class.getDeclaredField("world");
        field.setAccessible(true);
        field.set(raid, world);
    }

    @AfterEach void close() { MockBukkit.unmock(); }

    private void hit(double distance, boolean visible) throws Exception {
        var player = new PlayerMock(server, "RaidAttacker") {
            @Override public boolean hasLineOfSight(Entity other) { return visible; }
        };
        server.addPlayer(player);
        player.teleport(new Location(world, 0, 65, 0));
        var mob = world.spawn(new Location(world, distance, 65, 0), Zombie.class);
        var field = RaidModule.class.getDeclaredField("health");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var health = (Map<UUID, Long>) field.get(raid);
        health.put(mob.getUniqueId(), 500L);
        var event = new EntityDamageByEntityEvent(player, mob,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4);
        // A denied hit must return before touching the combat calculator or crediting rewards.
        raid.hit(event);
        assertTrue(event.isCancelled());
        assertEquals(500L, health.get(mob.getUniqueId()));
    }

    @Test void remoteAttackCannotDamageOrRewardARaidMob() throws Exception { hit(8, true); }
    @Test void nearbyAttackThroughAWallCannotDamageOrRewardARaidMob() throws Exception { hit(2, false); }
}
