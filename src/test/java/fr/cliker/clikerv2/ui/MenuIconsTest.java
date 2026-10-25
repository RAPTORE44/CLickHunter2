package fr.cliker.clikerv2.ui;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class MenuIconsTest {
    static class Holder implements InventoryHolder {
        Inventory inventory;

        public Inventory getInventory() {
            return inventory;
        }
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void realBagStorageIsExcludedEvenForNamedItems() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var icons = new MenuIcons(plugin);
        var type = Class.forName("fr.cliker.clikerv2.bag.BagMenu$Holder");
        var constructor = type.getDeclaredConstructor(java.util.UUID.class, int.class);
        constructor.setAccessible(true);
        var holder = (InventoryHolder) constructor.newInstance(java.util.UUID.randomUUID(), 0);
        var inventory = Bukkit.createInventory(holder, 54);
        var item = new ItemStack(Material.DIAMOND);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Retour"));
        item.setItemMeta(meta);
        inventory.setItem(22, item);
        icons.render(inventory);
        assertEquals(item, inventory.getItem(22));

    }

    @Test
    void vanillaFallbackUntilResourcePackIsConfirmed() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var player = server.addPlayer();
        var icons = new MenuIcons(plugin);
        icons.start();
        var holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, 27);
        var item = new ItemStack(Material.ARROW);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Retour"));
        item.setItemMeta(meta);
        holder.inventory.setItem(22, item);
        player.openInventory(holder.inventory);
        assertNull(holder.inventory.getItem(22).getItemMeta().getItemModel());
        icons.pack(
                new org.bukkit.event.player.PlayerResourcePackStatusEvent(
                        player,
                        java.util.UUID.randomUUID(),
                        org.bukkit.event.player.PlayerResourcePackStatusEvent.Status
                                .SUCCESSFULLY_LOADED));
        server.getScheduler().performTicks(5);
        assertEquals(
                new NamespacedKey("clickhunter", "navigation_retour"),
                holder.inventory.getItem(22).getItemMeta().getItemModel());
        icons.shutdown();
    }

    @Test
    void buttonKeepsMaterialNameLoreBusinessDataAndAmount() {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var icons = new MenuIcons(plugin);
        var holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, 27);
        var item = new ItemStack(Material.ARROW, 3);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Retour"));
        meta.lore(java.util.List.of(Component.text("Vers le menu")));
        var key = new NamespacedKey(plugin, "menu_back");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        holder.inventory.setItem(22, item);
        icons.render(holder.inventory);
        var result = holder.inventory.getItem(22);
        assertEquals(Material.ARROW, result.getType());
        assertEquals(3, result.getAmount());
        assertEquals(meta.displayName(), result.getItemMeta().displayName());
        assertEquals(meta.lore(), result.getItemMeta().lore());
        assertTrue(
                result.getItemMeta()
                        .getPersistentDataContainer()
                        .has(key, PersistentDataType.BYTE));
        assertEquals(
                new NamespacedKey("clickhunter", "navigation_retour"),
                result.getItemMeta().getItemModel());
    }

    @Test
    void backgroundAndForeignInventoriesAreNotReplaced() {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var icons = new MenuIcons(plugin);
        var inventory = Bukkit.createInventory(null, 27);
        var item = new ItemStack(Material.DIAMOND);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Retour"));
        item.setItemMeta(meta);
        inventory.setItem(0, item);
        icons.render(inventory);
        assertEquals(item, inventory.getItem(0));
    }
}
