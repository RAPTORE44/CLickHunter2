package fr.cliker.clikerv2.mobs;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class VanillaRewardsListenerTest {
    @Test
    void hensCannotLayVanillaEggsButSpawnEggsArePreserved() {
        var server = org.mockbukkit.mockbukkit.MockBukkit.mock();
        try {
            var plugin = org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin();
            var world = server.addSimpleWorld("world");
            var listener = new VanillaRewardsListener(plugin);
            var chicken =
                    world.spawnEntity(
                            new org.bukkit.Location(world, 0, 65, 0),
                            org.bukkit.entity.EntityType.CHICKEN);
            for (var material :
                    java.util.List.of(org.bukkit.Material.EGG, org.bukkit.Material.COW_SPAWN_EGG)) {
                var item =
                        world.dropItem(
                                chicken.getLocation(),
                                new org.bukkit.inventory.ItemStack(material));
                var event = new org.bukkit.event.entity.EntityDropItemEvent(chicken, item);
                listener.egg(event);
                assertEquals(material == org.bukkit.Material.EGG, event.isCancelled());
            }
        } finally {
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
        }
    }

    @Test
    void onlyVanillaAdvancementsAreBlocked() {
        assertTrue(VanillaRewardsListener.blocked(NamespacedKey.minecraft("story/mine_stone")));
        assertFalse(
                VanillaRewardsListener.blocked(
                        NamespacedKey.minecraft("recipes/tools/stone_pickaxe")));
        assertFalse(VanillaRewardsListener.blocked(new NamespacedKey("cliker", "reward")));
    }
}
