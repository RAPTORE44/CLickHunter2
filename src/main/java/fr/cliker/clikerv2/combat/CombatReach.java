package fr.cliker.clikerv2.combat;

import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Validation serveur commune des attaques de mêlée : aucun réglage du client ne fait autorité. */
public final class CombatReach {
    private CombatReach() {}

    public static boolean canHit(Player player, LivingEntity target) {
        return !player.isDead()
                && !target.isDead()
                && player.getGameMode() != GameMode.SPECTATOR
                && player.getWorld().equals(target.getWorld())
                && player.getLocation().distanceSquared(target.getLocation()) <= 36
                && player.hasLineOfSight(target);
    }
}
