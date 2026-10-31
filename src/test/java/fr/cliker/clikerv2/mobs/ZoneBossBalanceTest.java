package fr.cliker.clikerv2.mobs;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class ZoneBossBalanceTest {
    @BeforeEach
    void before() {
        MockBukkit.mock();
    }

    @AfterEach
    void after() {
        MockBukkit.unmock();
    }

    @Test
    void legacyMultiplierMigratesToOneThousandAndLaterCustomValueIsKept() throws Exception {
        var plugin = MockBukkit.createMockPlugin();
        var file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "boss.yml");
        var old = new YamlConfiguration();
        old.set("multiplicateur-pv", 100);
        old.save(file);
        var manager = new CustomMobManager(plugin, null, null, null, null, null, null, null);
        assertEquals(
                Math.round(MobTier.VACHE.maxHealth() * 1000), manager.bossHealth(MobTier.VACHE));
        assertEquals(
                Math.round(MobTier.WARDEN.maxHealth() * 1000), manager.bossHealth(MobTier.WARDEN));
        manager.shutdown();
        var custom = YamlConfiguration.loadConfiguration(file);
        custom.set("multiplicateur-pv", 1500);
        custom.save(file);
        manager = new CustomMobManager(plugin, null, null, null, null, null, null, null);
        assertEquals(
                Math.round(MobTier.VACHE.maxHealth() * 1500), manager.bossHealth(MobTier.VACHE));
        manager.shutdown();
    }
}
