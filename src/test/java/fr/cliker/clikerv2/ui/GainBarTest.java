package fr.cliker.clikerv2.ui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.hunting.Gains;

import net.kyori.adventure.text.format.NamedTextColor;

import org.junit.jupiter.api.Test;

class GainBarTest {
    @Test
    void eachCurrencyUsesItsOwnColor() {
        var text = GainBar.format(Gains.of(10, 20, 30, 40));
        assertEquals(NamedTextColor.YELLOW, text.color());
        assertEquals(NamedTextColor.GREEN, text.children().get(1).color());
        assertEquals(NamedTextColor.LIGHT_PURPLE, text.children().get(3).color());
        assertEquals(NamedTextColor.AQUA, text.children().get(5).color());
    }
}
