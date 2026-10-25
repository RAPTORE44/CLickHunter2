package fr.cliker.clikerv2.essence;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.entity.Player;

/**
 * Compression en cascade des essences d'un mob : 64 d'un palier de rareté -> 1 du palier suivant,
 * tant que possible.
 */
public final class EssenceCompression {
    public static final int RATIO = 64;

    private EssenceCompression() {}

    /** Retourne le nombre total de compressions effectuées. */
    public static int cascade(Player player, MobTier tier) {
        int total = 0;
        for (EssenceRarity rarity : EssenceRarity.values()) {
            var next = rarity.next();
            if (next == null) continue;
            int batches = EssenceItem.count(player, tier, rarity) / RATIO;
            if (batches == 0) continue;
            if (EssenceItem.remove(player, tier, rarity, batches * RATIO)) {
                give(player, tier, next, batches);
                total += batches;
            }
        }
        return total;
    }

    private static void give(Player player, MobTier tier, EssenceRarity rarity, int amount) {
        fr.cliker.clikerv2.rewards.ItemDelivery.give(
                player, EssenceItem.create(tier, rarity, amount));
    }
}
