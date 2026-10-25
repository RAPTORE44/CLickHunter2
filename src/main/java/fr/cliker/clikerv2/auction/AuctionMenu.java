package fr.cliker.clikerv2.auction;

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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI de l'hôtel des ventes : parcourir/acheter (avec confirmation), vendre (avec confirmation),
 * gérer ses annonces.
 */
public final class AuctionMenu implements Listener {
    private static final int[] LISTING_SLOTS = buildRange(0, 45);
    private static final int CONFIRM_SLOT = 11;
    private static final int ITEM_SLOT = 13;
    private static final int CANCEL_SLOT = 15;
    private static final int MY_LISTINGS_SLOT = 49;
    private static final int BACK_SLOT = 49;

    private final JavaPlugin plugin;
    private final AuctionManager manager;

    public AuctionMenu(JavaPlugin plugin, AuctionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void openBrowse(Player player) {
        BrowseHolder holder = new BrowseHolder();
        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        54,
                        Component.text(
                                "Hôtel des Ventes", NamedTextColor.GOLD, TextDecoration.BOLD));
        holder.inventory = inventory;
        List<AuctionListing> listings = manager.activeListings();
        for (int i = 0; i < LISTING_SLOTS.length && i < listings.size(); i++) {
            inventory.setItem(LISTING_SLOTS[i], browseItem(listings.get(i)));
        }
        inventory.setItem(
                MY_LISTINGS_SLOT,
                actionButton(Material.NAME_TAG, "Mes Annonces", NamedTextColor.LIGHT_PURPLE));
        player.openInventory(inventory);
    }

    public void openMyListings(Player player) {
        MyListingsHolder holder = new MyListingsHolder();
        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        54,
                        Component.text(
                                "Mes Annonces", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        holder.inventory = inventory;
        List<AuctionListing> listings = manager.listingsOf(player.getUniqueId());
        for (int i = 0; i < LISTING_SLOTS.length && i < listings.size(); i++) {
            inventory.setItem(LISTING_SLOTS[i], myListingItem(listings.get(i)));
        }
        inventory.setItem(
                BACK_SLOT,
                actionButton(Material.ARROW, "Retour à l'hôtel des ventes", NamedTextColor.YELLOW));
        player.openInventory(inventory);
    }

    public void openSellConfirm(Player player, ItemStack item, long price) {
        SellConfirmHolder holder = new SellConfirmHolder(item.clone(), price);
        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        27,
                        Component.text(
                                "Confirmer la vente", NamedTextColor.GOLD, TextDecoration.BOLD));
        holder.inventory = inventory;

        long tax = Math.round(price * AuctionManager.TAX_RATE);
        long proceeds = price - tax;
        ItemStack display = item.clone();
        appendLore(
                display,
                List.of(
                        Component.text(""),
                        Component.text("Prix de vente : ", NamedTextColor.GRAY)
                                .append(Component.text(price + " Écus", NamedTextColor.GOLD)),
                        Component.text("Taxe (1%) : ", NamedTextColor.GRAY)
                                .append(Component.text("-" + tax + " Écus", NamedTextColor.RED)),
                        Component.text("Tu recevras : ", NamedTextColor.GRAY)
                                .append(Component.text(proceeds + " Écus", NamedTextColor.GREEN))));
        inventory.setItem(ITEM_SLOT, display);
        inventory.setItem(
                CONFIRM_SLOT,
                actionButton(Material.LIME_WOOL, "Confirmer la vente", NamedTextColor.GREEN));
        inventory.setItem(
                CANCEL_SLOT, actionButton(Material.RED_WOOL, "Annuler", NamedTextColor.RED));
        player.openInventory(inventory);
    }

    private void openBuyConfirm(Player player, AuctionListing listing) {
        BuyConfirmHolder holder = new BuyConfirmHolder(listing.id());
        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        27,
                        Component.text(
                                "Confirmer l'achat", NamedTextColor.GOLD, TextDecoration.BOLD));
        holder.inventory = inventory;

        ItemStack item = AuctionManager.deserialize(listing.itemYaml());
        if (item == null) item = new ItemStack(Material.BARRIER);
        ItemStack display = item.clone();
        appendLore(
                display,
                List.of(
                        Component.text(""),
                        Component.text("Vendeur : ", NamedTextColor.GRAY)
                                .append(Component.text(listing.sellerName(), NamedTextColor.WHITE)),
                        Component.text("Prix : ", NamedTextColor.GRAY)
                                .append(
                                        Component.text(
                                                listing.price() + " Écus", NamedTextColor.GOLD))));
        inventory.setItem(ITEM_SLOT, display);
        inventory.setItem(
                CONFIRM_SLOT,
                actionButton(Material.LIME_WOOL, "Confirmer l'achat", NamedTextColor.GREEN));
        inventory.setItem(
                CANCEL_SLOT, actionButton(Material.RED_WOOL, "Annuler", NamedTextColor.RED));
        player.openInventory(inventory);
    }

    private ItemStack browseItem(AuctionListing listing) {
        ItemStack item = AuctionManager.deserialize(listing.itemYaml());
        if (item == null) return new ItemStack(Material.BARRIER);
        ItemStack display = item.clone();
        appendLore(
                display,
                List.of(
                        Component.text(""),
                        Component.text("Vendeur : ", NamedTextColor.GRAY)
                                .append(Component.text(listing.sellerName(), NamedTextColor.WHITE)),
                        Component.text("Prix : ", NamedTextColor.GRAY)
                                .append(
                                        Component.text(
                                                listing.price() + " Écus", NamedTextColor.GOLD)),
                        expiryLine(listing),
                        Component.text(""),
                        Component.text(
                                "Clique pour acheter !",
                                NamedTextColor.GREEN,
                                TextDecoration.BOLD)));
        return display;
    }

    private ItemStack myListingItem(AuctionListing listing) {
        ItemStack item = AuctionManager.deserialize(listing.itemYaml());
        if (item == null) return new ItemStack(Material.BARRIER);
        ItemStack display = item.clone();
        appendLore(
                display,
                List.of(
                        Component.text(""),
                        Component.text("Prix : ", NamedTextColor.GRAY)
                                .append(
                                        Component.text(
                                                listing.price() + " Écus", NamedTextColor.GOLD)),
                        expiryLine(listing),
                        Component.text(""),
                        Component.text(
                                "Clique pour retirer l'annonce.",
                                NamedTextColor.RED,
                                TextDecoration.BOLD)));
        return display;
    }

    private Component expiryLine(AuctionListing listing) {
        long remainingMillis =
                AuctionManager.EXPIRY_MILLIS - (System.currentTimeMillis() - listing.listedAt());
        long hours = Math.max(0, remainingMillis) / (1000 * 60 * 60);
        long minutes = (Math.max(0, remainingMillis) / (1000 * 60)) % 60;
        return Component.text(
                "Expire dans " + hours + "h" + minutes + "min", NamedTextColor.DARK_GRAY);
    }

    private ItemStack actionButton(Material material, String label, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(
                Component.text(label, color, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private void appendLore(ItemStack item, List<Component> extra) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        for (Component line : extra) lore.add(line.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private static int[] buildRange(int from, int count) {
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) slots[i] = from + i;
        return slots;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder rawHolder = event.getInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (rawHolder instanceof BrowseHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (event.getSlot() == MY_LISTINGS_SLOT) {
                openMyListings(player);
                return;
            }
            int index = indexOf(event.getSlot());
            if (index < 0) return;
            List<AuctionListing> listings = manager.activeListings();
            if (index >= listings.size()) return;
            openBuyConfirm(player, listings.get(index));
            return;
        }

        if (rawHolder instanceof MyListingsHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (event.getSlot() == BACK_SLOT) {
                openBrowse(player);
                return;
            }
            int index = indexOf(event.getSlot());
            if (index < 0) return;
            List<AuctionListing> listings = manager.listingsOf(player.getUniqueId());
            if (index >= listings.size()) return;
            if (manager.cancel(player, listings.get(index).id(), false)) {
                player.sendMessage(
                        Component.text(
                                "Retrait demandé. L’objet sera livré ou disponible dans /claim.",
                                NamedTextColor.GREEN));
            }
            openMyListings(player);
            return;
        }

        if (rawHolder instanceof BuyConfirmHolder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (event.getSlot() == CONFIRM_SLOT) {
                manager.buy(
                        player,
                        holder.listingId,
                        () ->
                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    if (player.isOnline()) openBrowse(player);
                                                }));
            } else if (event.getSlot() == CANCEL_SLOT) {
                openBrowse(player);
            }
            return;
        }

        if (rawHolder instanceof SellConfirmHolder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (event.getSlot() == CONFIRM_SLOT) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (!hand.isSimilar(holder.item) || hand.getAmount() < holder.item.getAmount()) {
                    player.sendMessage(
                            Component.text(
                                    "Tu ne tiens plus le bon item en main, vente annulée.",
                                    NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }
                hand.setAmount(hand.getAmount() - holder.item.getAmount());
                manager.createListing(player, holder.item, holder.price);
                player.sendMessage(
                        Component.text("Publication en cours : ", NamedTextColor.GREEN)
                                .append(
                                        Component.text(
                                                holder.price + " Écus", NamedTextColor.GOLD)));
                player.closeInventory();
            } else if (event.getSlot() == CANCEL_SLOT) {
                player.sendMessage(Component.text("Vente annulée.", NamedTextColor.GRAY));
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof BrowseHolder
                || holder instanceof MyListingsHolder
                || holder instanceof BuyConfirmHolder
                || holder instanceof SellConfirmHolder) event.setCancelled(true);
    }

    private int indexOf(int slot) {
        for (int i = 0; i < LISTING_SLOTS.length; i++) if (LISTING_SLOTS[i] == slot) return i;
        return -1;
    }

    private static final class BrowseHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MyListingsHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class BuyConfirmHolder implements InventoryHolder {
        private final String listingId;
        private Inventory inventory;

        private BuyConfirmHolder(String listingId) {
            this.listingId = listingId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class SellConfirmHolder implements InventoryHolder {
        private final ItemStack item;
        private final long price;
        private Inventory inventory;

        private SellConfirmHolder(ItemStack item, long price) {
            this.item = item;
            this.price = price;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
