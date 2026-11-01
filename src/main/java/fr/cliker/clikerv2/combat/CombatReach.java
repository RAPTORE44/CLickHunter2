package fr.cliker.clikerv2.combat;

import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Validation serveur commune des attaques de mêlée : aucun réglage du client ne fait autorité. */
public final class CombatReach {
    private CombatReach() {}

    public static boolean sword(org.bukkit.inventory.ItemStack item) {
        return item != null && item.getType().name().endsWith("_SWORD");
    }

    public static boolean autoRange(Player player, LivingEntity target) {
        var a = player.getBoundingBox();
        var b = target.getBoundingBox();
        double x = Math.max(0, Math.max(a.getMinX() - b.getMaxX(), b.getMinX() - a.getMaxX()));
        double y = Math.max(0, Math.max(a.getMinY() - b.getMaxY(), b.getMinY() - a.getMaxY()));
        double z = Math.max(0, Math.max(a.getMinZ() - b.getMaxZ(), b.getMinZ() - a.getMaxZ()));
        return x * x + y * y + z * z <= 1.0;
    }

    public static boolean canHit(Player player, LivingEntity target) {
        return !player.isDead()
                && !target.isDead()
                && player.getGameMode() != GameMode.SPECTATOR
                && player.getWorld().equals(target.getWorld())
                && player.getLocation().distanceSquared(target.getLocation()) <= 36
                && player.hasLineOfSight(target);
    }
}
