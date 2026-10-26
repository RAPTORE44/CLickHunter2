package fr.cliker.clikerv2.daily;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;

class DailySettingsTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void close() {
        MockBukkit.unmock();
    }

    @Test
    void calendarMigrationAddsMissingDaysWithoutReplacingCustomRewardsOrExisting31DayData()
            throws Exception {
        var plugin = MockBukkit.createMockPlugin();
        var file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "daily.yml");
        try (var in = getClass().getResourceAsStream("/daily.yml")) {
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        var old = YamlConfiguration.loadConfiguration(file);
        old.set("schema-version", 2);
        old.set("recompenses.1.description", "Mon cadeau personnalisé");
        old.set("recompenses.1.commandes", List.of("test cadeau"));
        old.set("recompenses.29", null);
        old.set("recompenses.30", null);
        old.set("recompenses.31", null);
        old.set(
                "interface.icones.informations.description",
                List.of("Jour {jour}/28", "Après le jour 28 : nouveau cycle"));
        old.save(file);
        var settings = new DailySettings(plugin);
        assertEquals(3, settings.config().getInt("schema-version"));
        assertEquals("Mon cadeau personnalisé", settings.reward(1));
        assertEquals(List.of("test cadeau"), settings.commands(1));
        for (int day = 29; day <= 31; day++) assertFalse(settings.commands(day).isEmpty());
        assertEquals(
                List.of("Jour {jour}/31", "Après le jour 31 : nouveau cycle"),
                settings.config().getStringList("interface.icones.informations.description"));
        try (var paths = Files.list(file.toPath().getParent().resolve("backups"))) {
            assertTrue(paths.findAny().isPresent());
        }
        for (int day = 29; day <= 31; day++)
            assertEquals(day, new DailyProgress(LocalDate.of(2026, 10, 26), day).day());
    }
}
