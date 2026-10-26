package fr.cliker.clikerv2.social;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.*;

class SocialSettingsTest {
    private YamlConfiguration config() {
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(
                        getClass().getResourceAsStream("/communaute/social.yml"),
                        java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void februaryUsesParisCalendarIncludingMonthBoundaryAndLeapDay() {
        var settings = new SocialSettings(config());
        for (String date :
                new String[] {
                    "2026-01-31T23:00:00Z", "2024-02-29T12:00:00Z", "2026-02-28T22:59:59Z"
                })
            assertTrue(settings.season(Clock.fixed(Instant.parse(date), ZoneOffset.UTC)), date);
        for (String date :
                new String[] {
                    "2026-01-31T22:59:59Z", "2026-02-28T23:00:00Z", "2026-10-26T12:00:00Z"
                })
            assertFalse(settings.season(Clock.fixed(Instant.parse(date), ZoneOffset.UTC)), date);
    }

    @Test
    void allSixteenThresholdsAndLatestDivorcePriceArePreserved() {
        var settings = new SocialSettings(config());
        assertEquals(16, settings.ranks.size());
        assertEquals(100_000_000, settings.divorceCost);
        assertEquals(360000, settings.requiredTicks);
        assertEquals(3600000, settings.recommendationCooldown);
        assertEquals(500, settings.recommendationXP);
        for (var rank : settings.ranks) {
            assertEquals(rank, settings.rank(rank.threshold()));
            if (rank.threshold() > 0) assertNotEquals(rank, settings.rank(rank.threshold() - 1));
        }
        assertEquals(settings.ranks.getLast(), settings.rank(Long.MAX_VALUE));
    }

    @Test
    void malformedConfigurationIsRejectedWithoutSilentlyChangingValues() {
        for (Object bad : new Object[] {-1, Double.NaN, Double.POSITIVE_INFINITY, 1.5, "100"}) {
            var config = config();
            config.set("mariage.divorce-ecus", bad);
            assertThrows(IllegalArgumentException.class, () -> new SocialSettings(config));
        }
        var zoneConfig = config();
        zoneConfig.set("mariage.fuseau", "unknown");
        assertThrows(Exception.class, () -> new SocialSettings(zoneConfig));
        var config = config();
        config.set("mariage.objet.material", "AIR");
        var bad = config;
        assertThrows(IllegalArgumentException.class, () -> new SocialSettings(bad));
    }
}
