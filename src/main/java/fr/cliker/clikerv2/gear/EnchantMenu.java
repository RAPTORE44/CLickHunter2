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
 * GUI /enchant : dépose une épée ou armure tier, enchante au hasard (type ET niveau I-XX) en Écus.
 * Max 1 enchant actif à la fois.
 */
public final class EnchantMenu implements Listener {
    private static final int DEPOSIT_SLOT = 4;
    private static final int BUTTON_SLOT = 13;
    private final long cost;
    private final List<Double> weights;

    private final JavaPlugin plugin;
    private final EconomyService economy;

    public EnchantMenu(JavaPlugin plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
        java.io.File file = new java.io.File(plugin.getDataFolder(), "enchants.yml");
        if (!file.exists()) plugin.saveResource("enchants.yml", false);
        var settings = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        cost = settings.getLong("cout", 100_000L);
        if (cost < 0) throw new IllegalArgumentException("Coût d'enchantement négatif");
        weights = settings.getDoubleList("probabilites");
        EnchantRoll.validate(weights);
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
                                "Table d'Enchantement",
                                NamedTextColor.LIGHT_PURPLE,
                                TextDecoration.BOLD));
        holder.inventory = inventory;
        decorate(inventory);
        if (preload != null) {
            inventory.setItem(DEPOSIT_SLOT, preload);
            refreshButton(inventory);
        }
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
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(
                Component.text("Enchanter", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(
                Component.text("Enchant actuel :", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
        boolean hasEnchant = false;
        for (EnchantType type : EnchantType.of(category)) {
            int level = ItemMods.getEnchantLevel(deposited, type);
            if (level <= 0) continue;
            hasEnchant = true;
            lore.add(
                    Component.text("  ■ " + type.label() + " ", type.color())
                            .append(
                                    Component.text(
                                            PrestigeType.toRoman(level), NamedTextColor.WHITE)));
            lore.add(
                    Component.text("     " + type.description(), NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false));
        }
        if (!hasEnchant) lore.add(Component.text("  aucun", NamedTextColor.WHITE));
        lore.add(Component.text(""));
        lore.add(
                Component.text("1 seul actif, tiré au sort (I-V).", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
        lore.add(
                Component.text("Universel possible : tous les effets.", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(""));
        lore.add(
                Component.text("Coût : ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(
                                Component.text(
                                        Numbers.compact(cost) + " Écus", NamedTextColor.GOLD)));
        lore.add(
                Component.text("Clique pour enchanter !", NamedTextColor.GREEN, TextDecoration.BOLD)
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

    private void enchant(Player player, Inventory inventory) {
        ItemStack deposited = inventory.getItem(DEPOSIT_SLOT);
        EnchantType.Category category = categoryOf(deposited);
        if (category == null) return;

        EnchantType[] options = EnchantType.of(category);

        GearTransactions.apply(
                plugin,
                economy,
                player,
                inventory,
                DEPOSIT_SLOT,
                CurrencyType.ECUS,
                cost,
                current -> {
                    EnchantType chosen =
                            options[ThreadLocalRandom.current().nextInt(options.length)];
                    int oldLevel = ItemMods.getEnchantLevel(current, chosen);
                    int newLevel =
                            EnchantRoll.level(weights, ThreadLocalRandom.current().nextDouble(100));
                    ItemMods.clearEnchants(current);

                    if (chosen.isUniversal()) {
                        for (EnchantType type : options)
                            if (!type.isUniversal())
                                ItemMods.setEnchantLevel(current, type, newLevel);
                        GearLore.refresh(current);
                        return;
                    }

                    ItemMods.setEnchantLevel(current, chosen, newLevel);
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
        if (slot == DEPOSIT_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshButton(holder.inventory));
            return;
        }
        event.setCancelled(true);
        if (slot == BUTTON_SLOT) enchant(player, holder.inventory);
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        if (GearTransactions.busy(holder.inventory)
                || event.getRawSlots().stream()
                        .anyMatch(
                                slot -> slot < holder.inventory.getSize() && slot != DEPOSIT_SLOT))
            event.setCancelled(true);
        else Bukkit.getScheduler().runTask(plugin, () -> refreshButton(holder.inventory));
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
