package fr.cliker.clikerv2.auction;

import fr.cliker.clikerv2.EconomyService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Cœur de l'hôtel des ventes : listings en cache mémoire (chargées depuis la BDD au démarrage),
 * achat/vente/annulation. Anti-dupe : l'item est retiré de l'inventaire du vendeur AVANT que la
 * listing existe (côté appelant), et une listing en cours d'achat est verrouillée synchronement
 * (avant tout appel async) pour empêcher un double achat. Une annonce expire après 72h : elle
 * disparaît de la vente, et l'item est rendu au vendeur (direct s'il est en ligne, sinon à sa
 * prochaine connexion — la ligne reste en BDD marquée "expired" jusqu'à la livraison).
 */
public final class AuctionManager implements Listener {
    public static final double TAX_RATE = 0.01;
    public static final long EXPIRY_MILLIS = TimeUnit.HOURS.toMillis(72);

    private final JavaPlugin plugin;
    private final AuctionStore store;
    private final EconomyService economy;
    private final Map<String, AuctionListing> listings = new LinkedHashMap<>();
    private final Set<String> locked = ConcurrentHashMap.newKeySet();

    public AuctionManager(JavaPlugin plugin, AuctionStore store, EconomyService economy) {
        this.plugin = plugin;
        this.store = store;
        this.economy = economy;
    }

    public void load() {
        store.loadActive()
                .whenComplete(
                        (points, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .severe(
                                                                "Impossible de charger"
                                                                        + " l'hôtel des ventes"
                                                                        + " : "
                                                                        + error.getMessage());
                                                return;
                                            }
                                            for (AuctionListing listing : points)
                                                listings.put(listing.id(), listing);
                                            sweepExpired();
                                        }));
    }

    /**
     * Vérifie les annonces expirées (72h) toutes les 5 min : les retire de la vente et rend l'item
     * si possible.
     */
    public void start() {
        plugin.getServer()
                .getScheduler()
                .runTaskTimer(plugin, this::sweepExpired, 20L * 60 * 5, 20L * 60 * 5);
    }

    private void sweepExpired() {
        long now = System.currentTimeMillis();
        for (AuctionListing listing : new ArrayList<>(listings.values())) {
            if (locked.contains(listing.id())) continue;
            if (now - listing.listedAt() < EXPIRY_MILLIS) continue;
            listings.remove(listing.id());
            deliverOrMarkExpired(listing);
        }
    }

    private fr.cliker.clikerv2.rewards.ClaimService delivery() {
        return java.util.Objects.requireNonNull(
                Bukkit.getServicesManager().load(fr.cliker.clikerv2.rewards.ClaimService.class));
    }

    private void returnListing(AuctionListing listing) {
        store.returnToOwner(listing.id())
                .whenComplete(
                        (ok, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            locked.remove(listing.id());
                                            if (error != null) {
                                                listings.put(listing.id(), listing);
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Retour d'annonce" + " impossible",
                                                                error);
                                                return;
                                            }
                                            listings.remove(listing.id());
                                            delivery().notifyAndDeliver(listing.sellerUuid());
                                            Player seller = Bukkit.getPlayer(listing.sellerUuid());
                                            if (Boolean.TRUE.equals(ok) && seller != null)
                                                seller.sendMessage(
                                                        Component.text(
                                                                "Annonce retirée ou expirée"
                                                                        + " : objet livré ou"
                                                                        + " disponible dans"
                                                                        + " /claim.",
                                                                NamedTextColor.YELLOW));
                                        }));
    }

    private void deliverOrMarkExpired(AuctionListing listing) {
        locked.add(listing.id());
        returnListing(listing);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        store.loadExpiredFor(event.getPlayer().getUniqueId())
                .whenComplete(
                        (expired, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (error != null) return;
                                            for (var listing : expired)
                                                if (locked.add(listing.id()))
                                                    returnListing(listing);
                                        }));
    }

    public List<AuctionListing> activeListings() {
        List<AuctionListing> result = new ArrayList<>(listings.values());
        result.removeIf(listing -> locked.contains(listing.id()));
        result.sort(
                Comparator.comparingLong(AuctionListing::listedAt)
                        .thenComparing(AuctionListing::id));
        return result;
    }

    public List<AuctionListing> listingsOf(UUID seller) {
        List<AuctionListing> result = new ArrayList<>();
        for (AuctionListing listing : listings.values()) {
            if (listing.sellerUuid().equals(seller)) result.add(listing);
        }
        result.sort(
                Comparator.comparingLong(AuctionListing::listedAt)
                        .thenComparing(AuctionListing::id));
        return result;
    }

    public static String serialize(ItemStack item) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item", item);
        return config.saveToString();
    }

    public static ItemStack deserialize(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            return null;
        }
        return config.getItemStack("item");
    }

    /**
     * L'appelant doit avoir DÉJÀ retiré l'item de l'inventaire du vendeur avant d'appeler cette
     * méthode.
     */
    public void createListing(Player seller, ItemStack item, long price) {
        createListing(seller, item, price, false);
    }

    public void createListing(Player seller, ItemStack item, long price, boolean advertise) {
        String id = UUID.randomUUID().toString();
        AuctionListing listing =
                new AuctionListing(
                        id,
                        seller.getUniqueId(),
                        seller.getName(),
                        serialize(item),
                        price,
                        System.currentTimeMillis());
        store.publish(listing, advertise)
                .whenComplete(
                        (ignored, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (error != null) {
                                                delivery()
                                                        .give(
                                                                seller.getUniqueId(),
                                                                seller.getName(),
                                                                item);
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Création d'annonce"
                                                                        + " impossible ; objet"
                                                                        + " rendu",
                                                                error);
                                                if (seller.isOnline())
                                                    seller.sendMessage(
                                                            Component.text(
                                                                    "Annonce impossible :"
                                                                            + " objet rendu ou"
                                                                            + " disponible dans"
                                                                            + " /claim.",
                                                                    NamedTextColor.RED));
                                            } else {
                                                listings.put(id, listing);
                                                if (advertise) {
                                                    var message =
                                                            Component.text(
                                                                            "[AH] "
                                                                                    + seller
                                                                                            .getName()
                                                                                    + " vend "
                                                                                    + item
                                                                                            .getAmount()
                                                                                    + " × ",
                                                                            NamedTextColor.GOLD)
                                                                    .append(
                                                                            item.getItemMeta()
                                                                                            .hasDisplayName()
                                                                                    ? item.getItemMeta()
                                                                                            .displayName()
                                                                                    : Component
                                                                                            .translatable(
                                                                                                    item.getType()
                                                                                                            .translationKey()))
                                                                    .append(
                                                                            Component.text(
                                                                                    " pour " + price
                                                                                            + " Écus"
                                                                                            + " — /ah"))
                                                                    .clickEvent(
                                                                            net.kyori.adventure.text
                                                                                    .event
                                                                                    .ClickEvent
                                                                                    .runCommand(
                                                                                            "/ah"));
                                                    Bukkit.broadcast(message);
                                                }
                                                if (seller.isOnline())
                                                    seller.sendMessage(
                                                            Component.text(
                                                                    "Annonce publiée : "
                                                                            + price
                                                                            + " Écus.",
                                                                    NamedTextColor.GREEN));
                                            }
                                        }));
    }

    public void buy(Player buyer, String listingId, Runnable onDone) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null || locked.contains(listingId)) {
            buyer.sendMessage(
                    Component.text("Cette annonce n'est plus disponible.", NamedTextColor.RED));
            if (onDone != null) onDone.run();
            return;
        }
        if (listing.sellerUuid().equals(buyer.getUniqueId())) {
            buyer.sendMessage(
                    Component.text(
                            "Tu ne peux pas acheter ta propre annonce.", NamedTextColor.RED));
            if (onDone != null) onDone.run();
            return;
        }
        locked.add(listingId);

        economy.purchaseAuction(buyer.getUniqueId(), buyer.getName(), listingId)
                .whenComplete(
                        (result, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            locked.remove(listingId);
                                            if (error != null
                                                    || result
                                                            != EconomyService.AuctionPurchase
                                                                    .PURCHASED) {
                                                if (result
                                                        == EconomyService.AuctionPurchase
                                                                .UNAVAILABLE)
                                                    listings.remove(listingId);
                                                if (buyer.isOnline())
                                                    buyer.sendMessage(
                                                            Component.text(
                                                                    error != null
                                                                            ? "Achat"
                                                                                    + " impossible."
                                                                                    + " Aucun"
                                                                                    + " débit"
                                                                                    + " validé."
                                                                            : result
                                                                                            == EconomyService
                                                                                                    .AuctionPurchase
                                                                                                    .INSUFFICIENT_FUNDS
                                                                                    ? "Écus insuffisants."
                                                                                    : "Cette"
                                                                                          + " annonce"
                                                                                          + " n’est"
                                                                                          + " plus"
                                                                                          + " disponible.",
                                                                    NamedTextColor.RED));
                                                if (error != null)
                                                    plugin.getLogger()
                                                            .log(
                                                                    java.util.logging.Level.SEVERE,
                                                                    "Transaction d'annonce"
                                                                            + " impossible",
                                                                    error);
                                            } else {
                                                listings.remove(listingId);
                                                delivery().notifyAndDeliver(buyer.getUniqueId());
                                                if (buyer.isOnline())
                                                    buyer.sendMessage(
                                                            Component.text(
                                                                    "Achat effectué : -"
                                                                            + listing.price()
                                                                            + " Écus. Objet"
                                                                            + " livré ou"
                                                                            + " disponible"
                                                                            + " dans"
                                                                            + " /claim.",
                                                                    NamedTextColor.GREEN));
                                                Player seller =
                                                        Bukkit.getPlayer(listing.sellerUuid());
                                                if (seller != null)
                                                    seller.sendMessage(
                                                            Component.text(
                                                                    "Annonce vendue ! Écus crédités"
                                                                            + " après frais de"
                                                                            + " publication non"
                                                                            + " remboursables.",
                                                                    NamedTextColor.GREEN));
                                            }
                                            if (onDone != null) onDone.run();
                                        }));
    }

    public boolean cancel(Player seller, String listingId, boolean admin) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null || locked.contains(listingId)) return false;
        if (!admin && !listing.sellerUuid().equals(seller.getUniqueId())) return false;

        locked.add(listingId);
        returnListing(listing);
        return true;
    }

    public AuctionListing get(String id) {
        return listings.get(id);
    }
}
