package fr.cliker.clikerv2.economy;

import fr.cliker.clikerv2.Numbers;

import java.util.Locale;

public enum CurrencyType {
    ECUS("ecus", "Écus", "✦"),
    GEMMES_SOMBRES("gemmes-sombres", "Gemmes Sombres", "◆"),
    CENDRES("cendres", "Cendres", "❀");

    private final String key;
    private final String displayName;
    private final String symbol;

    CurrencyType(String key, String displayName, String symbol) {
        this.key = key;
        this.displayName = displayName;
        this.symbol = symbol;
    }

    public String key() { return key; }
    public String displayName() { return displayName; }
    public String symbol() { return symbol; }

    /** Gemmes Sombres sont stockées en dixièmes (10 = 1.0 gemme) pour permettre les récompenses de +0.1. */
    public String format(long rawValue) {
        if (this != GEMMES_SOMBRES) return Numbers.compact(rawValue);
        return Numbers.compact(rawValue / 10.0);
    }

    public static CurrencyType parse(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replace('_', '-');
        for (CurrencyType currency : values()) {
            if (currency.key.equals(normalized) || currency.name().equalsIgnoreCase(normalized)) return currency;
        }
        return null;
    }
}
