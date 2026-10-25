package fr.cliker.clikerv2.essence;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.economy.PermissionBonus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI /sell : vide, le prix s'affiche directement sur les essences dans l'inventaire du joueur tant
 * que le menu est ouvert.
 */
public final class SellMenu implements Listener {
    private final JavaPlugin plugin;
    private final EconomyService economy;

    public SellMenu(JavaPlugin plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        9,
                        Component.text(
                                "Vente d'essences",
                                NamedTextColor.LIGHT_PURPLE,
                                TextDecoration.BOLD));
        holder.inventory = inventory;
        player.openInventory(inventory);
        showPrices(player);
        player.sendMessage(
                Component.text(
                        "Gauche : vendre 1 essence | Droite : vendre la pile cliquée.",
                        NamedTextColor.GRAY));
    }

    private void showPrices(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            MobTier tier = EssenceItem.tierOf(contents[slot]);
            if (tier == null) continue;
            player.getInventory().setItem(slot, withPriceLore(player, contents[slot], tier));
        }
    }

    private void hidePrices(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            MobTier tier = EssenceItem.tierOf(contents[slot]);
            if (tier == null) continue;
            EssenceRarity rarity = EssenceItem.rarityOf(contents[slot]);
            player.getInventory()
                    .setItem(slot, EssenceItem.create(tier, rarity, contents[slot].getAmount()));
        }
    }

    private ItemStack withPriceLore(Player player, ItemStack original, MobTier tier) {
        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        EssenceRarity rarity = EssenceItem.rarityOf(original);
        long price =
                tier.sellPricePerUnit()
                        * rarity.valueMultiplier()
                        * PermissionBonus.multiplier(player, "vente");
        long total = price * item.getAmount();

        List<Component> lore = new ArrayList<>();
        lore.add(
                Component.text(rarity.label(), rarity.color())
                        .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(""));
        lore.add(
                Component.text("Prix : ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(
                                Component.text(
                                        Numbers.compact(price) + " Écus/unité",
                                        NamedTextColor.GOLD)));
        lore.add(
                Component.text("Valeur de la pile : ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(
                                Component.text(
                                        Numbers.compact(total) + " Écus", NamedTextColor.GOLD)));
        lore.add(Component.text(""));
        lore.add(
                Component.text(
                                "Gauche : vendre 1 | Droite : vendre cette pile",
                                NamedTextColor.GREEN,
                                TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void sell(Player player, int slot, ItemStack clicked, MobTier tier, int amount) {
        EssenceRarity rarity = EssenceItem.rarityOf(clicked);
        ItemStack sold = EssenceItem.create(tier, rarity, amount);
        long gain =
                tier.sellPricePerUnit()
                        * rarity.valueMultiplier()
                        * PermissionBonus.multiplier(player, "vente")
                        * amount;
        var remaining = clicked.clone();
        remaining.setAmount(clicked.getAmount() - amount);
        player.getInventory().setItem(slot, remaining.getAmount() == 0 ? null : remaining);
        economy.change(player.getUniqueId(), player.getName(), CurrencyType.ECUS, gain)
                .whenComplete(
                        (balance, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (error != null) {
                                                var delivery =
                                                        Bukkit.getServicesManager()
                                                                .load(
                                                                        fr.cliker.clikerv2.rewards
                                                                                .ClaimService
                                                                                .class);
                                                if (delivery != null)
                                                    delivery.give(
                                                            player.getUniqueId(),
                                                            player.getName(),
                                                            sold);
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Vente d’essences"
                                                                        + " impossible,"
                                                                        + " restitution"
                                                                        + " demandée",
                                                                error);
                                                if (player.isOnline())
                                                    player.sendMessage(
                                                            Component.text(
                                                                    "Vente impossible : tes"
                                                                            + " essences sont"
                                                                            + " rendues ou"
                                                                            + " disponibles"
                                                                            + " dans /claim.",
                                                                    NamedTextColor.RED));
                                                return;
                                            }
                                            if (!player.isOnline()) return;
                                            player.sendMessage(
                                                    Component.text("✦ Vendu ", NamedTextColor.GREEN)
                                                            .append(
                                                                    Component.text(
                                                                            Numbers.compact(amount)
                                                                                    + " "
                                                                                    + rarity.label()
                                                                                    + " "
                                                                                    + tier
                                                                                            .essenceName(),
                                                                            NamedTextColor.YELLOW))
                                                            .append(
                                                                    Component.text(
                                                                            " contre ",
                                                                            NamedTextColor.GREEN))
                                                            .append(
                                                                    Component.text(
                                                                            "+"
                                                                                    + Numbers
                                                                                            .compact(
                                                                                                    gain)
                                                                                    + " Écus",
                                                                            NamedTextColor.GOLD)));
                                        }));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Ce menu ne stocke aucun objet : interdire aussi les transferts d’objets non vendables.
        event.setCancelled(true);

        if (event.getClickedInventory() != player.getInventory()) {
            event.setCancelled(true);
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        MobTier tier = EssenceItem.tierOf(clicked);
        if (tier == null) return;
        event.setCancelled(true);
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)) return;
        if (event.getClick() == org.bukkit.event.inventory.ClickType.LEFT)
            sell(player, event.getSlot(), clicked, tier, 1);
        else if (event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT)
            sell(player, event.getSlot(), clicked, tier, clicked.getAmount());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        if (event.getRawSlots().stream()
                .anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        hidePrices(player);
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
