package fr.cliker.clikerv2.essence;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CompressorActivationTest {
    private PlayerMock player;

    @BeforeEach
    void setup() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        CompressorItem.init(plugin);
        EssenceItem.init(plugin);
        player = server.addPlayer();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    private PlayerInteractEvent use(EquipmentSlot hand, ItemStack snapshot) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, snapshot, null, null, hand);
    }

    @Test
    void cancelledAirBlockResultStillAllowsItemButExplicitItemDenialDoesNot() {
        var item = CompressorItem.create(MobTier.VACHE);
        player.getInventory().setItemInMainHand(item);
        var plugin = MockBukkit.createMockPlugin();
        var manager = new CompressorManager(p -> {});
        MockBukkit.getMock().getPluginManager().registerEvents(manager, plugin);
        var denied = use(EquipmentSlot.HAND, item.clone());
        denied.setCancelled(true);
        MockBukkit.getMock().getPluginManager().callEvent(denied);
        assertEquals(item, player.getInventory().getItemInMainHand());
        var air = use(EquipmentSlot.HAND, item.clone());
        air.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        air.setUseItemInHand(org.bukkit.event.Event.Result.DEFAULT);
        assertTrue(air.isCancelled());
        MockBukkit.getMock().getPluginManager().callEvent(air);
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());
        assertTrue(
                player.getPersistentDataContainer()
                        .has(CompressorItem.activationKey(MobTier.VACHE), PersistentDataType.BYTE));
    }

    @Test
    void inactiveCompressorDoesNotReadOrCloneInventoryOnEveryHit() {
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var probe =
                new org.mockbukkit.mockbukkit.entity.PlayerMock(MockBukkit.getMock(), "Inactive") {
                    @Override
                    public org.bukkit.inventory.PlayerInventory getInventory() {
                        reads.incrementAndGet();
                        return super.getInventory();
                    }
                };
        MockBukkit.getMock().addPlayer(probe);
        reads.set(0);
        for (int i = 0; i < 100; i++) new CompressorManager(p -> {}).onHit(probe);
        assertEquals(0, reads.get());
    }

    @Test
    void consumesActualHandEvenWhenEventContainsACopy() {
        var item = CompressorItem.create(MobTier.VACHE);
        item.setAmount(2);
        player.getInventory().setItemInMainHand(item);
        var saves = new java.util.concurrent.atomic.AtomicInteger();
        new CompressorManager(p -> saves.incrementAndGet())
                .onUse(use(EquipmentSlot.HAND, item.clone()));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertTrue(
                player.getPersistentDataContainer()
                        .has(CompressorItem.activationKey(MobTier.VACHE), PersistentDataType.BYTE));
        assertEquals(1, saves.get());
    }

    @Test
    void failedSaveRestoresObjectAndActivationFlag() {
        var item = CompressorItem.create(MobTier.VACHE);
        player.getInventory().setItemInMainHand(item);
        new CompressorManager(
                        p -> {
                            throw new IllegalStateException("Injected storage failure");
                        })
                .onUse(use(EquipmentSlot.HAND, item.clone()));
        assertEquals(item, player.getInventory().getItemInMainHand());
        assertFalse(
                player.getPersistentDataContainer()
                        .has(CompressorItem.activationKey(MobTier.VACHE), PersistentDataType.BYTE));
    }

    @Test
    void offhandActivationDoesNotConsumeMainHandAndDuplicateUseDoesNotConsumeAgain() {
        var item = CompressorItem.create(MobTier.VACHE);
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND));
        player.getInventory().setItemInOffHand(item);
        var manager = new CompressorManager(p -> {});
        manager.onUse(use(EquipmentSlot.OFF_HAND, item.clone()));
        assertEquals(Material.DIAMOND, player.getInventory().getItemInMainHand().getType());
        assertTrue(player.getInventory().getItemInOffHand().getType().isAir());
        player.getInventory().setItemInOffHand(item);
        manager.onUse(use(EquipmentSlot.OFF_HAND, item.clone()));
        assertEquals(item, player.getInventory().getItemInOffHand());
    }

    @Test
    void staleEventCannotConsumeDifferentHeldCompressor() {
        var actual = CompressorItem.create(MobTier.COCHON);
        player.getInventory().setItemInMainHand(actual);
        new CompressorManager(p -> fail("Save must not run"))
                .onUse(use(EquipmentSlot.HAND, CompressorItem.create(MobTier.VACHE)));
        assertEquals(actual, player.getInventory().getItemInMainHand());
        assertFalse(
                player.getPersistentDataContainer()
                        .has(CompressorItem.activationKey(MobTier.VACHE), PersistentDataType.BYTE));
    }
}
