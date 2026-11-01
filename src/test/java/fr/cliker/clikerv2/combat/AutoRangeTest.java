package fr.cliker.clikerv2.combat;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

class AutoRangeTest {
    @Test
    void oneBlockIsMeasuredBetweenHitboxes() {
        var player =
                (Player)
                        java.lang.reflect.Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class<?>[] {Player.class},
                                (p, m, a) ->
                                        m.getName().equals("getBoundingBox")
                                                ? new BoundingBox(0, 0, 0, .6, 1.8, .6)
                                                : null);
        var near =
                (LivingEntity)
                        java.lang.reflect.Proxy.newProxyInstance(
                                LivingEntity.class.getClassLoader(),
                                new Class<?>[] {LivingEntity.class},
                                (p, m, a) ->
                                        m.getName().equals("getBoundingBox")
                                                ? new BoundingBox(1.6, 0, 0, 3, 2, 1)
                                                : null);
        var far =
                (LivingEntity)
                        java.lang.reflect.Proxy.newProxyInstance(
                                LivingEntity.class.getClassLoader(),
                                new Class<?>[] {LivingEntity.class},
                                (p, m, a) ->
                                        m.getName().equals("getBoundingBox")
                                                ? new BoundingBox(1.61, 0, 0, 3, 2, 1)
                                                : null);
        assertTrue(CombatReach.autoRange(player, near));
        assertFalse(CombatReach.autoRange(player, far));
    }
}
