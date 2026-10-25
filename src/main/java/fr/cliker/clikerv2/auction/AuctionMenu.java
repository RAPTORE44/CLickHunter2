package fr.cliker.clikerv2.auction;

import fr.cliker.clikerv2.Numbers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Chaque case garde l'identifiant affiché : une vente concurrente ne décale jamais sa cible. */
public final class AuctionMenu implements Listener {
    private static final int PAGE_SIZE = 45, CONFIRM = 11, ITEM = 13, CANCEL = 15;
    private final JavaPlugin plugin;
    private final AuctionManager manager;
    private final fr.cliker.clikerv2.features.Menus sortMenus;

    public AuctionMenu(JavaPlugin plugin, AuctionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        sortMenus = new fr.cliker.clikerv2.features.Menus(plugin);
    }

    public void openBrowse(Player player) {
        openListings(player, false, 0);
    }

    public void openMyListings(Player player) {
        openListings(player, true, 0);
    }

    private static final String[] SORTS = {
        "Plus récentes",
        "Plus anciennes",
        "Moins cher",
        "Plus cher",
        "A à Z",
        "Z à A",
        "Rareté décroissante",
        "Rareté croissante"
    };

    static String itemName(AuctionListing listing) {
        var item = AuctionManager.deserialize(listing.itemYaml());
        if (item == null) return "";
        return item.getItemMeta().hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(item.getItemMeta().displayName())
                : item.getType().name();
    }

    static int rarity(AuctionListing listing) {
        var item = AuctionManager.deserialize(listing.itemYaml());
        if (item == null) return 0;
        var data = item.getItemMeta().getPersistentDataContainer();
        for (var key : data.getKeys()) {
            if (Set.of("cliker_tier_sword", "cliker_tier_armor").contains(key.getKey())) {
                String value = data.get(key, org.bukkit.persistence.PersistentDataType.STRING);
                var tier = fr.cliker.clikerv2.MobTier.parse(value);
                if (tier != null) return Math.min(5, tier.ordinal() / 3);
            }
            if (key.getKey().equals("cliker_essence_rarity")) {
                String value = data.get(key, org.bukkit.persistence.PersistentDataType.STRING);
                if (value != null)
                    try {
                        return fr.cliker.clikerv2.essence.EssenceRarity.valueOf(value).ordinal();
                    } catch (IllegalArgumentException ignored) {
                    }
            }
        }
        return item.getItemMeta().hasRarity() ? item.getItemMeta().getRarity().ordinal() : 0;
    }

    static Comparator<AuctionListing> comparator(int sort) {
        Map<String, String> names = new HashMap<>();
        Map<String, Integer> rarities = new HashMap<>();
        java.util.function.Function<AuctionListing, String> name =
                l -> names.computeIfAbsent(l.id(), key -> itemName(l));
        java.util.function.ToIntFunction<AuctionListing> rank =
                l -> rarities.computeIfAbsent(l.id(), key -> rarity(l));
        Comparator<AuctionListing> comparator =
                switch (sort) {
                    case 1 -> Comparator.comparingLong(AuctionListing::listedAt);
                    case 2 -> Comparator.comparingLong(AuctionListing::price);
                    case 3 -> Comparator.comparingLong(AuctionListing::price).reversed();
                    case 4 -> Comparator.comparing(name, String.CASE_INSENSITIVE_ORDER);
                    case 5 -> Comparator.comparing(name, String.CASE_INSENSITIVE_ORDER).reversed();
                    case 6 -> Comparator.comparingInt(rank).reversed();
                    case 7 -> Comparator.comparingInt(rank);
                    default -> Comparator.comparingLong(AuctionListing::listedAt).reversed();
                };
        return comparator.thenComparing(AuctionListing::id);
    }

    private void openListings(Player player, boolean own, int requested) {
        openListings(player, own, requested, 0);
    }

    private void openListings(Player player, boolean own, int requested, int sort) {
        openListings(player, own, requested, sort, "", "tous");
    }

    static String normalizeSearch(String value) {
        return java.text.Normalizer.normalize(
                        value.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim();
    }

    static String category(AuctionListing listing) {
        var item = AuctionManager.deserialize(listing.itemYaml());
        if (item == null) return "autres";
        String material = item.getType().name();
        if (material.endsWith("_SWORD")) return "epees";
        if (material.matches(".*_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)")) return "armures";
        if (material.contains("POTION")) return "potions";
        if (item.getItemMeta().getPersistentDataContainer().getKeys().stream()
                .anyMatch(k -> k.getKey().equals("cliker_essence_tier"))) return "essences";
        if (material.equals("NAUTILUS_SHELL")) return "talismans";
        return "autres";
    }

    static boolean matches(AuctionListing listing, String query, String filter) {
        return (filter.equals("tous") || filter.equals(category(listing)))
                && normalizeSearch(itemName(listing) + " " + category(listing))
                        .contains(normalizeSearch(query));
    }

    public void search(Player player, String query) {
        if (query.length() > 48) {
            player.sendMessage("Recherche limitée à 48 caractères.");
            return;
        }
        openListings(player, false, 0, 0, query.trim(), "tous");
    }

    private List<String> favorites(Player player) {
        String raw =
                player.getPersistentDataContainer()
                        .getOrDefault(
                                new NamespacedKey(plugin, "ah_searches"),
                                org.bukkit.persistence.PersistentDataType.STRING,
                                "");
        return new ArrayList<>(
                raw.lines().filter(q -> !q.isBlank() && q.length() <= 48).limit(10).toList());
    }

    public void openFavorites(Player player) {
        if (!player.hasPermission("cliker.ah.use")
                || fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)) return;
        var view = sortMenus.create(player, 27, "AH — Recherches favorites");
        List<String> favorites = favorites(player);
        for (int i = 0; i < favorites.size(); i++) {
            String query = favorites.get(i);
            view.put(
                    9 + i,
                    button(Material.PAPER, query, NamedTextColor.GOLD),
                    (who, r) -> search(who, query));
        }
        if (favorites.isEmpty())
            view.put(
                    13,
                    button(
                            Material.PAPER,
                            "Aucun favori — recherche puis clique sur l’étoile",
                            NamedTextColor.GRAY),
                    null);
        view.put(
                22,
                button(Material.ARROW, "Retour", NamedTextColor.GREEN),
                (who, r) -> openBrowse(who));
        view.show(player);
    }

    private void filterMenu(Player player, Listings page) {
        var view = sortMenus.create(player, 27, "AH — Catégories");
        String[] categories = {
            "tous", "epees", "armures", "potions", "essences", "talismans", "autres"
        };
        String[] labels = {
            "Tous", "Épées", "Armures", "Potions", "Essences", "Talismans", "Autres"
        };
        for (int i = 0; i < categories.length; i++) {
            String filter = categories[i];
            view.put(
                    10 + i,
                    button(Material.BOOK, labels[i], NamedTextColor.AQUA),
                    (who, r) -> openListings(who, page.own, 0, page.sort, page.query, filter));
        }
        view.put(
                22,
                button(Material.ARROW, "Retour", NamedTextColor.GREEN),
                (who, r) ->
                        openListings(
                                who, page.own, page.page, page.sort, page.query, page.category));
        view.show(player);
    }

    private void openListings(
            Player player, boolean own, int requested, int sort, String query, String filter) {
        if (!player.hasPermission("cliker.ah.use")
                || fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)) return;
        var listings =
                new ArrayList<>(
                        own ? manager.listingsOf(player.getUniqueId()) : manager.activeListings());
        listings.removeIf(l -> !matches(l, query, filter));
        listings.sort(comparator(sort));
        int page = Math.max(0, Math.min(requested, Math.max(0, (listings.size() - 1) / PAGE_SIZE)));
        var visible =
                List.copyOf(
                        listings.subList(
                                page * PAGE_SIZE,
                                Math.min(listings.size(), (page + 1) * PAGE_SIZE)));
        var holder =
                new Listings(
                        player.getUniqueId(),
                        own,
                        page,
                        visible.stream().map(AuctionListing::id).toList());
        var inventory =
                Bukkit.createInventory(
                        holder,
                        54,
                        Component.text(
                                (own ? "Mes annonces" : "Hôtel des ventes") + " • " + (page + 1),
                                NamedTextColor.GREEN));
        holder.sort = sort;
        holder.query = query;
        holder.category = filter;
        holder.inventory = inventory;
        for (int i = 0; i < visible.size(); i++)
            inventory.setItem(i, listingItem(visible.get(i), own));
        for (int i = 45; i < 54; i++)
            inventory.setItem(
                    i, button(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
        if (page > 0)
            inventory.setItem(46, button(Material.ARROW, "Page précédente", NamedTextColor.GREEN));
        if ((page + 1) * PAGE_SIZE < listings.size())
            inventory.setItem(53, button(Material.ARROW, "Page suivante", NamedTextColor.GREEN));
        inventory.setItem(
                49,
                button(
                        own ? Material.ARROW : Material.NAME_TAG,
                        own ? "Retour aux annonces" : "Mes annonces",
                        NamedTextColor.GOLD));
        inventory.setItem(
                48,
                button(
                        Material.PAPER,
                        "Page " + (page + 1) + " / " + Math.max(1, (listings.size() + 44) / 45),
                        NamedTextColor.WHITE));
        inventory.setItem(
                45,
                button(
                        Material.HOPPER,
                        "Trier : " + SORTS[sort] + " — clique pour changer",
                        NamedTextColor.AQUA));
        inventory.setItem(47, button(Material.BOOK, "Catégorie : " + filter, NamedTextColor.AQUA));
        inventory.setItem(
                51,
                button(
                        Material.COMPASS,
                        query.isBlank() ? "Rechercher un objet" : "Recherche : " + query,
                        NamedTextColor.GREEN));
        inventory.setItem(
                52,
                button(
                        Material.NETHER_STAR,
                        query.isBlank()
                                ? "Recherches favorites"
                                : (favorites(player).contains(query)
                                        ? "Retirer cette recherche des favoris"
                                        : "Ajouter cette recherche aux favoris"),
                        NamedTextColor.GOLD));
        inventory.setItem(50, button(Material.SUNFLOWER, "Actualiser", NamedTextColor.GREEN));
        if (visible.isEmpty())
            inventory.setItem(22, button(Material.PAPER, "Aucune annonce", NamedTextColor.GRAY));
        player.openInventory(inventory);
    }

    private void openSort(Player player, boolean own, int current, String query, String filter) {
        var view = sortMenus.create(player, 27, "AH — Choisir le tri");
        for (int i = 0; i < SORTS.length; i++) {
            int sort = i;
            view.put(
                    9 + i,
                    button(
                            Material.PAPER,
                            SORTS[i],
                            i == current ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                    (who, right) -> openListings(who, own, 0, sort, query, filter));
        }
        view.put(
                22,
                button(Material.ARROW, "Retour", NamedTextColor.GREEN),
                (who, right) -> openListings(who, own, 0, current, query, filter));
        view.show(player);
    }

    public void openSellConfirm(Player player, ItemStack item, long price) {
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)
                || price <= 0
                || item == null
                || item.getType().isAir()) return;
        var holder = new Confirmation(player.getUniqueId(), null, item.clone(), price);
        confirmation(
                player,
                holder,
                "Confirmer la vente",
                List.of(
                        "Prix : " + String.format(Locale.FRANCE, "%,d", price) + " Écus",
                        "Frais immédiats non remboursables (1 %) : "
                                + String.format(Locale.FRANCE, "%,d", AuctionListing.tax(price))
                                + " Écus",
                        "Tu recevras : " + String.format(Locale.FRANCE, "%,d", price) + " Écus"));
        holder.inventory.setItem(
                22,
                button(
                        Material.PAPER,
                        "Annonce chat : NON — supplément 5 % ("
                                + AuctionStore.advertisingFee(price)
                                + " Écus)",
                        NamedTextColor.GOLD));
    }

    private void openBuyConfirm(Player player, AuctionListing listing) {
        var item = AuctionManager.deserialize(listing.itemYaml());
        if (item == null || item.getType().isAir()) {
            player.sendMessage(
                    Component.text(
                            "Cette annonce est indisponible. Contacte un administrateur.",
                            NamedTextColor.RED));
            return;
        }
        confirmation(
                player,
                new Confirmation(player.getUniqueId(), listing.id(), item, listing.price()),
                "Confirmer l’achat",
                List.of(
                        "Prix : " + String.format(Locale.FRANCE, "%,d", listing.price()) + " Écus",
                        "Vendeur : " + listing.sellerName()));
    }

    private void confirmation(
            Player player, Confirmation holder, String title, List<String> details) {
        holder.inventory =
                Bukkit.createInventory(holder, 27, Component.text(title, NamedTextColor.GREEN));
        var display = holder.item.clone();
        var meta = display.getItemMeta();
        var lore = new ArrayList<Component>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.empty());
        details.forEach(
                line ->
                        lore.add(
                                Component.text(line, NamedTextColor.GRAY)
                                        .decoration(TextDecoration.ITALIC, false)));
        meta.lore(lore);
        display.setItemMeta(meta);
        holder.inventory.setItem(ITEM, display);
        holder.inventory.setItem(
                CONFIRM, button(Material.LIME_WOOL, "Confirmer", NamedTextColor.GREEN));
        holder.inventory.setItem(CANCEL, button(Material.RED_WOOL, "Annuler", NamedTextColor.RED));
        player.openInventory(holder.inventory);
    }

    private ItemStack listingItem(AuctionListing listing, boolean own) {
        var item = AuctionManager.deserialize(listing.itemYaml());
        if (item == null)
            return button(Material.BARRIER, "Annonce indisponible", NamedTextColor.RED);
        item = item.clone();
        var meta = item.getItemMeta();
        var lore = new ArrayList<Component>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.empty());
        lore.add(Component.text("Vendeur : " + listing.sellerName(), NamedTextColor.GRAY));
        lore.add(
                Component.text(
                        "Prix : " + Numbers.compact(listing.price()) + " Écus",
                        NamedTextColor.GOLD));
        long minutes =
                Math.max(
                                0,
                                AuctionManager.EXPIRY_MILLIS
                                        - (System.currentTimeMillis() - listing.listedAt()))
                        / 60000;
        lore.add(
                Component.text(
                        "Expire dans " + minutes / 60 + " h " + minutes % 60 + " min",
                        NamedTextColor.GRAY));
        lore.add(
                Component.text(
                        own
                                ? "Clique pour retirer l’annonce."
                                : "Clique pour voir et confirmer l’achat.",
                        own ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
        meta.lore(
                lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String title, NamedTextColor color) {
        var item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(title, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.owner.equals(player.getUniqueId())
                || !player.hasPermission("cliker.ah.use")
                || event.getClickedInventory() != holder.inventory
                || (!event.isLeftClick() && !event.isRightClick())
                || holder.pending) return;
        int slot = event.getRawSlot();
        holder.pending = true;
        fr.cliker.clikerv2.runtime.ServerTasks.run(
                plugin,
                () -> {
                    if (!player.isOnline()
                            || player.getOpenInventory().getTopInventory() != holder.inventory)
                        return;
                    if (!player.hasPermission("cliker.ah.use")
                            || fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)) {
                        holder.pending = false;
                        return;
                    }
                    if (holder instanceof Listings page) {
                        holder.pending = false;
                        if (slot == 45) {
                            openSort(player, page.own, page.sort, page.query, page.category);
                            return;
                        }
                        if (slot == 46 && page.page > 0) {
                            openListings(
                                    player,
                                    page.own,
                                    page.page - 1,
                                    page.sort,
                                    page.query,
                                    page.category);
                            return;
                        }
                        if (slot == 53) {
                            openListings(
                                    player,
                                    page.own,
                                    page.page + 1,
                                    page.sort,
                                    page.query,
                                    page.category);
                            return;
                        }
                        if (slot == 49) {
                            openListings(
                                    player, !page.own, 0, page.sort, page.query, page.category);
                            return;
                        }
                        if (slot == 50) {
                            openListings(
                                    player,
                                    page.own,
                                    page.page,
                                    page.sort,
                                    page.query,
                                    page.category);
                            return;
                        }
                        if (slot == 47) {
                            filterMenu(player, page);
                            return;
                        }
                        if (slot == 51) {
                            player.closeInventory();
                            player.sendMessage(
                                    Component.text(
                                                    "Clique ici puis saisis le nom : /ah rechercher"
                                                            + " ",
                                                    NamedTextColor.YELLOW)
                                            .clickEvent(
                                                    net.kyori.adventure.text.event.ClickEvent
                                                            .suggestCommand("/ah rechercher ")));
                            return;
                        }
                        if (slot == 52) {
                            if (page.query.isBlank()) {
                                openFavorites(player);
                                return;
                            }
                            var saved = favorites(player);
                            if (!saved.remove(page.query)) {
                                if (saved.size() >= 10) {
                                    player.sendMessage(
                                            "10 recherches favorites au maximum. Retire-en une"
                                                    + " avant d’en ajouter.");
                                    return;
                                }
                                saved.add(page.query);
                            }
                            player.getPersistentDataContainer()
                                    .set(
                                            new NamespacedKey(plugin, "ah_searches"),
                                            org.bukkit.persistence.PersistentDataType.STRING,
                                            String.join("\n", saved));
                            openListings(
                                    player,
                                    page.own,
                                    page.page,
                                    page.sort,
                                    page.query,
                                    page.category);
                            return;
                        }
                        if (slot < 0 || slot >= page.ids.size()) return;
                        String id = page.ids.get(slot);
                        var listing = manager.get(id);
                        if (listing == null) {
                            player.sendMessage(
                                    Component.text(
                                            "Cette annonce n’est plus disponible.",
                                            NamedTextColor.YELLOW));
                            openListings(
                                    player,
                                    page.own,
                                    page.page,
                                    page.sort,
                                    page.query,
                                    page.category);
                            return;
                        }
                        if (page.own) {
                            if (manager.cancel(player, id, false))
                                player.sendMessage(
                                        Component.text(
                                                "Retrait demandé. Objet livré ou disponible dans"
                                                        + " /claim.",
                                                NamedTextColor.GREEN));
                            openListings(player, true, page.page);
                        } else openBuyConfirm(player, listing);
                    } else if (holder instanceof Confirmation confirm) {
                        if (slot == 22 && confirm.listing == null) {
                            confirm.advertise = !confirm.advertise;
                            confirm.inventory.setItem(
                                    22,
                                    button(
                                            Material.PAPER,
                                            "Annonce chat : "
                                                    + (confirm.advertise ? "OUI" : "NON")
                                                    + " — supplément 5 % ("
                                                    + AuctionStore.advertisingFee(confirm.price)
                                                    + " Écus)",
                                            NamedTextColor.GOLD));
                            fr.cliker.clikerv2.ui.MenuIcons.decorate(confirm.inventory);
                            holder.pending = false;
                            return;
                        }
                        if (slot == CANCEL) {
                            openBrowse(player);
                            return;
                        }
                        if (slot != CONFIRM) {
                            holder.pending = false;
                            return;
                        }
                        if (confirm.listing != null) {
                            manager.buy(
                                    player,
                                    confirm.listing,
                                    () -> {
                                        if (player.isOnline()
                                                && player.getOpenInventory().getTopInventory()
                                                        == holder.inventory) openBrowse(player);
                                    });
                            return;
                        }
                        var hand = player.getInventory().getItemInMainHand();
                        if (!hand.isSimilar(confirm.item)
                                || hand.getAmount() < confirm.item.getAmount()) {
                            player.sendMessage(
                                    Component.text(
                                            "Tu ne tiens plus le même objet. Vente annulée.",
                                            NamedTextColor.RED));
                            player.closeInventory();
                            return;
                        }
                        if (fr.cliker.clikerv2.comfort.PlayerComfort.protectedForSale(
                                player, hand)) {
                            player.sendMessage(
                                    "Équipement protégé : vente annulée. Vérifie /lock et"
                                            + " /antidrop.");
                            player.closeInventory();
                            return;
                        }
                        int remaining = hand.getAmount() - confirm.item.getAmount();
                        hand.setAmount(remaining);
                        player.getInventory().setItemInMainHand(remaining == 0 ? null : hand);
                        manager.createListing(
                                player, confirm.item, confirm.price, confirm.advertise);
                        player.sendMessage(
                                Component.text(
                                        "Publication de l’annonce en cours…",
                                        NamedTextColor.GREEN));
                        player.closeInventory();
                    }
                });
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true);
    }

    private abstract static class View implements InventoryHolder {
        final UUID owner;
        Inventory inventory;
        boolean pending;

        View(UUID owner) {
            this.owner = owner;
        }

        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class Listings extends View {
        int sort;
        String query = "", category = "tous";
        final boolean own;
        final int page;
        final List<String> ids;

        Listings(UUID owner, boolean own, int page, List<String> ids) {
            super(owner);
            this.own = own;
            this.page = page;
            this.ids = ids;
        }
    }

    private static final class Confirmation extends View {
        final String listing;
        final ItemStack item;
        final long price;
        boolean advertise;

        Confirmation(UUID owner, String listing, ItemStack item, long price) {
            super(owner);
            this.listing = listing;
            this.item = item;
            this.price = price;
        }
    }
}
