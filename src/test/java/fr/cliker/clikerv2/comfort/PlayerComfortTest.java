package fr.cliker.clikerv2.comfort;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PlayerComfortTest {
    private ServerMock server;
    private PlayerMock player;
    private Item item;
    private PlayerComfort comfort;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var world = server.addSimpleWorld("comfort");
        player = server.addPlayer();
        player.addAttachment(plugin, "cliker.comfort.use", true);
        item = world.dropItem(world.getSpawnLocation(), new ItemStack(Material.DIAMOND));
        fr.cliker.clikerv2.gear.TierGear.init(plugin);
        fr.cliker.clikerv2.essence.CompressorItem.init(plugin);
        new fr.cliker.clikerv2.combat.SwordCommand(plugin);
        comfort = new PlayerComfort(plugin);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    private void command(String name, String... args) {
        comfort.onCommand(
                player,
                new Command(name) {
                    @Override
                    public boolean execute(CommandSender sender, String label, String[] args) {
                        return true;
                    }
                },
                name,
                args);
    }

    @Test
    void messagePreferencesAreIndependentAndDoNotChangePickupAuthorization() {
        command("settings");
        for (int slot : new int[] {17, 4}) {
            var event =
                    new org.bukkit.event.inventory.InventoryClickEvent(
                            player.getOpenInventory(),
                            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                            slot,
                            org.bukkit.event.inventory.ClickType.LEFT,
                            org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
            comfort.click(event);
            server.getScheduler().performOneTick();
        }
        assertFalse(PlayerComfort.enabled(player, "common_drop_messages", true));
        assertFalse(PlayerComfort.enabled(player, "farm_actionbar", true));
        var pickup = new EntityPickupItemEvent(player, item, 0);
        comfort.pickup(pickup);
        assertTrue(pickup.isCancelled());
    }

    @Test
    void protectsTrapdoorsButKeepsChestInteractionAndItemUse() {
        var block = player.getWorld().getBlockAt(0, 65, 0);
        block.setType(Material.OAK_TRAPDOOR);
        var event =
                new PlayerInteractEvent(
                        player,
                        org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                        new ItemStack(Material.WOODEN_SWORD),
                        block,
                        org.bukkit.block.BlockFace.UP,
                        org.bukkit.inventory.EquipmentSlot.HAND);
        var originalItemUse = event.useItemInHand();
        comfort.decoration(event);
        assertEquals(org.bukkit.event.Event.Result.DENY, event.useInteractedBlock());
        assertEquals(originalItemUse, event.useItemInHand());
        block.setType(Material.CHEST);
        var chest =
                new PlayerInteractEvent(
                        player,
                        org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                        new ItemStack(Material.WOODEN_SWORD),
                        block,
                        org.bukkit.block.BlockFace.UP,
                        org.bukkit.inventory.EquipmentSlot.HAND);
        var before = chest.useInteractedBlock();
        comfort.decoration(chest);
        assertEquals(before, chest.useInteractedBlock());
    }

    @Test
    void starterSwordIsAlsoLockable() {
        player.getInventory().setItemInMainHand(fr.cliker.clikerv2.combat.SwordCommand.create());
        command("lock");
        assertTrue(PlayerComfort.locked(player.getInventory().getItemInMainHand()));
    }

    @Test
    void lockedEquipmentRemainsProtectedAfterSessionAuthorization() {
        player.getInventory()
                .setItemInMainHand(
                        fr.cliker.clikerv2.gear.TierGear.createSword(
                                fr.cliker.clikerv2.MobTier.VACHE));
        command("lock");
        var held = player.getInventory().getItemInMainHand();
        assertTrue(PlayerComfort.locked(held));
        command("antidrop", "autoriser");
        item.setItemStack(held);
        var drop = new PlayerDropItemEvent(player, item);
        comfort.drop(drop);
        assertTrue(drop.isCancelled());
        assertTrue(PlayerComfort.protectedForSale(player, held));
        command("lock");
        assertFalse(PlayerComfort.locked(player.getInventory().getItemInMainHand()));
    }

    @Test
    void inventoryPreferencesToggleAndCannotBeDraggedOut() {
        command("settings");
        var view = player.getOpenInventory();
        var click =
                new org.bukkit.event.inventory.InventoryClickEvent(
                        view,
                        org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                        11,
                        org.bukkit.event.inventory.ClickType.LEFT,
                        org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        comfort.click(click);
        assertTrue(click.isCancelled());
        assertFalse(PlayerComfort.enabled(player, "damage_texts", true));
        server.getScheduler().performOneTick();
        var drag =
                new org.bukkit.event.inventory.InventoryDragEvent(
                        player.getOpenInventory(),
                        new ItemStack(Material.DIAMOND),
                        new ItemStack(Material.DIAMOND),
                        false,
                        java.util.Map.of(0, new ItemStack(Material.DIAMOND)));
        comfort.drag(drag);
        assertTrue(drag.isCancelled());
        comfort.quit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        assertFalse(PlayerComfort.enabled(player, "damage_texts", true));
    }

    @Test
    void dropsBlockedByDefaultAndPermissionLastsOnlyThisSession() {
        var before = new PlayerDropItemEvent(player, item);
        comfort.drop(before);
        assertTrue(before.isCancelled());
        command("antidrop", "autoriser");
        var allowed = new PlayerDropItemEvent(player, item);
        comfort.drop(allowed);
        assertFalse(allowed.isCancelled());
        comfort.quit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        var after = new PlayerDropItemEvent(player, item);
        comfort.drop(after);
        assertTrue(after.isCancelled());
    }

    @Test
    void pickupRequiresSeparateAuthorizationAndResetsOnQuit() {
        command("antidrop", "autoriser");
        var before = new EntityPickupItemEvent(player, item, 0);
        comfort.pickup(before);
        assertTrue(before.isCancelled());
        command("ramassage", "autoriser");
        var allowed = new EntityPickupItemEvent(player, item, 0);
        comfort.pickup(allowed);
        assertFalse(allowed.isCancelled());
        comfort.quit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        var after = new EntityPickupItemEvent(player, item, 0);
        comfort.pickup(after);
        assertTrue(after.isCancelled());
    }

    @Test
    void invalidCommandNeverOpensProtection() {
        command("antidrop", "foo");
        var event = new PlayerDropItemEvent(player, item);
        comfort.drop(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void onlyFallDamageIsCancelledAndHungerStaysFull() {
        var fall = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, 10);
        comfort.fall(fall);
        assertTrue(fall.isCancelled());
        var attack = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10);
        comfort.fall(attack);
        assertFalse(attack.isCancelled());
        var food = new FoodLevelChangeEvent(player, 10);
        comfort.hunger(food);
        assertEquals(20, food.getFoodLevel());
    }
}
