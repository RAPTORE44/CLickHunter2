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
