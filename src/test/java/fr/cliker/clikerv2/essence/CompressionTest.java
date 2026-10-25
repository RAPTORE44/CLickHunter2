package fr.cliker.clikerv2.essence;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.*;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class CompressionTest {
    private DatabaseManager db;
    private ClaimService claims;
    private org.mockbukkit.mockbukkit.entity.PlayerMock player;

    @BeforeEach
    void setup() throws Exception {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "compression-" + UUID.randomUUID() + ".db");
        db.initialize();
        claims = ClaimTestFactory.create(plugin, db);
        Bukkit.getServicesManager()
                .register(ClaimService.class, claims, plugin, ServicePriority.Normal);
        EssenceItem.init(plugin);
        CompressorItem.init(plugin);
        player = server.addPlayer();
    }

    @AfterEach
    void cleanup() {
        try {
            claims.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void bulkConversionPreservesValueAcrossBothRaritySteps() {
        player.getInventory()
                .setItem(0, EssenceItem.create(MobTier.VACHE, EssenceRarity.COMMUNE, 64));
        player.getInventory()
                .setItem(1, EssenceItem.create(MobTier.VACHE, EssenceRarity.COMMUNE, 64));
        player.getInventory()
                .setItem(2, EssenceItem.create(MobTier.VACHE, EssenceRarity.PEU_COMMUNE, 64));
        assertEquals(3, EssenceCompression.cascade(player, MobTier.VACHE));
        assertEquals(0, EssenceItem.count(player, MobTier.VACHE, EssenceRarity.COMMUNE));
        assertEquals(2, EssenceItem.count(player, MobTier.VACHE, EssenceRarity.PEU_COMMUNE));
        assertEquals(1, EssenceItem.count(player, MobTier.VACHE, EssenceRarity.RARE));
    }

    @Test
    void onlyOwnedCompressorTypesApplyIncludingOffhand() {
        player.getInventory().setItemInOffHand(CompressorItem.create(MobTier.VACHE));
        player.getInventory().setItem(0, EssenceItem.create(MobTier.VACHE, 64));
        player.getInventory().setItem(1, EssenceItem.create(MobTier.COCHON, 64));
        new CompressorManager().onHit(player);
        assertEquals(1, EssenceItem.count(player, MobTier.VACHE, EssenceRarity.PEU_COMMUNE));
        assertEquals(64, EssenceItem.count(player, MobTier.COCHON, EssenceRarity.COMMUNE));
    }
}
