package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.essence.EssenceRarity;

/** Même barème pour les prix d’équipement et le plafond de compression du mob. */
public final class EquipmentEssences {
    private EquipmentEssences() {}

    public static long communeEquivalent(MobTier tier) {
        return 1L << (tier.ordinal() + 1);
    }

    public static EssenceRarity requiredRarity(MobTier tier) {
        return EssenceRarity.forCommuneEquivalent(communeEquivalent(tier));
    }

    public static int armorUnits(MobTier tier) {
        var rarity = requiredRarity(tier);
        return (int)
                Math.min(
                        communeEquivalent(tier) / rarity.valueMultiplier(),
                        EssenceRarity.maxItemCost(rarity));
    }

    public static int swordUnits(MobTier tier) {
        var rarity = requiredRarity(tier);
        return (int)
                Math.min(
                        communeEquivalent(tier) * 2 / rarity.valueMultiplier(),
                        EssenceRarity.maxItemCost(rarity));
    }
}
