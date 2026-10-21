package fr.cliker.clikerv2.essence;

import net.kyori.adventure.text.format.NamedTextColor;

/** Palier de rareté d'une essence de mob. 64 essences d'un palier = 1 essence du palier suivant (même mob). */
public enum EssenceRarity {
    COMMUNE("Commune", NamedTextColor.WHITE),
    PEU_COMMUNE("Peu Commune", NamedTextColor.GREEN),
    RARE("Rare", NamedTextColor.BLUE);

    private final String label;
    private final NamedTextColor color;

    EssenceRarity(String label, NamedTextColor color) {
        this.label = label;
        this.color = color;
    }

    public String label() { return label; }
    public NamedTextColor color() { return color; }

    /** Multiplicateur de valeur : chaque palier vaut 64x le précédent (conserve la valeur de la compression). */
    public long valueMultiplier() {
        long value = 1;
        for (int i = 0; i < ordinal(); i++) value *= 64;
        return value;
    }

    public EssenceRarity next() {
        int index = ordinal();
        return index + 1 < values().length ? values()[index + 1] : null;
    }

    public EssenceRarity previous() {
        int index = ordinal();
        return index > 0 ? values()[index - 1] : null;
    }

    /** Palier le plus élevé dont le multiplicateur de valeur tient dans ce total en équivalent-Commune. */
    public static EssenceRarity forCommuneEquivalent(long communeEquivalent) {
        EssenceRarity best = COMMUNE;
        for (EssenceRarity rarity : values()) {
            if (communeEquivalent >= rarity.valueMultiplier()) best = rarity;
        }
        return best;
    }

    /** Coût maximum en essences pour un seul item au PNJ, quel que soit le mob (évite les milliers de Rare). */
    public static int maxItemCost(EssenceRarity rarity) {
        return switch (rarity) {
            case COMMUNE -> 256;
            case PEU_COMMUNE -> 32;
            case RARE -> 16;
        };
    }
}
