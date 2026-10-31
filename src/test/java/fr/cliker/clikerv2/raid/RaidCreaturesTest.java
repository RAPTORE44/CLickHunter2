package fr.cliker.clikerv2.raid;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class RaidCreaturesTest {
    private org.bukkit.plugin.java.JavaPlugin plugin;

    @BeforeEach
    void before() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void after() {
        MockBukkit.unmock();
    }

    @Test
    void cowAndPigRaidsUseTheirOwnSpecies() {
        assertEquals(
                EntityType.COW,
                RaidModule.managed(plugin, null, null, "arena", 1, false, () -> {}).creatureType());
        assertEquals(
                EntityType.PIG,
                RaidModule.managed(plugin, null, null, "arena", 2, false, () -> {}).creatureType());
    }

    @Test
    void everyZoneKeepsItsSpeciesForPrivateRaidAndPublicBoss() {
        for (var tier : MobTier.values())
            for (boolean boss : java.util.List.of(false, true)) {
                var raid =
                        RaidModule.managed(
                                plugin, null, null, "arena", tier.ordinal() + 1, boss, () -> {});
                assertEquals(tier.entityType(), raid.creatureType());
            }
    }

    @Test
    void invalidDifficultyCannotCreateAScaledRaid() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RaidModule.managed(plugin, null, null, "arena", 0, false, () -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> RaidModule.managed(plugin, null, null, "arena", 20, false, () -> {}));
    }
}
