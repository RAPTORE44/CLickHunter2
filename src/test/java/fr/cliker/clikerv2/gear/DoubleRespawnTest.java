package fr.cliker.clikerv2.gear;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.concurrent.atomic.AtomicInteger;

class DoubleRespawnTest {
    ServerMock server;
    org.mockbukkit.mockbukkit.entity.PlayerMock player;
    Location origin;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        TierGear.init(plugin);
        ItemMods.init(plugin);
        var world = server.addSimpleWorld("world");
        player = server.addPlayer();
        origin = new Location(world, 0, 65, 0);
        player.getInventory().setItemInMainHand(TierGear.createSword(MobTier.VACHE));
    }

    @AfterEach
    void end() {
        MockBukkit.unmock();
    }

    @Test
    void requiresEnchantAndSpawnsCorrectVictimWithoutMovingAnchor() {
        var calls = new AtomicInteger();
        assertFalse(
                DoubleRespawn.trigger(
                        player, MobTier.COCHON, origin, 0, (t, p) -> calls.incrementAndGet()));
        var sword = player.getInventory().getItemInMainHand();
        ItemMods.setEnchantLevel(sword, EnchantType.DOUBLE_RESPAWN, 5);
        player.getInventory().setItemInMainHand(sword);
        assertTrue(
                DoubleRespawn.trigger(
                        player,
                        MobTier.COCHON,
                        origin,
                        9.99,
                        (t, p) -> {
                            assertEquals(MobTier.COCHON, t);
                            assertEquals(.8, p.getX());
                            calls.incrementAndGet();
                        }));
        assertFalse(
                DoubleRespawn.trigger(
                        player, MobTier.COCHON, origin, 10, (t, p) -> calls.incrementAndGet()));
        assertEquals(1, calls.get());
        assertEquals(0, origin.getX());
    }

    @Test
    void capacityLimitDoesNotBreakKillOrCreateMoreMobs() {
        var sword = player.getInventory().getItemInMainHand();
        ItemMods.setEnchantLevel(sword, EnchantType.DOUBLE_RESPAWN, 1);
        player.getInventory().setItemInMainHand(sword);
        assertFalse(
                DoubleRespawn.trigger(
                        player,
                        MobTier.VACHE,
                        origin,
                        0,
                        (t, p) -> {
                            throw new IllegalStateException("capacity");
                        }));
    }
}
