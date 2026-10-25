package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;

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

/**
 * GUI /reforge : dépose une épée ou armure tier, applique un préfixe aléatoire en Gemmes Sombres.
 */
public final class ReforgeMenu implements Listener {
    private static final int DEPOSIT_SLOT = 4;
    private static final int BUTTON_SLOT = 13;

    private final JavaPlugin plugin;
    private final EconomyService economy;

    public ReforgeMenu(JavaPlugin plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
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
                                "Enclume de Reforge",
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
        MobTier tier = tierOf(deposited);
        long cost = ReforgeType.cost(tier);
        ReforgeType current = ItemMods.getReforge(deposited);

        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(
                Component.text("Reforger", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(
                Component.text("Reforge actuelle : ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(
                                current == null
                                        ? Component.text("aucune", NamedTextColor.WHITE)
                                        : Component.text(current.label(), current.color())));
        if (current != null) {
            lore.add(
                    Component.text("  " + current.description(), NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text(""));
        lore.add(
                Component.text(
                                "Résultat aléatoire parmi les préfixes de cette catégorie.",
                                NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(""));
        lore.add(
                Component.text("Coût : ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(
                                Component.text(
                                        Numbers.compact(cost) + " Gemmes Sombres",
                                        NamedTextColor.DARK_PURPLE)));
        lore.add(
                Component.text("Clique pour reforger !", NamedTextColor.GREEN, TextDecoration.BOLD)
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

    private MobTier tierOf(ItemStack item) {
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(TierGear.SWORD_KEY, PersistentDataType.STRING);
        if (id == null) id = pdc.get(TierGear.ARMOR_KEY, PersistentDataType.STRING);
        return id == null ? null : MobTier.parse(id);
    }

    private void reforge(Player player, Inventory inventory) {
        ItemStack deposited = inventory.getItem(DEPOSIT_SLOT);
        EnchantType.Category category = categoryOf(deposited);
        if (category == null) return;
        MobTier tier = tierOf(deposited);
        long costTenths = ReforgeType.cost(tier) * 10L;

        GearTransactions.apply(
                plugin,
                economy,
                player,
                inventory,
                DEPOSIT_SLOT,
                CurrencyType.GEMMES_SOMBRES,
                costTenths,
                current -> {
                    ReforgeType result = ReforgeType.randomFor(category);
                    ItemMods.setReforge(current, result);
                    GearLore.refresh(current);
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
        if (slot == DEPOSIT_SLOT) {
            fr.cliker.clikerv2.runtime.ServerTasks.run(
                    plugin, () -> refreshButton(holder.inventory));
            return;
        }
        event.setCancelled(true);
        if (slot == BUTTON_SLOT) reforge(player, holder.inventory);
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
