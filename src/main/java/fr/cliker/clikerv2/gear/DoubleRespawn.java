package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.function.BiConsumer;

/** Un mob bonus temporaire : aucune modification du point de réapparition persistant. */
public final class DoubleRespawn {
    private DoubleRespawn() {}

    public static boolean trigger(
            Player killer,
            MobTier victim,
            Location origin,
            double roll,
            BiConsumer<MobTier, Location> spawn) {
        if (killer == null || origin == null || !Double.isFinite(roll) || roll < 0 || roll >= 100)
            return false;
        var sword = killer.getInventory().getItemInMainHand();
        if (sword == null || !sword.hasItemMeta()) return false;
        String id =
                sword.getItemMeta()
                        .getPersistentDataContainer()
                        .get(TierGear.SWORD_KEY, PersistentDataType.STRING);
        var tier = id == null ? null : MobTier.parse(id);
        if (tier == null) return false;
        int level =
                Math.min(
                        EnchantType.MAX_LEVEL,
                        ItemMods.getEnchantLevel(sword, EnchantType.DOUBLE_RESPAWN));
        if (roll >= EnchantType.DOUBLE_RESPAWN.magnitudeAt(tier, level)) return false;
        try {
            spawn.accept(victim, origin.clone().add(.8, 0, 0));
            return true;
        } catch (IllegalStateException limit) {
            return false;
        } // plafond partagé avec les œufs : 30 par monde
    }
}
