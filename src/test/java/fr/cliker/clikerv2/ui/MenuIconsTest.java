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

    private ItemStack button(Material type, String name) {
        var item = new ItemStack(type);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    @Test
    void usesExistingItemsAdderIdAndKeepsButtonData() {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var ids = new java.util.ArrayList<String>();
        var icons =
                new MenuIcons(
                        plugin,
                        id -> {
                            ids.add(id);
                            var icon = new ItemStack(Material.PAPER);
                            var m = icon.getItemMeta();
                            m.setCustomModelData(1234);
                            icon.setItemMeta(m);
                            return icon;
                        });
        var holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, 27);
        var item = button(Material.ARROW, "Retour");
        item.setAmount(3);
        var meta = item.getItemMeta();
        meta.lore(java.util.List.of(Component.text("Vers le menu")));
        var key = new NamespacedKey(plugin, "menu_back");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        holder.inventory.setItem(22, item);
        icons.render(holder.inventory);
        icons.render(holder.inventory);
        var result = holder.inventory.getItem(22);
        assertEquals(java.util.List.of("icons:navigation_retour"), ids);
        assertEquals(Material.PAPER, result.getType());
        assertEquals(1234, result.getItemMeta().getCustomModelData());
        assertEquals(3, result.getAmount());
        assertEquals(meta.displayName(), result.getItemMeta().displayName());
        assertEquals(meta.lore(), result.getItemMeta().lore());
        assertTrue(
                result.getItemMeta()
                        .getPersistentDataContainer()
                        .has(key, PersistentDataType.BYTE));
    }

    @Test
    void missingItemsAdderKeepsVanillaWithoutDependingOnPackStatus() {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var icons = new MenuIcons(plugin, id -> null);
        var holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, 27);
        var item = button(Material.ARROW, "Retour");
        holder.inventory.setItem(22, item);
        icons.render(holder.inventory);
        assertEquals(item, holder.inventory.getItem(22));
    }

    @Test
    void neverCallsItemsAdderForRealStorageOrForeignInventory() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var icons =
                new MenuIcons(
                        plugin,
                        id -> {
                            fail("Real inventory must not be decorated");
                            return null;
                        });
        var type = Class.forName("fr.cliker.clikerv2.bag.BagMenu$Holder");
        var constructor = type.getDeclaredConstructor(java.util.UUID.class, int.class);
        constructor.setAccessible(true);
        var holder = (InventoryHolder) constructor.newInstance(java.util.UUID.randomUUID(), 0);
        var inventory = Bukkit.createInventory(holder, 54);
        var item = button(Material.DIAMOND, "Retour");
        inventory.setItem(22, item);
        icons.render(inventory);
        assertEquals(item, inventory.getItem(22));
        var foreign = Bukkit.createInventory(null, 27);
        foreign.setItem(0, item);
        icons.render(foreign);
        assertEquals(item, foreign.getItem(0));
    }

    @Test
    void invalidationRefreshesItemsAdderTemplate() {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var icons =
                new MenuIcons(
                        plugin,
                        id -> {
                            var i = new ItemStack(Material.PAPER);
                            var m = i.getItemMeta();
                            m.setCustomModelData(calls.incrementAndGet());
                            i.setItemMeta(m);
                            return i;
                        });
        var holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, 27);
        holder.inventory.setItem(22, button(Material.ARROW, "Retour"));
        icons.render(holder.inventory);
        icons.invalidate();
        icons.render(holder.inventory);
        assertEquals(2, holder.inventory.getItem(22).getItemMeta().getCustomModelData());
    }
}
