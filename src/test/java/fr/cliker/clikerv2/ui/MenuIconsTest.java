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
    void changedDailyStateRefreshesModelAndLoreAfterAnUnchangedFrame() {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var ids = new java.util.ArrayList<String>();
        var icons =
                new MenuIcons(
                        plugin,
                        id -> {
                            ids.add(id);
                            var item = new ItemStack(Material.PAPER);
                            var meta = item.getItemMeta();
                            meta.setCustomModelData(id.contains("cadenas") ? 10 : 20);
                            item.setItemMeta(meta);
                            return item;
                        });
        var holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, 27);
        var day = button(Material.CHEST, "Jour 2");
        var meta = day.getItemMeta();
        meta.lore(java.util.List.of(Component.text("État : verrouillé")));
        day.setItemMeta(meta);
        holder.inventory.setItem(13, day);
        icons.render(holder.inventory);
        icons.render(holder.inventory);
        assertEquals(10, holder.inventory.getItem(13).getItemMeta().getCustomModelData());
        var changed = holder.inventory.getItem(13);
        meta = changed.getItemMeta();
        meta.lore(java.util.List.of(Component.text("État : disponible")));
        changed.setItemMeta(meta);
        holder.inventory.setItem(13, changed);
        icons.render(holder.inventory);
        assertEquals(20, holder.inventory.getItem(13).getItemMeta().getCustomModelData());
        assertEquals(meta.lore(), holder.inventory.getItem(13).getItemMeta().lore());
        assertEquals(
                java.util.List.of(
                        "icons:etats_cadenas_ferme_grise", "icons:recompenses_cadeau"),
                ids);
    }

    @Test
    void shopProductModelsStayIntactWhileNavigationUsesIcons() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var icons = new MenuIcons(plugin, id -> new ItemStack(Material.PAPER));
        var menus = new fr.cliker.clikerv2.features.Menus(plugin);
        var view = menus.create(server.addPlayer(), 54, "Boutique");
        view.merchandise = true;
        view.put(0, button(Material.DIAMOND_SWORD, "Épée"), null);
        view.put(1, button(Material.DIAMOND_CHESTPLATE, "Armure"), null);
        view.put(49, button(Material.ARROW, "Retour"), null);
        icons.render(view.getInventory());
        assertEquals(Material.DIAMOND_SWORD, view.getInventory().getItem(0).getType());
        assertEquals(Material.DIAMOND_CHESTPLATE, view.getInventory().getItem(1).getType());
        assertEquals(Material.PAPER, view.getInventory().getItem(49).getType());
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
