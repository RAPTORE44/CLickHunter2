package fr.cliker.clikerv2.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;

/** Bouton commun aux menus consultatifs ; les coffres de stockage gardent leurs 54 cases. */
public final class MenuNavigation implements Listener {
    private final Plugin plugin;
    private final NamespacedKey key;
    private final Set<UUID> pending = new HashSet<>();

    public MenuNavigation(Plugin plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "menu_back");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void back(Plugin plugin, Inventory inventory, int slot) {
        var item = new ItemStack(Material.ARROW);
        var meta = item.getItemMeta();
        meta.displayName(
                Component.text("Retour au menu", NamedTextColor.GREEN)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer()
                .set(new NamespacedKey(plugin, "menu_back"), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        var inventory = event.getView().getTopInventory();
        var holder = inventory.getHolder();
        if (holder == null
                || !holder.getClass().getName().startsWith("fr.cliker.clikerv2.")
                || event.getClickedInventory() != inventory) return;
        var item = event.getCurrentItem();
        if (item == null
                || !item.hasItemMeta()
                || !item.getItemMeta()
                        .getPersistentDataContainer()
                        .has(key, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                || !pending.add(player.getUniqueId())) return;
        fr.cliker.clikerv2.runtime.ServerTasks.run(
                plugin,
                () -> {
                    pending.remove(player.getUniqueId());
                    if (!player.isOnline()
                            || player.getOpenInventory().getTopInventory() != inventory) return;
                    player.closeInventory();
                    player.performCommand("menu");
                });
    }
}
