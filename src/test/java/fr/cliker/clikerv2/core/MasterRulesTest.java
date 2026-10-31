package fr.cliker.clikerv2.core;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.daily.*;
import fr.cliker.clikerv2.essence.*;
import fr.cliker.clikerv2.gear.EnchantRoll;
import fr.cliker.clikerv2.prestige.PrestigeType;
import fr.cliker.clikerv2.security.SystemEggProtection;

import org.bukkit.*;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDate;
import java.util.List;

class MasterRulesTest {
    @Test
    void romanNumeralsOneToOneHundred() {
        String[] units = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        for (int n = 1; n < 100; n++)
            assertEquals(tens[n / 10] + units[n % 10], PrestigeType.toRoman(n));
        assertEquals("C", PrestigeType.toRoman(100));
        long previous = 0;
        for (int p = 1; p <= 100; p++) {
            assertTrue(PrestigeType.requiredKills(p) >= previous);
            previous = PrestigeType.requiredKills(p);
        }
    }

    @Test
    void enchantLevelFiveIsExactlyOneTenthPercent() {
        List<Double> weights = List.of(40.0, 30.0, 20.0, 9.9, 0.1);
        int five = 0;
        for (int i = 0; i < 100000; i++) if (EnchantRoll.level(weights, i / 1000.0) == 5) five++;
        assertEquals(100, five);
        assertThrows(IllegalArgumentException.class, () -> EnchantRoll.validate(List.of(1.0, 2.0)));
    }

    @Test
    void legacyClaimsThroughDay31RemainClaimedAfterCalendarMigration() {
        LocalDate date = LocalDate.of(2026, 10, 24);
        for (int oldDay = 29; oldDay <= 31; oldDay++) {
            var progress = new DailyProgress(date, oldDay);
            assertEquals(oldDay, progress.day());
            assertTrue(progress.claimedToday(date));
            assertEquals(oldDay == 31 ? 1 : oldDay + 1, progress.currentDay(date.plusDays(1)));
        }
    }

    @Test
    void oldTestCalendarIsBackedUpAndMigrated() throws Exception {
        MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            var file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "daily.yml").toPath();
            java.nio.file.Files.createDirectories(file.getParent());
            var old = new org.bukkit.configuration.file.YamlConfiguration();
            for (int d = 1; d <= 31; d++)
                old.set("recompenses." + d + ".description", "Récompense test — Jour " + d);
            old.save(file.toFile());
            var settings = new DailySettings(plugin);
            assertEquals(3, settings.config().getInt("schema-version"));
            assertEquals("Récompense test — Jour 1", settings.reward(1));
            assertEquals("Récompense test — Jour 31", settings.reward(31));
            assertTrue(settings.config().contains("recompenses.29"));
            try (var backups = java.nio.file.Files.list(file.getParent().resolve("backups"))) {
                assertEquals(2, backups.count());
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void onlySystemSpawnEggsAreBlockedIncludingDispensers() {
        var server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            EssenceItem.init(plugin);
            ItemStack special = EssenceItem.create(MobTier.VACHE, EssenceRarity.COMMUNE, 1);
            assertTrue(SystemEggProtection.protectedEgg(special));
            assertFalse(SystemEggProtection.protectedEgg(new ItemStack(Material.COW_SPAWN_EGG)));
            assertFalse(SystemEggProtection.protectedEgg(new ItemStack(Material.STONE)));
            var world = server.addSimpleWorld("eggs");
            var event = new BlockDispenseEvent(world.getBlockAt(0, 64, 0), special, new Vector());
            new SystemEggProtection().dispense(event);
            assertTrue(event.isCancelled());
        } finally {
            MockBukkit.unmock();
        }
    }
}
