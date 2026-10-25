package fr.cliker.clikerv2.display;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.prestige.PrestigeManager;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class TabListManagerTest {
    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void rotatingLinksSwitchEveryTenSecondsAndLoop() {
        assertEquals(0, TabListManager.rotatingIndex(0, 10, 4));
        assertEquals(0, TabListManager.rotatingIndex(9999, 10, 4));
        assertEquals(1, TabListManager.rotatingIndex(10000, 10, 4));
        assertEquals(3, TabListManager.rotatingIndex(39999, 10, 4));
        assertEquals(0, TabListManager.rotatingIndex(40000, 10, 4));
    }

    @Test
    void compactLevelsKeepBoundariesAndRequestedPrecision() {
        assertEquals("0", TabListManager.compactLevel(0));
        assertEquals("999", TabListManager.compactLevel(999));
        assertEquals("1k", TabListManager.compactLevel(1000));
        assertEquals("1.2k", TabListManager.compactLevel(1250));
        assertEquals("1.5k", TabListManager.compactLevel(1500));
        assertEquals("5.5k", TabListManager.compactLevel(5500));
        assertEquals("99k", TabListManager.compactLevel(99000));
        assertEquals("2147483.6k", TabListManager.compactLevel(Integer.MAX_VALUE));
    }

    @Test
    void colorRangesChangeAtLevelTenHundredAndThousandBoundaries() {
        assertEquals(0, TabListManager.colorBand(1));
        assertEquals(9, TabListManager.colorBand(10));
        assertEquals(1, TabListManager.colorBand(11));
        assertEquals(1, TabListManager.colorBand(20));
        assertEquals(2, TabListManager.colorBand(21));
        assertEquals(9, TabListManager.colorBand(100));
        assertEquals(1, TabListManager.colorBand(101));
        assertEquals(2, TabListManager.colorBand(201));
        assertEquals(9, TabListManager.colorBand(1000));
        assertEquals(9, TabListManager.colorBand(Integer.MAX_VALUE));
    }

    @Test
    void tabUsesPlayerLevelAndMostSpecificInheritedRankAfterName() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var player = server.addPlayer();
        player.setLevel(1250);
        player.addAttachment(plugin, "group.vip", true);
        player.addAttachment(plugin, "group.vipplus", true);
        var manager = new TabListManager(plugin, new PrestigeManager(plugin, null, null));
        String text =
                PlainTextComponentSerializer.plainText().serialize(manager.buildTabName(player));
        assertTrue(text.contains("VIP+"));
        assertTrue(text.contains(player.getName() + " [0 - 1.2k]"));
        player.setOp(true);
        assertTrue(
                PlainTextComponentSerializer.plainText()
                        .serialize(manager.buildTabName(player))
                        .startsWith("Staff"));
    }
}
