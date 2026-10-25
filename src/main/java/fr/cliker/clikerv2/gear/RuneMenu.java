package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.prestige.PrestigeType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * GUI /runes : dépose une épée ou armure tier, grave une rune au hasard (type ET niveau I-XX) en
 * Cendres. Max 1 rune active à la fois.
 */
public final class RuneMenu implements Listener {
    private static final int DEPOSIT_SLOT = 4;
    private static final int BUTTON_SLOT = 13;
    private static final long COST = 500L;

    private final GearGuide guide;
    private final JavaPlugin plugin;
    private final EconomyService economy;

    public RuneMenu(JavaPlugin plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
        guide = new GearGuide(plugin, "Runes", this::open, List.of());
    }

    public void open(Player player) {
        open(player, null);
    }

    public void open(Player player, ItemStack preload) {
        Holder holder = new Holder();
        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        27,
                        Component.text(
                                "Table de Runes",
                                NamedTextColor.LIGHT_PURPLE,
                                TextDecoration.BOLD));
        holder.inventory = inventory;
        decorate(inventory);
        if (preload != null) {
            inventory.setItem(DEPOSIT_SLOT, preload);
            refreshButton(inventory);
        }
        fr.cliker.clikerv2.ui.MenuNavigation.back(plugin, inventory, 26);
        player.openInventory(inventory);
    }

    private void decorate(Inventory inventory) {
        ItemStack filler = filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == DEPOSIT_SLOT || i == BUTTON_SLOT) continue;
            inventory.setItem(i, filler);
        }
        inventory.setItem(8, GearGuide.book());
        refreshButton(inventory);
    }

    private ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    private void refreshButton(Inventory inventory) {
        ItemStack deposited = inventory.getItem(DEPOSIT_SLOT);
        EnchantType.Category category = categoryOf(deposited);
        if (category == null) {
            inventory.setItem(BUTTON_SLOT, null);
            return;
        }
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(
                Component.text("Graver une rune", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(
                Component.text("Runes actuelles :", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
        for (RuneType type : RuneType.of(category)) {
            int level = ItemMods.getRuneLevel(deposited, type);
            if (level <= 0) continue;
            lore.add(
                    Component.text("  ◆ " + type.label() + " ", type.color())
                            .append(
                                    Component.text(
                                            PrestigeType.toRoman(level), NamedTextColor.WHITE)));
            lore.add(
                    Component.text("     " + type.description(), NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text(""));
        lore.add(
                Component.text("1 seule active, tirée au sort (I-XV).", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
        lore.add(
                Component.text("Universelle possible : tous les effets.", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(""));
        lore.add(
                Component.text("Coût : ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(
                                Component.text(
                                        Numbers.compact(COST) + " Cendres", NamedTextColor.GRAY)));
        lore.add(
                Component.text("Clique pour graver !", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        inventory.setItem(BUTTON_SLOT, item);
    }

    private EnchantType.Category categoryOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(TierGear.SWORD_KEY, PersistentDataType.STRING))
            return EnchantType.Category.SWORD;
        if (pdc.has(TierGear.ARMOR_KEY, PersistentDataType.STRING))
            return EnchantType.Category.ARMOR;
        return null;
    }

    private void engrave(Player player, Inventory inventory) {
        ItemStack deposited = inventory.getItem(DEPOSIT_SLOT);
        EnchantType.Category category = categoryOf(deposited);
        if (category == null) return;
        RuneType[] pool = RuneType.of(category);

        GearTransactions.apply(
                plugin,
                economy,
                player,
                inventory,
                DEPOSIT_SLOT,
                CurrencyType.CENDRES,
                COST,
                current -> {
                    RuneType chosen = pool[ThreadLocalRandom.current().nextInt(pool.length)];
                    int oldLevel = ItemMods.getRuneLevel(current, chosen);
                    int newLevel = 1 + ThreadLocalRandom.current().nextInt(RuneType.MAX_LEVEL);
                    ItemMods.clearRunes(current);

                    if (chosen.isUniversal()) {
                        for (RuneType type : pool)
                            if (!type.isUniversal()) ItemMods.setRuneLevel(current, type, newLevel);
                        GearLore.refresh(current);
                        return;
                    }

                    ItemMods.setRuneLevel(current, chosen, newLevel);
                    GearLore.refresh(current);
                    NamedTextColor resultColor =
                            newLevel > oldLevel
                                    ? NamedTextColor.GREEN
                                    : newLevel < oldLevel
                                            ? NamedTextColor.RED
                                            : NamedTextColor.YELLOW;
                },
                () -> refreshButton(inventory));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (GearTransactions.busy(holder.inventory)
                || event.isShiftClick()
                || event.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (slot == 8) {
            event.setCancelled(true);
            if (event.getClick() == org.bukkit.event.inventory.ClickType.LEFT)
                fr.cliker.clikerv2.runtime.ServerTasks.run(
                        plugin,
                        () -> {
                            if (player.getOpenInventory().getTopInventory() == holder.inventory
                                    && !GearTransactions.busy(holder.inventory)) guide.open(player);
                        });
            return;
        }
        if (slot == DEPOSIT_SLOT) {
            fr.cliker.clikerv2.runtime.ServerTasks.run(
                    plugin, () -> refreshButton(holder.inventory));
            return;
        }
        event.setCancelled(true);
        if (slot == BUTTON_SLOT
                && (event.getClick() == org.bukkit.event.inventory.ClickType.LEFT
                        || event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT))
            engrave(player, holder.inventory);
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        if (GearTransactions.busy(holder.inventory)
                || event.getRawSlots().stream()
                        .anyMatch(
                                slot -> slot < holder.inventory.getSize() && slot != DEPOSIT_SLOT))
            event.setCancelled(true);
        else
            fr.cliker.clikerv2.runtime.ServerTasks.run(
                    plugin, () -> refreshButton(holder.inventory));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        ItemStack deposited = holder.inventory.getItem(DEPOSIT_SLOT);
        if (deposited == null) return;
        holder.inventory.setItem(DEPOSIT_SLOT, null);
        fr.cliker.clikerv2.rewards.ItemDelivery.give(player, deposited);
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
