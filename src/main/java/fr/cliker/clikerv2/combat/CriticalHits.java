package fr.cliker.clikerv2.combat;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/** Les coups en retombant conservent le multiplicateur critique de Minecraft. */
public final class CriticalHits {
    private CriticalHits() {}

    public static long multiply(long damage) {
        if (damage <= 0) return damage;
        long bonus = Math.max(1, damage / 2 + damage % 2);
        return damage > Long.MAX_VALUE - bonus ? Long.MAX_VALUE : damage + bonus;
    }

    public static long apply(Player player, long damage) {
        return player.getFallDistance() > 0
                        && !player.isOnGround()
                        && !player.isSprinting()
                        && !player.isInWater()
                        && !player.isInsideVehicle()
                        && !player.isClimbing()
                        && !player.isFlying()
                        && !player.isGliding()
                        && !player.hasPotionEffect(PotionEffectType.BLINDNESS)
                ? multiply(damage)
                : damage;
    }
}
