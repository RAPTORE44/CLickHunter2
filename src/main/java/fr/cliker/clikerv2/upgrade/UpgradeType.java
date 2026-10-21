package fr.cliker.clikerv2.upgrade;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public enum UpgradeType {
    RICHESSE("richesse", "Richesse", "💰", NamedTextColor.GOLD, 10, "Écus par kill"),
    FORTUNE("fortune", "Fortune", "◆", NamedTextColor.DARK_PURPLE, 1, "Gemmes Sombres par kill"),
    SAGESSE("sagesse", "Sagesse", "✎", NamedTextColor.AQUA, 1, "XP par kill"),
    FORCE("force", "Force", "⚔", NamedTextColor.RED, 2, "Dégâts"),
    VITESSE("vitesse", "Vitesse", "➤", NamedTextColor.YELLOW, 0.002, "Vitesse de déplacement"),
    VITALITE("vitalite", "Vitalité", "❤", NamedTextColor.LIGHT_PURPLE, 2, "PV max"),
    RESISTANCE("resistance", "Résistance", "🛡", NamedTextColor.BLUE, 0.2, "Points d'armure"),
    CADENCE("cadence", "Cadence", "⚡", NamedTextColor.GREEN, 0.1, "secondes de délai en moins sur /autoclick");

    public static final int MAX_LEVEL = 100;

    private static final Map<String, UpgradeType> BY_ID = new LinkedHashMap<>();
    static { for (UpgradeType type : values()) BY_ID.put(type.id, type); }

    private final String id;
    private final String label;
    private final String symbol;
    private final NamedTextColor color;
    private final double perLevelEffect;
    private final String unitLabel;

    UpgradeType(String id, String label, String symbol, NamedTextColor color, double perLevelEffect, String unitLabel) {
        this.id = id;
        this.label = label;
        this.symbol = symbol;
        this.color = color;
        this.perLevelEffect = perLevelEffect;
        this.unitLabel = unitLabel;
    }

    public String id() { return id; }
    public String label() { return label; }
    public String symbol() { return symbol; }
    public NamedTextColor color() { return color; }
    public double perLevelEffect() { return perLevelEffect; }
    public String unitLabel() { return unitLabel; }

    public double bonusAt(int level) { return perLevelEffect * level; }

    /** Coût en Gemmes Sombres (affichées, pas en dixièmes) pour acheter ce niveau précis. */
    public static long costForLevel(int level) { return level * 1000L; }

    public static UpgradeType parse(String input) {
        return BY_ID.get(input.toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    public static Map<String, UpgradeType> byId() { return BY_ID; }
}
