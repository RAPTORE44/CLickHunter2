package fr.cliker.clikerv2.hunting;

import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.economy.CurrencyType;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.IntConsumer;

/** Inventaires de consultation : aucun objet ne peut être emporté. */
public final class HuntUI implements Listener {
    public static final class View implements InventoryHolder {
        final UUID owner;
        final Inventory inventory;
        final IntConsumer action;
        final IntConsumer rightAction;
        boolean pending;

        View(Player player, String title, int size, IntConsumer action, IntConsumer rightAction) {
            owner = player.getUniqueId();
            this.action = action;
            this.rightAction = rightAction;
            inventory = Bukkit.createInventory(this, size, DailySettings.color(title));
            for (int i = 0; i < size; i++)
                inventory.setItem(i, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final JavaPlugin plugin;

    public HuntUI(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static View view(Player player, String title, int size, IntConsumer action) {
        return new View(player, title, size, action, slot -> {});
    }

    public static View view(
            Player player, String title, int size, IntConsumer action, IntConsumer rightAction) {
        return new View(player, title, size, action, rightAction);
    }

    public static ItemStack item(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(
                DailySettings.color(title)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(
                lore.stream()
                        .map(
                                line ->
                                        DailySettings.color(line)
                                                .decoration(
                                                        net.kyori.adventure.text.format
                                                                .TextDecoration.ITALIC,
                                                        false))
                        .toList());
        item.setItemMeta(meta);
        return item;
    }

    public static List<String> gains(Gains gains) {
        List<String> lines = new ArrayList<>();
        for (var currency : CurrencyType.values())
            lines.add(
                    "&7"
                            + currency.displayName()
                            + " : &f"
                            + currency.format(gains.amount(currency)));
        lines.add("&7XP : &b" + gains.xp());
        return lines;
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View view) {
            e.setCancelled(true);
            if (e.getWhoClicked().getUniqueId().equals(view.owner)
                    && e.getRawSlot() >= 0
                    && e.getRawSlot() < view.inventory.getSize()
                    && (e.getClick().isLeftClick() || e.getClick().isRightClick())
                    && !view.pending) {
                view.pending = true;
                int slot = e.getRawSlot();
                IntConsumer action = e.getClick().isRightClick() ? view.rightAction : view.action;
                fr.cliker.clikerv2.runtime.ServerTasks.run(
                        plugin,
                        () -> {
                            view.pending = false;
                            if (e.getWhoClicked().getOpenInventory().getTopInventory()
                                    == view.inventory) action.accept(slot);
                        });
            }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }

    public void shutdown() {
        for (var player : Bukkit.getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof View)
                player.closeInventory();
        HandlerList.unregisterAll(this);
    }
}
