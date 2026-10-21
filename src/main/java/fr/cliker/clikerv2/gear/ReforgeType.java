package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.MobTier;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Préfixes de reforge appliqués aléatoirement à l'Enclume de Reforge (payé en Gemmes Sombres). */
public enum ReforgeType {
    FEROCE(EnchantType.Category.SWORD, "Féroce", NamedTextColor.RED, "Plus de dégâts"),
    VIF(EnchantType.Category.SWORD, "Vif", NamedTextColor.AQUA, "Autoclick plus rapide"),
    SANGLANT(EnchantType.Category.SWORD, "Sanglant", NamedTextColor.DARK_RED, "Soigne en tapant"),
    ROBUSTE(EnchantType.Category.ARMOR, "Robuste", NamedTextColor.GREEN, "Plus de vie max"),
    FERME(EnchantType.Category.ARMOR, "Ferme", NamedTextColor.BLUE, "Réduit dégâts reçus"),
    OMBRAGEUX(EnchantType.Category.ARMOR, "Ombrageux", NamedTextColor.DARK_PURPLE, "Plus de vitesse"),
    UNIVERSEL_EPEE(EnchantType.Category.SWORD, "Universel", NamedTextColor.GOLD, "Tous les effets d'épée à la fois"),
    UNIVERSEL_ARMURE(EnchantType.Category.ARMOR, "Universel", NamedTextColor.GOLD, "Tous les effets d'armure à la fois");

    private final EnchantType.Category category;
    private final String label;
    private final NamedTextColor color;
    private final String description;

    ReforgeType(EnchantType.Category category, String label, NamedTextColor color, String description) {
        this.category = category;
        this.label = label;
        this.color = color;
        this.description = description;
    }

    public EnchantType.Category category() { return category; }
    public String label() { return label; }
    public NamedTextColor color() { return color; }
    public String description() { return description; }

    /** Dégâts fixes supplémentaires (épée uniquement, Féroce ou Universel). */
    public long swordDamageBonus(MobTier tier) {
        return (this == FEROCE || this == UNIVERSEL_EPEE) ? Math.round(TierGear.pieceDamage(tier) * 0.10) : 0;
    }

    /** % de vol de vie supplémentaire (épée, Sanglant ou Universel). */
    public double swordLifestealPercent() { return (this == SANGLANT || this == UNIVERSEL_EPEE) ? 8.0 : 0; }

    /** % de réduction du délai d'autoclick (épée, Vif ou Universel). */
    public double swordAutoclickSpeedPercent() { return (this == VIF || this == UNIVERSEL_EPEE) ? 25.0 : 0; }

    /** Vie max fixe supplémentaire (armure, Robuste ou Universel). */
    public long armorHealthBonus(MobTier tier) {
        return (this == ROBUSTE || this == UNIVERSEL_ARMURE) ? Math.round(30.0 * (tier.ordinal() + 1)) : 0;
    }

    /** % de réduction des dégâts reçus (armure, Ferme ou Universel). */
    public double armorDamageReductionPercent() { return (this == FERME || this == UNIVERSEL_ARMURE) ? 8.0 : 0; }

    /** Bonus d'attribut de vitesse de déplacement (armure, Ombrageux ou Universel). */
    public double armorSpeedBonus() { return (this == OMBRAGEUX || this == UNIVERSEL_ARMURE) ? 0.02 : 0; }

    /** Coût en Gemmes Sombres (unités entières) : flat, identique quel que soit le tier de l'item. */
    public static long cost(MobTier tier) { return 2_500L; }

    public static ReforgeType randomFor(EnchantType.Category category) {
        List<ReforgeType> options = Arrays.stream(values()).filter(reforge -> reforge.category == category).toList();
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }
}
