package fr.cliker.clikerv2.features;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.*;

public final class Menus implements Listener {
    private final JavaPlugin plugin;

    public Menus(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public View create(Player p, int size, String title) {
        return new View(p.getUniqueId(), size, title);
    }

    public static ItemStack item(Material m, String title, String... lines) {
        var i = new ItemStack(m);
        var meta = i.getItemMeta();
        meta.displayName(
                fr.cliker.clikerv2.daily.DailySettings.color(title)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(
                Arrays.stream(lines)
                        .map(fr.cliker.clikerv2.daily.DailySettings::color)
                        .map(
                                c ->
                                        c.decoration(
                                                net.kyori.adventure.text.format.TextDecoration
                                                        .ITALIC,
                                                false))
                        .toList());
        i.setItemMeta(meta);
        return i;
    }

    public final class View implements InventoryHolder {
        final UUID owner;
        final Map<Integer, BiConsumer<Player, Boolean>> actions = new HashMap<>();
        final Inventory inventory;
        public boolean pending;
        public boolean merchandise;
        public boolean vanilla;

        View(UUID owner, int size, String title) {
            this.owner = owner;
            inventory = Bukkit.createInventory(this, size, Component.text(title));
            for (int i = 0; i < size; i++)
                inventory.setItem(i, item(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        public Inventory getInventory() {
            return inventory;
        }

        public void put(int slot, ItemStack i, BiConsumer<Player, Boolean> action) {
            inventory.setItem(slot, i);
            if (action != null) actions.put(slot, action);
        }

        public void show(Player p) {
            p.openInventory(inventory);
        }
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof View v)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)
                || !v.owner.equals(p.getUniqueId())
                || v.pending
                || e.getClickedInventory() != v.inventory
                || (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT)) return;
        var action = v.actions.get(e.getRawSlot());
        if (action != null)
            fr.cliker.clikerv2.runtime.ServerTasks.run(
                    plugin,
                    () -> {
                        if (p.isOnline()
                                && p.getOpenInventory().getTopInventory() == v.inventory
                                && !v.pending) action.accept(p, e.getClick() == ClickType.RIGHT);
                    });
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }
}
