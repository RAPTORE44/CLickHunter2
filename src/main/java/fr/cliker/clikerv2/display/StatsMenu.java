package fr.cliker.clikerv2.display;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.mobs.MobKillStore;
import fr.cliker.clikerv2.prestige.PrestigeManager;
import fr.cliker.clikerv2.prestige.PrestigeType;
import fr.cliker.clikerv2.upgrade.UpgradeManager;
import fr.cliker.clikerv2.upgrade.UpgradeType;

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
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class StatsMenu implements Listener {
    private static final int[] ARMOR_SLOTS = {12, 21, 30, 39};
    private static final int SWORD_SLOT = 22;
    private static final int RECAP_SLOT = 24;

    private final org.bukkit.plugin.Plugin plugin;
    private final EconomyService economy;
    private final UpgradeManager upgrades;
    private final MobKillStore killStore;
    private final PrestigeManager prestige;

    public StatsMenu(org.bukkit.plugin.Plugin plugin, EconomyService economy, UpgradeManager upgrades, MobKillStore killStore, PrestigeManager prestige) {
        this.plugin = plugin;
        this.economy = economy;
        this.upgrades = upgrades;
        this.killStore = killStore;
        this.prestige = prestige;
    }

    public void open(Player player) {
        economy.getBalances(player.getUniqueId()).thenCombine(killStore.getTotal(player.getUniqueId()), (balances, kills) ->
                new Object[]{balances, kills}
        ).whenComplete((result, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || !player.isOnline()) return;
            EconomyService.Balances balances = (EconomyService.Balances) result[0];
            long kills = (long) result[1];

            Holder holder = new Holder();
            Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Statistiques", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            holder.inventory = inventory;

            ItemStack[] armorContents = player.getInventory().getArmorContents(); // [boots, leggings, chest, helmet]
            inventory.setItem(ARMOR_SLOTS[0], displayOrPlaceholder(armorContents[3], "Casque"));
            inventory.setItem(ARMOR_SLOTS[1], displayOrPlaceholder(armorContents[2], "Plastron"));
            inventory.setItem(ARMOR_SLOTS[2], displayOrPlaceholder(armorContents[1], "Jambières"));
            inventory.setItem(ARMOR_SLOTS[3], displayOrPlaceholder(armorContents[0], "Bottes"));
            inventory.setItem(SWORD_SLOT, displayOrPlaceholder(player.getInventory().getItemInMainHand(), "Épée"));
            inventory.setItem(RECAP_SLOT, recapItem(player, balances, kills));

            player.openInventory(inventory);
        }));
    }

    private ItemStack displayOrPlaceholder(ItemStack item, String label) {
        if (item != null && item.getType() != Material.AIR) return item.clone();
        ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = placeholder.getItemMeta();
        meta.displayName(Component.text("Aucun(e) " + label + " équipé(e)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        placeholder.setItemMeta(meta);
        return placeholder;
    }

    private ItemStack recapItem(Player player, EconomyService.Balances balances, long kills) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text("Récapitulatif", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(header("Compte", NamedTextColor.YELLOW));
        lore.add(line("Premier join", new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(player.getFirstPlayed())), NamedTextColor.WHITE));
        lore.add(line("Temps de jeu", formatPlaytime(player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE)), NamedTextColor.WHITE));
        lore.add(Component.text(""));
        lore.add(header("Économie", NamedTextColor.GREEN));
        lore.add(line("Écus", Numbers.compact(balances.ecus()), NamedTextColor.GOLD));
        lore.add(line("Gemmes Sombres", CurrencyType.GEMMES_SOMBRES.format(balances.gemmesSombres()), NamedTextColor.DARK_PURPLE));
        lore.add(line("Cendres", Numbers.compact(balances.cendres()), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(header("Progression", NamedTextColor.AQUA));
        lore.add(line("XP total gagné", Numbers.compact(player.getTotalExperience()), NamedTextColor.AQUA));
        lore.add(line("Niveau actuel", String.valueOf(player.getLevel()), NamedTextColor.GREEN));
        lore.add(line("Mobs tués", Numbers.compact(kills), NamedTextColor.RED));
        lore.add(line("Prestige", PrestigeType.toRoman(prestige.getLevel(player.getUniqueId())), NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.text(""));
        lore.add(header("Bonus Upgrades", NamedTextColor.GOLD));
        for (UpgradeType type : UpgradeType.values()) {
            int level = upgrades.getLevel(player.getUniqueId(), type);
            lore.add(Component.text(type.symbol() + " " + type.label() + " : ", type.color()).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(upgrades.formatBonus(type, level) + " " + type.unitLabel() + " ", NamedTextColor.WHITE))
                    .append(Component.text("(niv. " + level + "/" + UpgradeType.MAX_LEVEL + ")", NamedTextColor.DARK_GRAY)));
        }
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private Component header(String text, NamedTextColor color) {
        return Component.text("▸ " + text, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false);
    }

    private Component line(String label, String value, NamedTextColor valueColor) {
        return Component.text(label + " : ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(value, valueColor));
    }

    private String formatPlaytime(int ticks) {
        long totalMinutes = ticks / 20L / 60L;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours + "h " + minutes + "m";
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
