package fr.cliker.clikerv2.daily;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.junit.jupiter.api.Test;

class ColorCacheTest {
    @Test
    void labelsKeepTheSameRenderingAndMemoryRemainsBounded() {
        String sample = "&aÉcus &l1 000 &7• &dGemmes";
        assertEquals(
                LegacyComponentSerializer.legacyAmpersand().deserialize(sample),
                DailySettings.color(sample));
        assertSame(DailySettings.color(sample), DailySettings.color(sample));
        for (int i = 0; i < 1000; i++) DailySettings.color("&bJoueur " + i);
        assertTrue(DailySettings.cachedLabels() <= 256);
        String longText = "&a" + "Texte".repeat(100);
        int before = DailySettings.cachedLabels();
        assertEquals(
                LegacyComponentSerializer.legacyAmpersand().deserialize(longText),
                DailySettings.color(longText));
        assertEquals(before, DailySettings.cachedLabels());
    }
}
