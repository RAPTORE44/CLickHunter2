package fr.cliker.clikerv2.essence;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.entity.Player;

import java.util.*;

/** Un relevé de l'inventaire par coup ; aucune recherche par compresseur et par palier. */
public final class CompressorManager {
    public void onHit(Player player) {
        var contents = player.getInventory().getContents();
        Set<MobTier> active = EnumSet.noneOf(MobTier.class);
        for (var stack : contents) {
            var tier = CompressorItem.tierOf(stack);
            if (tier != null) active.add(tier);
        }
        if (active.isEmpty()) return;
        Map<MobTier, int[]> counts = new EnumMap<>(MobTier.class);
        // Les 36 premières cases sont le stockage ; les essences en main secondaire ne sont pas
        // consommées.
        for (int slot = 0; slot < Math.min(36, contents.length); slot++) {
            var stack = contents[slot];
            var tier = EssenceItem.tierOf(stack);
            if (tier != null && active.contains(tier))
                counts.computeIfAbsent(tier, k -> new int[EssenceRarity.values().length])[
                                EssenceItem.rarityOf(stack).ordinal()] +=
                        stack.getAmount();
        }
        for (var tier : active) {
            var count = counts.get(tier);
            if (count == null) continue;
            for (var rarity : EssenceRarity.values())
                if (rarity.next() != null && count[rarity.ordinal()] >= EssenceCompression.RATIO) {
                    EssenceCompression.cascade(player, tier);
                    break;
                }
        }
    }
}
