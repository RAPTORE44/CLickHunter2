package fr.cliker.clikerv2.hunting;

import fr.cliker.clikerv2.economy.CurrencyType;

import java.util.*;

/** Quantités brutes, même unité que CurrencyType (gemmes en dixièmes). */
public record Gains(Map<CurrencyType, Long> money, long xp) {
    public Gains {
        money = Map.copyOf(money);
        if (xp < 0 || money.values().stream().anyMatch(n -> n < 0))
            throw new IllegalArgumentException("Gain négatif");
    }

    public static Gains of(long ecus, long gems, long cendres, long xp) {
        return new Gains(
                Map.of(
                        CurrencyType.ECUS,
                        ecus,
                        CurrencyType.GEMMES_SOMBRES,
                        gems,
                        CurrencyType.CENDRES,
                        cendres),
                xp);
    }

    public long amount(CurrencyType currency) {
        return money.getOrDefault(currency, 0L);
    }

    public Gains plus(Gains other) {
        Map<CurrencyType, Long> total = new EnumMap<>(CurrencyType.class);
        for (var currency : CurrencyType.values())
            total.put(currency, Math.addExact(amount(currency), other.amount(currency)));
        return new Gains(total, Math.addExact(xp, other.xp));
    }
}
