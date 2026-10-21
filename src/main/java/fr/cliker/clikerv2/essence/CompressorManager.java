package fr.cliker.clikerv2.essence;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Compresseur d'essences : tant qu'un joueur a le compresseur du bon mob dans son inventaire, compresse
 * automatiquement ses essences de ce mob (64 d'un palier de rareté -> 1 du palier suivant, en cascade)
 * à chaque coup porté sur un mob custom. Propre à chaque joueur, aucun bloc à poser.
 */
public final class CompressorManager {
    /** Appelé par CustomMobManager à chaque coup réussi sur un mob custom. */
    public void onHit(Player player) {
        for (MobTier tier : compressorsOf(player)) EssenceCompression.cascade(player, tier);
    }

    private Set<MobTier> compressorsOf(Player player) {
        Set<MobTier> tiers = new HashSet<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            MobTier tier = CompressorItem.tierOf(stack);
            if (tier != null) tiers.add(tier);
        }
        return tiers;
    }
}
