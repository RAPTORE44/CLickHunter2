package fr.cliker.clikerv2.prestige;

import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.display.TabListManager;
import fr.cliker.clikerv2.mobs.MobKillStore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class PrestigeMenu implements Listener {
    private static final int BUTTON_SLOT = 13;

    private final JavaPlugin plugin;
    private final PrestigeManager manager;
    private final MobKillStore killStore;
    private final TabListManager tabList;

    public PrestigeMenu(JavaPlugin plugin, PrestigeManager manager, MobKillStore killStore, TabListManager tabList) {
        this.plugin = plugin;
        this.manager = manager;
        this.killStore = killStore;
        this.tabList = tabList;
    }

    public void open(Player player) {
        killStore.getTotal(player.getUniqueId()).whenComplete((kills, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || !player.isOnline()) return;
            Holder holder = new Holder();
            Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Prestige", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            holder.inventory = inventory;
            inventory.setItem(BUTTON_SLOT, buildItem(player, kills));
            player.openInventory(inventory);
        }));
    }

    private ItemStack buildItem(Player player, long kills) {
        int level = manager.getLevel(player.getUniqueId());
        int nextPrestige = level + 1;
        int requiredLevel = PrestigeType.requiredLevel(nextPrestige);
        long requiredKills = PrestigeType.requiredKills(nextPrestige);
        boolean eligible = player.getLevel() >= requiredLevel && kills >= requiredKills;

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Prestige " + PrestigeType.toRoman(level), NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Bonus actuel :", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(statLine("Écus", level * PrestigeType.ECUS_PER_LEVEL, NamedTextColor.GOLD));
        lore.add(statLine("Gemmes", level * PrestigeType.GEMMES_PER_LEVEL, NamedTextColor.DARK_PURPLE));
        lore.add(statLine("XP", level * PrestigeType.XP_PER_LEVEL, NamedTextColor.AQUA));
        lore.add(statLine("Dégâts", level * PrestigeType.FORCE_PER_LEVEL, NamedTextColor.RED));
        lore.add(Component.text(""));
        lore.add(Component.text("Prochain : Prestige " + PrestigeType.toRoman(nextPrestige), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(requirementLine("Niveau", player.getLevel(), requiredLevel));
        lore.add(requirementLine("Mobs tués", kills, requiredKills));
        lore.add(Component.text(""));
        lore.add(Component.text("Récompense :", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(statLine("Écus", PrestigeType.ECUS_PER_LEVEL, NamedTextColor.GOLD));
        lore.add(statLine("Gemme", PrestigeType.GEMMES_PER_LEVEL, NamedTextColor.DARK_PURPLE));
        lore.add(statLine("XP", PrestigeType.XP_PER_LEVEL, NamedTextColor.AQUA));
        lore.add(statLine("Dégâts", PrestigeType.FORCE_PER_LEVEL, NamedTextColor.RED));
        lore.add(Component.text(""));
        lore.add(eligible
                ? Component.text("Clique pour prestiger !", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
                : Component.text("Conditions non remplies.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component statLine(String label, long value, NamedTextColor color) {
        return Component.text("  ■ ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("+" + Numbers.compact(value) + " " + label, color));
    }

    private Component requirementLine(String label, long current, long required) {
        NamedTextColor color = current >= required ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text(label + " : ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(Numbers.compact(current) + "/" + Numbers.compact(required), color));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (event.getSlot() != BUTTON_SLOT || !(event.getWhoClicked() instanceof Player player)) return;

        manager.attemptPrestige(player, () -> {
            if (player.isOnline() && plugin.getConfig().getBoolean("displays.tab.enabled", false)) tabList.apply(player);
            killStore.getTotal(player.getUniqueId()).whenComplete((kills, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || !player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
                holder.inventory.setItem(BUTTON_SLOT, buildItem(player, kills));
            }));
        });
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
