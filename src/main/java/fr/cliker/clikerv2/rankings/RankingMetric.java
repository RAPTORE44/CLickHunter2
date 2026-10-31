package fr.cliker.clikerv2.rankings;

import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.donors.DonorPointsStore;
import fr.cliker.clikerv2.economy.CurrencyType;

import org.bukkit.Material;

import java.util.*;

public enum RankingMetric {
    ECUS("ecus", "&eÉcus", Material.GOLD_INGOT, "Écus", "ecus", CurrencyType.ECUS),
    GEMS(
            "gemmes",
            "&5Gemmes Sombres",
            Material.AMETHYST_SHARD,
            "Gemmes Sombres",
            "gemmes_sombres",
            CurrencyType.GEMMES_SOMBRES),
    ASH("cendres", "&7Cendres", Material.GUNPOWDER, "Cendres", "cendres", CurrencyType.CENDRES),
    KILLS("kills", "&cÉliminations", Material.IRON_SWORD, "éliminations", "total", null),
    LEVELS("niveaux", "&bNiveaux", Material.EXPERIENCE_BOTTLE, "niveaux", "level", null),
    DONORS("donateurs", "&6Donateurs", Material.EMERALD, "points", "units", null);
    public final String id, title, unit, column;
    public final Material icon;
    private final CurrencyType currency;

    RankingMetric(
            String id,
            String title,
            Material icon,
            String unit,
            String column,
            CurrencyType currency) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.unit = unit;
        this.column = column;
        this.currency = currency;
    }

    public String format(long value) {
        return this == DONORS
                ? DonorPointsStore.format(value)
                : currency != null ? currency.format(value) : Numbers.compact(value);
    }

    public String meaning(boolean all) {
        if (this == DONORS)
            return all
                    ? "Classement des points de dons actuels."
                    : "Points ajoutés moins les points retirés sur la période.";
        if (currency != null)
            return all
                    ? "Solde actuel, après achats et dépenses."
                    : "Variation du solde : entrées moins sorties sur la période.";
        if (this == LEVELS)
            return all
                    ? "Dernier niveau connu de chaque joueur."
                    : "Niveaux gagnés sur la période (baisses ignorées).";
        return all
                ? "Total des éliminations suivies par le plugin."
                : "Éliminations ajoutées sur la période.";
    }

    public static RankingMetric parse(String value) {
        if (value.equalsIgnoreCase("mobs")) return KILLS;
        if (value.equalsIgnoreCase("level") || value.equalsIgnoreCase("niveau")) return LEVELS;
        return Arrays.stream(values())
                .filter(v -> v.id.equalsIgnoreCase(value))
                .findFirst()
                .orElse(null);
    }
}
