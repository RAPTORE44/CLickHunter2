package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.MobTier;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;

/**
 * Enchantements achetés en Écus à la Table d'Enchantement, niveau I à V.
 * Épée : Tranchant, Giant Killer, First Strike, Force (dégâts/XP au kill). Armure : Défense, Force, Chercheur de Butin, Critique.
 * Universel : tiré au hasard comme les autres, applique tous les effets réels de la catégorie en même temps.
 */
public enum EnchantType {
    TRANCHANT(Category.SWORD, "Tranchant", "dégâts", NamedTextColor.RED, "Plus de dégâts"),
    GIANT_KILLER(Category.SWORD, "Giant Killer", "dégâts", NamedTextColor.DARK_RED, "Dégâts bonus contre les gros mobs"),
    FIRST_STRIKE(Category.SWORD, "First Strike", "XP", NamedTextColor.AQUA, "Bonus d'XP au kill"),
    STRENGTH(Category.BOTH, "Force", "dégâts", NamedTextColor.RED, "Plus de dégâts"),
    DEFENCE(Category.ARMOR, "Défense", "%", NamedTextColor.BLUE, "Réduit les dégâts reçus"),
    LOOT_FINDER(Category.ARMOR, "Chercheur de Butin", "%", NamedTextColor.GREEN, "Plus de chance de coffre au kill"),
    CRITICAL(Category.ARMOR, "Critique", "%", NamedTextColor.GOLD, "Chance de coup double"),
    UNIVERSAL_EPEE(Category.SWORD, "Universel", "", NamedTextColor.LIGHT_PURPLE, "Tous les effets d'épée à la fois"),
    UNIVERSAL_ARMURE(Category.ARMOR, "Universel", "", NamedTextColor.LIGHT_PURPLE, "Tous les effets d'armure à la fois");

    public static final int MAX_LEVEL = 5;

    public enum Category { SWORD, ARMOR, BOTH }

    private final Category category;
    private final String label;
    private final String unit;
    private final NamedTextColor color;
    private final String description;

    EnchantType(Category category, String label, String unit, NamedTextColor color, String description) {
        this.category = category;
        this.label = label;
        this.unit = unit;
        this.color = color;
        this.description = description;
    }

    public Category category() { return category; }
    public String label() { return label; }
    public String unit() { return unit; }
    public NamedTextColor color() { return color; }
    public String description() { return description; }

    public boolean isUniversal() { return this == UNIVERSAL_EPEE || this == UNIVERSAL_ARMURE; }

    /** Coût Écus pour passer au niveau donné : flat, identique quel que soit le tier de l'item. */
    public long costForLevel(MobTier tier, int level) {
        return 250_000L;
    }

    /** Valeur du bonus au niveau donné. Nombres bruts (dégâts fixes, XP par kill) ou pourcentages. */
    public double magnitudeAt(MobTier tier, int level) {
        if (level <= 0) return 0;
        return switch (this) {
            case TRANCHANT -> TierGear.pieceDamage(tier) * 0.04 * level;
            case GIANT_KILLER -> TierGear.pieceDamage(tier) * 0.06 * level;
            case STRENGTH -> TierGear.pieceDamage(tier) * 0.04 * level;
            case FIRST_STRIKE -> 5.0 * (tier.ordinal() + 1) * level;
            case DEFENCE -> 1.5 * level;
            case LOOT_FINDER -> 2.0 * level;
            case CRITICAL -> 2.0 * level;
            case UNIVERSAL_EPEE, UNIVERSAL_ARMURE -> 0;
        };
    }

    public static EnchantType[] of(Category category) {
        return Arrays.stream(values()).filter(type -> type.category == category || type.category == Category.BOTH).toArray(EnchantType[]::new);
    }
}
