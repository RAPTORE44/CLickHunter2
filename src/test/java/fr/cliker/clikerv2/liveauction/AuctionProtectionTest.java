package fr.cliker.clikerv2.liveauction;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.config.YamlFiles;
import fr.cliker.clikerv2.features.Settings;

import org.bukkit.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class AuctionProtectionTest {
    @AfterEach
    void close() {
        MockBukkit.unmock();
    }

    @Test
    void protectsAllWorldHeightsWithinOneHundredBlocks() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var world = server.addSimpleWorld("auction");
        var settings = new Settings(plugin, "protection-test.yml");
        YamlFiles.location(settings.yaml, "salle", new Location(world, 0, 64, 0));
        var protection = new AuctionProtection(settings);
        assertTrue(protection.inside(new Location(world, 100, -64, -100)));
        assertTrue(protection.inside(new Location(world, -100, 320, 100)));
        assertFalse(protection.inside(new Location(world, 100.1, 64, 0)));
        assertFalse(protection.inside(new Location(server.addSimpleWorld("other"), 0, 64, 0)));
        var player = server.addPlayer();
        player.teleport(new Location(world, 0, 64, 0));
        player.setOp(true);
        var damage = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, 5);
        protection.damage(damage);
        assertTrue(damage.isCancelled());
        var food = new FoodLevelChangeEvent(player, 10);
        protection.hunger(food);
        assertTrue(food.isCancelled());
        var block = new BlockBreakEvent(world.getBlockAt(0, 63, 0), player);
        protection.breakBlock(block);
        assertTrue(block.isCancelled());
    }
}
