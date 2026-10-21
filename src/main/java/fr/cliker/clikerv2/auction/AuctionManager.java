package fr.cliker.clikerv2.auction;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.economy.CurrencyType;

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
 * Cœur de l'hôtel des ventes : listings en cache mémoire (chargées depuis la BDD au démarrage), achat/vente/annulation.
 * Anti-dupe : l'item est retiré de l'inventaire du vendeur AVANT que la listing existe (côté appelant), et une
 * listing en cours d'achat est verrouillée synchronement (avant tout appel async) pour empêcher un double achat.
 * Une annonce expire après 72h : elle disparaît de la vente, et l'item est rendu au vendeur (direct s'il est en
 * ligne, sinon à sa prochaine connexion — la ligne reste en BDD marquée "expired" jusqu'à la livraison).
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
        store.loadActive().whenComplete((points, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) { plugin.getLogger().severe("Impossible de charger l'hôtel des ventes : " + error.getMessage()); return; }
            for (AuctionListing listing : points) listings.put(listing.id(), listing);
        }));
    }

    /** Vérifie les annonces expirées (72h) toutes les 5 min : les retire de la vente et rend l'item si possible. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweepExpired, 20L * 60 * 5, 20L * 60 * 5);
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

    private void deliverOrMarkExpired(AuctionListing listing) {
        Player seller = Bukkit.getPlayer(listing.sellerUuid());
        if (seller != null && seller.isOnline()) {
            ItemStack item = deserialize(listing.itemYaml());
            if (item != null) {
                var leftover = seller.getInventory().addItem(item);
                leftover.values().forEach(leftoverItem -> seller.getWorld().dropItemNaturally(seller.getLocation(), leftoverItem));
            }
            store.removeById(listing.id());
            seller.sendMessage(Component.text("⌛ Ton annonce (" + listing.price() + " Écus) a expiré après 72h, item rendu.", NamedTextColor.YELLOW));
        } else {
            store.markExpired(listing.id());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        store.loadExpiredFor(player.getUniqueId()).whenComplete((expired, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null || expired.isEmpty() || !player.isOnline()) return;
            for (AuctionListing listing : expired) {
                ItemStack item = deserialize(listing.itemYaml());
                if (item != null) {
                    var leftover = player.getInventory().addItem(item);
                    leftover.values().forEach(leftoverItem -> player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem));
                }
                store.removeById(listing.id());
            }
            player.sendMessage(Component.text("⌛ " + expired.size() + " annonce(s) expirée(s) pendant ton absence, items rendus.", NamedTextColor.YELLOW));
        }));
    }

    public List<AuctionListing> activeListings() {
        List<AuctionListing> result = new ArrayList<>(listings.values());
        result.removeIf(listing -> locked.contains(listing.id()));
        result.sort(Comparator.comparingLong(AuctionListing::listedAt));
        return result;
    }

    public List<AuctionListing> listingsOf(UUID seller) {
        List<AuctionListing> result = new ArrayList<>();
        for (AuctionListing listing : listings.values()) {
            if (listing.sellerUuid().equals(seller)) result.add(listing);
        }
        result.sort(Comparator.comparingLong(AuctionListing::listedAt));
        return result;
    }

    public static String serialize(ItemStack item) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item", item);
        return config.saveToString();
    }

    public static ItemStack deserialize(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(yaml); } catch (org.bukkit.configuration.InvalidConfigurationException exception) { return null; }
        return config.getItemStack("item");
    }

    /** L'appelant doit avoir DÉJÀ retiré l'item de l'inventaire du vendeur avant d'appeler cette méthode. */
    public void createListing(Player seller, ItemStack item, long price) {
        String id = UUID.randomUUID().toString();
        AuctionListing listing = new AuctionListing(id, seller.getUniqueId(), seller.getName(), serialize(item), price, System.currentTimeMillis());
        listings.put(id, listing);
        store.save(listing);
    }

    public void buy(Player buyer, String listingId, Runnable onDone) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null || locked.contains(listingId)) {
            buyer.sendMessage(Component.text("Cette annonce n'est plus disponible.", NamedTextColor.RED));
            if (onDone != null) onDone.run();
            return;
        }
        if (listing.sellerUuid().equals(buyer.getUniqueId())) {
            buyer.sendMessage(Component.text("Tu ne peux pas acheter ta propre annonce.", NamedTextColor.RED));
            if (onDone != null) onDone.run();
            return;
        }
        locked.add(listingId);

        economy.change(buyer.getUniqueId(), buyer.getName(), CurrencyType.ECUS, -listing.price()).whenComplete((balance, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) {
                locked.remove(listingId);
                buyer.sendMessage(Component.text("Écus insuffisants.", NamedTextColor.RED));
                if (onDone != null) onDone.run();
                return;
            }

            listings.remove(listingId);
            locked.remove(listingId);
            store.removeById(listingId);

            ItemStack item = deserialize(listing.itemYaml());
            if (item != null) {
                var leftover = buyer.getInventory().addItem(item);
                leftover.values().forEach(leftoverItem -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), leftoverItem));
            }

            long tax = Math.round(listing.price() * TAX_RATE);
            long sellerProceeds = listing.price() - tax;
            economy.change(listing.sellerUuid(), listing.sellerName(), CurrencyType.ECUS, sellerProceeds);

            buyer.sendMessage(Component.text("✦ Achat effectué : ", NamedTextColor.GREEN)
                    .append(Component.text("-" + listing.price() + " Écus", NamedTextColor.GOLD)));
            Player sellerPlayer = Bukkit.getPlayer(listing.sellerUuid());
            if (sellerPlayer != null) {
                sellerPlayer.sendMessage(Component.text("✦ Annonce vendue : ", NamedTextColor.GREEN)
                        .append(Component.text("+" + sellerProceeds + " Écus", NamedTextColor.GOLD))
                        .append(Component.text(" (taxe 1% : -" + tax + ")", NamedTextColor.GRAY)));
            }
            if (onDone != null) onDone.run();
        }));
    }

    public boolean cancel(Player seller, String listingId, boolean admin) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null || locked.contains(listingId)) return false;
        if (!admin && !listing.sellerUuid().equals(seller.getUniqueId())) return false;

        listings.remove(listingId);
        store.removeById(listingId);
        ItemStack item = deserialize(listing.itemYaml());
        if (item != null) {
            var leftover = seller.getInventory().addItem(item);
            leftover.values().forEach(leftoverItem -> seller.getWorld().dropItemNaturally(seller.getLocation(), leftoverItem));
        }
        return true;
    }

    public AuctionListing get(String id) { return listings.get(id); }
}
