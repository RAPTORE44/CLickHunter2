package fr.cliker.clikerv2.combat;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.essence.*;
import fr.cliker.clikerv2.gear.*;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class StarterSwordTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void end() {
        MockBukkit.unmock();
    }

    @Test
    void oldLoreIsUpdatedWithoutChangingOtherData() {
        var plugin = MockBukkit.createMockPlugin();
        new SwordCommand(plugin);
        var sword = SwordCommand.create();
        var meta = sword.getItemMeta();
        meta.lore(
                java.util.List.of(
                        net.kyori.adventure.text.Component.text("+25 Dégâts"),
                        net.kyori.adventure.text.Component.text("Mon texte")));
        sword.setItemMeta(meta);
        assertTrue(SwordCommand.refresh(sword));
        var plain =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertEquals("+5 Dégâts", plain.serialize(sword.getItemMeta().lore().getFirst()));
        assertEquals("Mon texte", plain.serialize(sword.getItemMeta().lore().get(1)));
        assertTrue(sword.getItemMeta().isUnbreakable());
        assertEquals(5, SwordCommand.BASE_DAMAGE);
    }

    @Test
    void actualUnboostedDamageIsFive() {
        var plugin = MockBukkit.createMockPlugin();
        new SwordCommand(plugin);
        TierGear.init(plugin);
        CharmItem.init(plugin);
        ItemMods.init(plugin);
        CompressorItem.init(plugin);
        var player = MockBukkit.getMock().addPlayer();
        player.getInventory().setItemInMainHand(SwordCommand.create());
        var upgrades = new fr.cliker.clikerv2.upgrade.UpgradeManager(plugin, null, null);
        var prestige = new fr.cliker.clikerv2.prestige.PrestigeManager(plugin, null, null);
        var bags = new fr.cliker.clikerv2.bag.BagManager(plugin, null);
        var mobs =
                new fr.cliker.clikerv2.mobs.CustomMobManager(
                        plugin,
                        null,
                        null,
                        upgrades,
                        null,
                        prestige,
                        new CompressorManager(),
                        bags);
        assertEquals(5L, mobs.calculateDamage(player, MobTier.VACHE));
        mobs.shutdown();
    }
}
