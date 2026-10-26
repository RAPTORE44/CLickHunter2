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
