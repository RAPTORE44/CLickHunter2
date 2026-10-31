package fr.cliker.clikerv2.lootbox;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;

import org.bukkit.Material;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class PrestigeLootboxesTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void close() {
        MockBukkit.unmock();
    }

    @Test
    void prestigeStartsWithLowestAndStopsAtDivine() throws Exception {
        var p = MockBukkit.createMockPlugin();
        var db = new DatabaseManager(p, "p.db");
        try {
            db.initialize();
            var service = new PrestigeLootboxes(p, db);
            assertEquals(LootboxTier.COMMUN, service.tier(0));
            assertEquals(LootboxTier.PEU_COMMUN, service.tier(1));
            assertEquals(LootboxTier.RARE, service.tier(2));
            assertEquals(LootboxTier.DIVIN, service.tier(100));
            service.shutdown();
        } finally {
            db.close();
        }
    }

    @Test
    void allCratesUseEnderChestAndHaveNoGemOrAshGains() {
        var p = MockBukkit.createMockPlugin();
        LootboxItem.init(p);
        LootboxTier.load(p);
        for (var t : LootboxTier.values()) {
            assertEquals(Material.ENDER_CHEST, LootboxItem.create(t).getType());
            assertEquals(0, t.cendres());
            assertEquals(0, t.gemmesSombresTenths());
        }
    }
}
