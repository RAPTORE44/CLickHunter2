package fr.cliker.clikerv2.ui;

import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.hunting.Gains;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class GainBar {
    private GainBar() {}

    public static Component format(Gains gains) {
        var separator = Component.text(" · ", NamedTextColor.DARK_GRAY);
        return Component.text(
                        "+" + Numbers.compact(gains.amount(CurrencyType.ECUS)) + " Écus",
                        NamedTextColor.YELLOW)
                .append(separator)
                .append(
                        Component.text(
                                "+"
                                        + CurrencyType.GEMMES_SOMBRES.format(
                                                gains.amount(CurrencyType.GEMMES_SOMBRES))
                                        + " Gemmes",
                                NamedTextColor.GREEN))
                .append(separator)
                .append(
                        Component.text(
                                "+"
                                        + Numbers.compact(gains.amount(CurrencyType.CENDRES))
                                        + " Cendres",
                                NamedTextColor.LIGHT_PURPLE))
                .append(separator)
                .append(
                        Component.text(
                                "+" + Numbers.compact(gains.xp()) + " XP", NamedTextColor.AQUA));
    }
}
