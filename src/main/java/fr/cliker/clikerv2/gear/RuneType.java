package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.MobTier;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;

/**
 * Runes gravées à la Table de Runes (payé en Cendres), niveau I à XV. Une seule rune active à la fois.
 * Épée : Tranchant, Expérience, Écus, Gemmes, Cendres. Armure : Force, Expérience. Universel : tiré au hasard comme les autres.
 */
public enum RuneType {
    TRANCHANT(EnchantType.Category.SWORD, "Tranchant", "dégâts", NamedTextColor.RED, "Plus de dégâts"),
    XP(EnchantType.Category.BOTH, "Expérience", "XP", NamedTextColor.AQUA, "Plus d'XP au kill"),
    ECUS(EnchantType.Category.SWORD, "Écus", "Écus", NamedTextColor.GOLD, "Plus d'Écus au kill"),
    GEMMES(EnchantType.Category.SWORD, "Gemmes", "Gemmes Sombres", NamedTextColor.DARK_PURPLE, "Plus de Gemmes Sombres au kill"),
    CENDRES(EnchantType.Category.SWORD, "Cendres", "Cendres", NamedTextColor.GRAY, "Plus de Cendres au kill"),
    FORCE(EnchantType.Category.ARMOR, "Force", "dégâts", NamedTextColor.RED, "Plus de dégâts"),
    UNIVERSEL_EPEE(EnchantType.Category.SWORD, "Universel", "", NamedTextColor.LIGHT_PURPLE, "Tous les effets d'épée à la fois"),
    UNIVERSEL_ARMURE(EnchantType.Category.ARMOR, "Universel", "", NamedTextColor.LIGHT_PURPLE, "Tous les effets d'armure à la fois");

    public static final int MAX_LEVEL = 15;

    private final EnchantType.Category category;
    private final String label;
    private final String unit;
    private final NamedTextColor color;
    private final String description;

    RuneType(EnchantType.Category category, String label, String unit, NamedTextColor color, String description) {
        this.category = category;
        this.label = label;
        this.unit = unit;
        this.color = color;
        this.description = description;
    }

    public EnchantType.Category category() { return category; }
    public String label() { return label; }
    public String unit() { return unit; }
    public NamedTextColor color() { return color; }
    public String description() { return description; }

    public boolean isUniversal() { return this == UNIVERSEL_EPEE || this == UNIVERSEL_ARMURE; }

    /** Coût Cendres pour passer au niveau donné : flat, identique quel que soit le tier de l'item. */
    public long costForLevel(MobTier tier, int level) {
        return 500L;
    }

    public double magnitudeAt(MobTier tier, int level) {
        if (level <= 0) return 0;
        return switch (this) {
            case TRANCHANT, FORCE -> TierGear.pieceDamage(tier) * 0.04 * level;
            case XP -> 5.0 * (tier.ordinal() + 1) * level;
            case ECUS -> 50.0 * (tier.ordinal() + 1) * level;
            case GEMMES -> 5.0 * (tier.ordinal() + 1) * level;
            case CENDRES -> 2.0 * (tier.ordinal() + 1) * level;
            case UNIVERSEL_EPEE, UNIVERSEL_ARMURE -> 0;
        };
    }

    public static RuneType[] of(EnchantType.Category category) {
        return Arrays.stream(values()).filter(rune -> rune.category == category || rune.category == EnchantType.Category.BOTH).toArray(RuneType[]::new);
    }
}
