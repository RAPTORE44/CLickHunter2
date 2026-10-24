package fr.cliker.clikerv2.gear;

import java.util.List;

public final class EnchantRoll {
    private EnchantRoll() {}
    public static void validate(List<Double> weights) {
        if (weights.size() != 5 || weights.stream().anyMatch(w -> w == null || !Double.isFinite(w) || w < 0)
                || Math.abs(weights.stream().mapToDouble(Double::doubleValue).sum() - 100) > 0.000001) {
            throw new IllegalArgumentException("Les cinq probabilités d'enchantement doivent totaliser 100 %");
        }
    }
    public static int level(List<Double> weights, double roll) {
        validate(weights);
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("Tirage hors limites");
        double cumulative = 0;
        for (int i = 0; i < weights.size(); i++) { cumulative += weights.get(i); if (roll < cumulative) return i + 1; }
        return 5;
    }
}
