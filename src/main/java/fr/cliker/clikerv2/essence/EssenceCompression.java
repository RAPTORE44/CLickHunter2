package fr.cliker.clikerv2.essence;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.entity.Player;

/** Compression en cascade des essences d'un mob : 64 d'un palier de rareté -> 1 du palier suivant, tant que possible. */
public final class EssenceCompression {
    public static final int RATIO = 64;

    private EssenceCompression() {}

    /** Retourne le nombre total de compressions effectuées. */
    public static int cascade(Player player, MobTier tier) {
        int total = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (EssenceRarity rarity : EssenceRarity.values()) {
                EssenceRarity next = rarity.next();
                if (next == null) continue;
                if (EssenceItem.remove(player, tier, rarity, RATIO)) {
                    give(player, tier, next, 1);
                    changed = true;
                    total++;
                }
            }
        }
        return total;
    }

    private static void give(Player player, MobTier tier, EssenceRarity rarity, int amount) {
        var leftover = player.getInventory().addItem(EssenceItem.create(tier, rarity, amount));
        leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }
}
