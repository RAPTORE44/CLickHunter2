package fr.cliker.clikerv2.combat;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.essence.*;
import fr.cliker.clikerv2.gear.*;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class GearParityTest {
    @Test
    void allNineteenSetsMatchTheirLoreAndHaveIdenticalOpAndNonOpDamage() {
        var server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            new SwordCommand(plugin);
            TierGear.init(plugin);
            CharmItem.init(plugin);
            ItemMods.init(plugin);
            CompressorItem.init(plugin);
            var player = server.addPlayer();
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
            try {
                player.getInventory().setItemInMainHand(SwordCommand.create());
                assertEquals(5L, mobs.calculateDamage(player, MobTier.values()[0]));
                var falling =
                        (org.bukkit.entity.Player)
                                java.lang.reflect.Proxy.newProxyInstance(
                                        org.bukkit.entity.Player.class.getClassLoader(),
                                        new Class<?>[] {org.bukkit.entity.Player.class},
                                        (self, method, args) ->
                                                switch (method.getName()) {
                                                    case "getFallDistance" -> 0.7f;
                                                    case "isOnGround",
                                                                    "isSprinting",
                                                                    "isInWater",
                                                                    "isInsideVehicle",
                                                                    "isClimbing",
                                                                    "isFlying",
                                                                    "isGliding",
                                                                    "hasPotionEffect" ->
                                                            false;
                                                    default -> method.invoke(player, args);
                                                });
                assertEquals(8L, mobs.calculateDamage(falling, MobTier.values()[0]));
                assertEquals(8L, mobs.calculateDamage(falling, MobTier.values()[0], true));
                for (var tier : MobTier.values()) {
                    player.getInventory().clear();
                    player.getInventory().setItemInMainHand(TierGear.createSword(tier));
                    player.setOp(false);
                    assertEquals(
                            TierGear.pieceDamage(tier),
                            mobs.calculateDamage(player, tier),
                            tier.id() + " épée seule");
                    player.getInventory()
                            .setBoots(TierGear.createArmor(tier, TierGear.ArmorSlot.BOOTS));
                    player.getInventory()
                            .setLeggings(TierGear.createArmor(tier, TierGear.ArmorSlot.LEGGINGS));
                    player.getInventory()
                            .setChestplate(
                                    TierGear.createArmor(tier, TierGear.ArmorSlot.CHESTPLATE));
                    player.getInventory()
                            .setHelmet(TierGear.createArmor(tier, TierGear.ArmorSlot.HELMET));
                    long normal = mobs.calculateDamage(player, tier);
                    assertEquals(
                            5 * TierGear.pieceDamage(tier),
                            normal,
                            tier.id() + " ensemble complet");
                    player.setOp(true);
                    assertEquals(
                            normal, mobs.calculateDamage(player, tier), tier.id() + " OP/non-OP");
                }
            } finally {
                mobs.shutdown();
            }
        } finally {
            MockBukkit.unmock();
        }
    }
}
