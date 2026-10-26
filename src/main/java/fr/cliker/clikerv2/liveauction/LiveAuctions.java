package fr.cliker.clikerv2.liveauction;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.gear.TierGear;
import fr.cliker.clikerv2.rewards.ClaimService;
import fr.cliker.clikerv2.runtime.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** Enchères événementielles, indépendantes des annonces de l'AH. */
public final class LiveAuctions implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final LiveAuctionStore store;
    private final EconomyService economy;
    private final ClaimService claims;
    private final Settings config;
    private final AuctionRoom room;
    private final long entryFee;
    private final List<Long> prices;
    private final long interval, registration, bidTime;
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, org.bukkit.profile.PlayerProfile> profiles = new HashMap<>();
    private final Set<UUID> profileRequests = new HashSet<>();
    private final Set<UUID> pending = new HashSet<>();
    private final Map<UUID, Object> joining = new HashMap<>();
    private LiveAuctionStore.State state;
    private long next, lastRemaining;
    private boolean ready, changing, stopped;
    private BukkitTask task;

    private static final class View implements InventoryHolder {
        final UUID owner;
        final String event;
        final Inventory inventory;
        int page;
        long balance;
        boolean loading;

        View(Player p, String event) {
            owner = p.getUniqueId();
            this.event = event;
            inventory = Bukkit.createInventory(this, 54, Component.text("Enchères légendaires"));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public LiveAuctions(
            JavaPlugin plugin, DatabaseManager db, EconomyService economy, ClaimService claims) {
        this.plugin = plugin;
        this.store = new LiveAuctionStore(db);
        this.economy = economy;
        this.claims = claims;
        config = new Settings(plugin, "activites/encheres.yml");
        entryFee = config.yaml.getLong("entree-ecus", 10_000_000);
        if (entryFee < 0 || entryFee > 1_000_000_000_000_000L)
            throw new IllegalArgumentException("Frais d’entrée invalides");
        room = new AuctionRoom(plugin, config, this::openCurrent, this::leave);
        interval = seconds("intervalle-secondes", 10800, 60, 604800);
        registration = seconds("inscriptions-secondes", 180, 1, 3600);
        bidTime = seconds("offre-secondes", 30, 1, 300);
        prices =
                BidPrices.generate(
                        config.yaml.getLong("prix.depart", 1000),
                        config.yaml.getLong("prix.maximum", 1_000_000_000_000_000L),
                        config.yaml.getInt("prix.augmentation-pourcent", 20));
        // Refuser une configuration qui introduirait involontairement un autre tier de rareté.
        for (String zone : config.yaml.getStringList("lots.zones")) {
            var tier = MobTier.parse(zone);
            if (!TierGear.isLegendary(tier))
                throw new IllegalArgumentException("Zone non légendaire : " + zone);
        }
        if (config.yaml.getStringList("lots.zones").isEmpty()
                || config.yaml.getStringList("lots.types").isEmpty())
            throw new IllegalArgumentException("Catalogue d’enchères vide");
        for (String type : config.yaml.getStringList("lots.types"))
            if (!List.of("epee", "casque", "plastron", "jambieres", "bottes").contains(type))
                throw new IllegalArgumentException("Type de lot inconnu : " + type);
    }

    private long seconds(String key, long fallback, long minimum, long maximum) {
        long n = config.yaml.getLong(key, fallback);
        if (n < minimum || n > maximum)
            throw new IllegalArgumentException("Durée d’enchère invalide : " + key);
        return n * 1000;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        room.start();
        var command = Objects.requireNonNull(plugin.getCommand("enchere"));
        command.setExecutor(this);
        command.setTabCompleter(this);
        next = config.yaml.getLong("prochaine-inscription", 0);
        if (next <= 0) schedule();
        store.initialize()
                .whenComplete(
                        (loaded, error) ->
                                sync(
                                        () -> {
                                            if (error != null) {
                                                log(
                                                        "Initialisation des enchères impossible",
                                                        error);
                                                return;
                                            }
                                            ready = true;
                                            apply(loaded);
                                            lastRemaining = remaining();
                                        }));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    private void schedule() {
        next = System.currentTimeMillis() + interval;
        config.yaml.set("prochaine-inscription", next);
        config.save();
    }

    private long remaining() {
        return state == null
                ? 0
                : Math.max(0, (state.deadline() - System.currentTimeMillis() + 999) / 1000);
    }

    private void tick() {
        if (!ready || !config.yaml.getBoolean("active", true)) return;
        if (state == null || state.phase().equals("DONE")) {
            if (!changing && System.currentTimeMillis() >= next) create(null);
            return;
        }
        long seconds = remaining();
        if (state.phase().equals("REGISTERING")) {
            boolean reminder = false;
            for (int threshold : config.yaml.getIntegerList("rappels-secondes"))
                if (lastRemaining > threshold && seconds <= threshold && seconds > 0)
                    reminder = true;
            if (reminder) registrationAnnouncement(seconds);
            lastRemaining = seconds;
        }
        if (System.currentTimeMillis() >= state.deadline() && !changing) advance(false);
        room.update(state, seconds);
    }

    private List<ItemStack> lots() {
        var candidates = new ArrayList<ItemStack>();
        for (String id : config.yaml.getStringList("lots.zones"))
            for (String type : config.yaml.getStringList("lots.types")) {
                MobTier tier = Objects.requireNonNull(MobTier.parse(id));
                candidates.add(
                        type.equals("epee")
                                ? TierGear.createSword(tier)
                                : TierGear.createArmor(
                                        tier,
                                        switch (type) {
                                            case "casque" -> TierGear.ArmorSlot.HELMET;
                                            case "plastron" -> TierGear.ArmorSlot.CHESTPLATE;
                                            case "jambieres" -> TierGear.ArmorSlot.LEGGINGS;
                                            default -> TierGear.ArmorSlot.BOOTS;
                                        }));
            }
        Collections.shuffle(candidates, ThreadLocalRandom.current());
        if (candidates.size() < 3)
            throw new IllegalArgumentException("Au moins trois lots distincts sont nécessaires");
        return candidates.subList(0, 3);
    }

    private void create(CommandSender sender) {
        if (!ready || changing || (state != null && !state.phase().equals("DONE"))) {
            if (sender != null)
                sender.sendMessage("Une enchère est déjà en cours ou en chargement.");
            return;
        }
        if (!config.yaml.getBoolean("active", true)) {
            if (sender != null)
                sender.sendMessage("Les enchères sont désactivées dans activites/encheres.yml.");
            return;
        }
        if (!room.configured()) {
            if (sender != null)
                sender.sendMessage(
                        "§cDéfinis la salle avec /enchere setsalle, puis le présentoir avec"
                            + " /enchere setpresentoir.");
            schedule();
            return;
        }
        List<String> payloads;
        try {
            payloads = lots().stream().map(ClaimService::serialize).toList();
        } catch (RuntimeException e) {
            log("Catalogue d’enchères invalide", e);
            if (sender != null) sender.sendMessage("Catalogue invalide. Consulte la console.");
            schedule();
            return;
        }
        changing = true;
        store.create(UUID.randomUUID().toString(), payloads, registration)
                .whenComplete(
                        (created, error) ->
                                sync(
                                        () -> {
                                            changing = false;
                                            if (error != null) {
                                                log("Création d’enchère impossible", error);
                                                schedule();
                                                return;
                                            }
                                            apply(created);
                                            lastRemaining = remaining();
                                            schedule();
                                            registrationAnnouncement(remaining());
                                        }));
    }

    private void registrationAnnouncement(long seconds) {
        Bukkit.broadcast(
                DailySettings.color(
                                text("inscription").replace("{secondes}", Long.toString(seconds))
                                        + " &7— Entrée : &e"
                                        + Numbers.compact(entryFee)
                                        + " Écus &7(non remboursable)")
                        .clickEvent(ClickEvent.runCommand("/enchere rejoindre")));
    }

    private void apply(LiveAuctionStore.State updated) {
        if (updated == null) {
            state = null;
            return;
        }
        if (state != null
                && state.id().equals(updated.id())
                && updated.revision() < state.revision()) return;
        if (state != null && !state.id().equals(updated.id()) && !state.phase().equals("DONE"))
            return;
        boolean newLot =
                state == null
                        || !state.id().equals(updated.id())
                        || state.lot() != updated.lot()
                        || !state.phase().equals(updated.phase());
        if (state != null && !state.id().equals(updated.id())) {
            profiles.clear();
            profileRequests.clear();
        }
        state = updated;
        for (View v : new ArrayList<>(views.values())) {
            Player p = Bukkit.getPlayer(v.owner);
            if (p != null && p.getOpenInventory().getTopInventory() == v.inventory) {
                if (newLot && updated.phase().equals("ACTIVE")) replaceView(p, v, 0, true);
                else {
                    int leader = Collections.binarySearch(prices, updated.price());
                    int target =
                            leader < 0
                                    ? v.page
                                    : Math.min(
                                            (prices.size() - 1) / BidPrices.PAGE_SIZE,
                                            (leader + 1) / BidPrices.PAGE_SIZE);
                    if (target > v.page && updated.phase().equals("ACTIVE"))
                        replaceView(p, v, target, false);
                    else render(v);
                }
            }
        }
        if (newLot && updated.phase().equals("ACTIVE"))
            for (UUID owner : new ArrayList<>(room.present)) {
                Player player = Bukkit.getPlayer(owner);
                if (player != null && !views.containsKey(owner)) openCurrent(player);
            }
        if (updated.phase().equals("DONE")) room.update(updated, 0);
    }

    private void openCurrent(Player player) {
        if (state == null || !room.present.contains(player.getUniqueId()) || !room.inside(player))
            return;
        if (!state.phase().equals("ACTIVE")) {
            player.sendMessage(
                    "§eLa vente de ce lot commencera dans " + remaining() + " secondes.");
            return;
        }
        View view = new View(player, state.id());
        int leader = Collections.binarySearch(prices, state.price());
        view.page =
                leader < 0
                        ? 0
                        : Math.min(
                                (prices.size() - 1) / BidPrices.PAGE_SIZE,
                                (leader + 1) / BidPrices.PAGE_SIZE);
        views.put(player.getUniqueId(), view);
        render(view);
        player.openInventory(view.inventory);
        refreshBalance(player, view);
    }

    /** Nouveau conteneur sur changement de contexte : les anciens paquets client sont invalides. */
    private void replaceView(Player player, View previous, int page, boolean refresh) {
        if (views.get(previous.owner) != previous
                || player.getOpenInventory().getTopInventory() != previous.inventory) return;
        View nextView = new View(player, previous.event);
        nextView.page = page;
        nextView.balance = previous.balance;
        views.put(previous.owner, nextView);
        render(nextView);
        player.openInventory(nextView.inventory);
        if (refresh) refreshBalance(player, nextView);
    }

    private void advance(boolean force) {
        if (state == null || changing) return;
        changing = true;
        String event = state.id();
        String previous = state.phase();
        store.advance(event, bidTime, force)
                .whenComplete(
                        (result, error) ->
                                sync(
                                        () -> {
                                            changing = false;
                                            if (error != null) {
                                                log("Attribution d’enchère à réessayer", error);
                                                return;
                                            }
                                            var updated = result.state();
                                            apply(updated);
                                            if (updated == null) return;
                                            var sale = result.sale();
                                            if (sale != null) {
                                                if (sale.winner() != null) {
                                                    Bukkit.broadcast(
                                                            DailySettings.color(
                                                                    text("gagne")
                                                                            .replace(
                                                                                    "{joueur}",
                                                                                    sale.name())
                                                                            .replace(
                                                                                    "{prix}",
                                                                                    Numbers.compact(
                                                                                            sale
                                                                                                    .price()))
                                                                            .replace(
                                                                                    "{lot}",
                                                                                    Integer
                                                                                            .toString(
                                                                                                    sale
                                                                                                                    .lot()
                                                                                                            + 1))));
                                                    claims.notifyAndDeliver(sale.winner());
                                                } else
                                                    Bukkit.broadcast(
                                                            DailySettings.color(
                                                                    text("invendu")
                                                                            .replace(
                                                                                    "{lot}",
                                                                                    Integer
                                                                                            .toString(
                                                                                                    sale
                                                                                                                    .lot()
                                                                                                            + 1))));
                                            }
                                            if (updated.phase().equals("DONE")) {
                                                Bukkit.broadcast(
                                                        DailySettings.color(
                                                                text(previous.equals("REGISTERING")
                                                                                        && updated
                                                                                                        .members()
                                                                                                == 0
                                                                                ? "sans-inscrits"
                                                                                : "fin")
                                                                        .replace(
                                                                                "{minutes}",
                                                                                Long.toString(
                                                                                        Math.max(
                                                                                                0,
                                                                                                (next
                                                                                                                - System
                                                                                                                        .currentTimeMillis()
                                                                                                                + 59999)
                                                                                                        / 60000)))));
                                                if (next <= System.currentTimeMillis()) schedule();
                                                for (View v : new ArrayList<>(views.values())) {
                                                    Player p = Bukkit.getPlayer(v.owner);
                                                    if (p != null
                                                            && p.getOpenInventory()
                                                                            .getTopInventory()
                                                                    == v.inventory)
                                                        p.closeInventory();
                                                }
                                                views.clear();
                                            } else if (previous.equals("REGISTERING")
                                                    && updated.phase().equals("ACTIVE")) {
                                                Bukkit.broadcast(
                                                        DailySettings.color(text("debut"))
                                                                .clickEvent(
                                                                        ClickEvent.runCommand(
                                                                                "/enchere"
                                                                                    + " rejoindre")));
                                                announceLot();
                                            } else if (sale != null) announceLot();
                                        }));
    }

    private void announceLot() {
        Bukkit.broadcast(
                DailySettings.color(
                        text("lot")
                                .replace("{lot}", Integer.toString(state.lot() + 1))
                                .replace("{secondes}", Long.toString(bidTime / 1000))));
    }

    private void join(Player player) {
        if (!ready || state == null || state.phase().equals("DONE")) {
            player.sendMessage("Aucune enchère ouverte pour le moment.");
            return;
        }
        if (isolated(player) || joining.containsKey(player.getUniqueId())) return;
        String id = state.id();
        UUID owner = player.getUniqueId();
        Object token = new Object();
        joining.put(owner, token);
        if (!room.configured()) {
            joining.remove(owner);
            player.sendMessage("§cLa salle d’enchères n’est pas encore définie.");
            return;
        }
        store.join(id, owner, entryFee)
                .whenComplete(
                        (allowed, error) ->
                                sync(
                                        () -> {
                                            if (joining.get(owner) != token) return;
                                            if (!PlayerSessions.isCurrent(player)) {
                                                joining.remove(owner, token);
                                                return;
                                            }
                                            if (error != null) {
                                                joining.remove(owner, token);
                                                log("Inscription d’enchère impossible", error);
                                                player.sendMessage(
                                                        "Inscription indisponible. Réessaie dans un"
                                                                + " instant.");
                                                return;
                                            }
                                            if (!allowed) {
                                                joining.remove(owner, token);
                                                player.sendMessage(
                                                        "Inscription refusée : solde insuffisant"
                                                            + " pour l’entrée ou inscriptions"
                                                            + " fermées ; seuls les joueurs déjà"
                                                            + " inscrits peuvent rejoindre.");
                                                return;
                                            }
                                            store.state(id)
                                                    .whenComplete(
                                                            (fresh, failure) ->
                                                                    sync(
                                                                            () -> {
                                                                                if (!joining.remove(
                                                                                        owner,
                                                                                        token))
                                                                                    return;
                                                                                if (failure
                                                                                        != null) {
                                                                                    log(
                                                                                            "Lecture"
                                                                                                + " d’enchère"
                                                                                                + " impossible",
                                                                                            failure);
                                                                                    return;
                                                                                }
                                                                                if (!PlayerSessions
                                                                                                .isCurrent(
                                                                                                        player)
                                                                                        || fresh
                                                                                                == null
                                                                                        || fresh.phase()
                                                                                                .equals(
                                                                                                        "DONE")
                                                                                        || isolated(
                                                                                                player))
                                                                                    return;
                                                                                if (state == null
                                                                                        || !state.id()
                                                                                                .equals(
                                                                                                        id))
                                                                                    return;
                                                                                apply(fresh);
                                                                                if (!room.enter(
                                                                                        player))
                                                                                    return;
                                                                                if (state.phase()
                                                                                        .equals(
                                                                                                "ACTIVE"))
                                                                                    openCurrent(
                                                                                            player);
                                                                                else
                                                                                    player
                                                                                            .closeInventory();
                                                                                player.sendMessage(
                                                                                        "§aInscription"
                                                                                            + " confirmée."
                                                                                            + " §eEntrée"
                                                                                            + " : "
                                                                                                + Numbers
                                                                                                        .compact(
                                                                                                                entryFee)
                                                                                                + " Écus"
                                                                                                + " §7(non"
                                                                                                + " remboursable,"
                                                                                                + " une seule"
                                                                                                + " fois"
                                                                                                + " par événement)."
                                                                                                + " Les offres"
                                                                                                + " restent"
                                                                                                + " fermes"
                                                                                                + " après"
                                                                                                + " départ.");
                                                                            }));
                                        }));
    }

    private boolean isolated(Player p) {
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)) {
            p.sendMessage("Reviens au /spawn pour participer aux enchères.");
            return true;
        }
        return false;
    }

    private void refreshBalance(Player p, View v) {
        if (v.loading) return;
        v.loading = true;
        economy.getBalances(p.getUniqueId())
                .whenComplete(
                        (balances, error) ->
                                sync(
                                        () -> {
                                            v.loading = false;
                                            if (!PlayerSessions.isCurrent(p)
                                                    || views.get(v.owner) != v
                                                    || p.getOpenInventory().getTopInventory()
                                                            != v.inventory) return;
                                            if (error != null) {
                                                log("Solde d’enchère indisponible", error);
                                                return;
                                            }
                                            v.balance = balances.ecus();
                                            render(v);
                                        }));
    }

    private static ItemStack item(Material m, String title, String... lore) {
        var i = new ItemStack(m);
        var meta = i.getItemMeta();
        meta.displayName(DailySettings.color(title));
        meta.lore(Arrays.stream(lore).map(DailySettings::color).toList());
        i.setItemMeta(meta);
        return i;
    }

    /** Réutilise le skin connu ; une résolution absente ne bloque jamais le thread serveur. */
    private ItemStack playerHead(UUID owner, String name, String title, String... lore) {
        var item = item(Material.PLAYER_HEAD, title, lore);
        var profile = profiles.get(owner);
        if (profile == null) {
            var online = Bukkit.getPlayer(owner);
            if (online != null) {
                var current = online.getPlayerProfile();
                if (!current.getTextures().isEmpty()) {
                    profile = Bukkit.createPlayerProfile(owner, name);
                    profile.setTextures(current.getTextures());
                    profiles.put(owner, profile);
                }
            }
        }
        if (profile != null) {
            var meta = (SkullMeta) item.getItemMeta();
            meta.setOwnerProfile(profile);
            item.setItemMeta(meta);
        } else if (owner.version() != 3 && profileRequests.add(owner)) {
            // Les UUID hors ligne v3 n'existent pas dans l'annuaire Mojang. Ne pas les y
            // rechercher.
            String event = state == null ? null : state.id();
            Bukkit.createPlayerProfile(owner, name)
                    .update()
                    .whenComplete(
                            (loaded, error) ->
                                    sync(
                                            () -> {
                                                if (error != null
                                                        || loaded == null
                                                        || loaded.getTextures().isEmpty()
                                                        || state == null
                                                        || !state.id().equals(event)) return;
                                                profiles.put(owner, loaded);
                                                for (View view : new ArrayList<>(views.values())) {
                                                    Player p = Bukkit.getPlayer(view.owner);
                                                    if (p != null
                                                            && p.getOpenInventory()
                                                                            .getTopInventory()
                                                                    == view.inventory) render(view);
                                                }
                                            }));
        }
        return item;
    }

    private void render(View v) {
        if (state == null || !state.id().equals(v.event)) return;
        for (int slot = 0; slot < 54; slot++)
            v.inventory.setItem(slot, item(Material.BLACK_STAINED_GLASS_PANE, " "));
        if (!state.phase().equals("ACTIVE")) return;
        int maxPage = (prices.size() - 1) / BidPrices.PAGE_SIZE;
        v.page = Math.max(0, Math.min(maxPage, v.page));
        for (int i = 0; i < BidPrices.PAGE_SIZE; i++) {
            int index = v.page * BidPrices.PAGE_SIZE + i;
            if (index >= prices.size()) break;
            long price = prices.get(index);
            if (price < state.price()) continue;
            String label = Numbers.compact(price) + " Écus";
            ItemStack button =
                    price == state.price() && state.bidder() != null
                            ? playerHead(
                                    state.bidder(),
                                    state.name(),
                                    "&e" + state.name() + " &7— &6" + label,
                                    "&7Meilleure offre actuelle.")
                            : item(
                                    price <= v.balance
                                            ? Material.GOLD_INGOT
                                            : Material.RED_STAINED_GLASS_PANE,
                                    (price <= v.balance ? "&e" : "&c") + label,
                                    "&7Montant exact : &f"
                                            + String.format(Locale.FRANCE, "%,d", price)
                                            + " Écus",
                                    price <= v.balance
                                            ? "&aClic : miser ce montant."
                                            : "&cSolde insuffisant.",
                                    "&8Offre ferme, paiement à l’attribution.");
            v.inventory.setItem(BidPrices.slot(i), button);
        }
        if (v.page < maxPage)
            v.inventory.setItem(
                    53,
                    item(
                            Material.ARROW,
                            "&aPage suivante",
                            "&7Page " + (v.page + 1) + "/" + (maxPage + 1)));
    }

    private void offer(Player p, View view, long amount) {
        if (state == null
                || !state.id().equals(view.event)
                || !state.phase().equals("ACTIVE")
                || !room.present.contains(p.getUniqueId())
                || !room.inside(p)
                || changing
                || isolated(p)) return;
        if (amount <= state.price()) return;
        if (amount > view.balance) {
            p.sendMessage(
                    "§cSolde affiché insuffisant. Actualisation en cours ; réessaie après la mise à"
                            + " jour.");
            refreshBalance(p, view);
            return;
        }
        UUID owner = p.getUniqueId();
        if (!pending.add(owner)) return;
        store.bid(state.id(), state.lot(), owner, p.getName(), amount, bidTime)
                .whenComplete(
                        (result, error) ->
                                sync(
                                        () -> {
                                            pending.remove(owner);
                                            if (error != null) {
                                                log("Offre d’enchère non validée", error);
                                                if (PlayerSessions.isCurrent(p))
                                                    p.sendMessage(
                                                            "Offre non validée. Réessaie dans un"
                                                                    + " instant.");
                                                return;
                                            }
                                            apply(result.state());
                                            if (result.result() == LiveAuctionStore.Bid.ACCEPTED) {
                                                String message =
                                                        text("offre")
                                                                .replace("{joueur}", p.getName())
                                                                .replace(
                                                                        "{prix}",
                                                                        Numbers.compact(amount))
                                                                .replace(
                                                                        "{secondes}",
                                                                        Long.toString(
                                                                                bidTime / 1000));
                                                // Retour privé aux participants : ne pas saturer
                                                // tout le serveur à chaque offre.
                                                for (View v : views.values()) {
                                                    var participant = Bukkit.getPlayer(v.owner);
                                                    if (participant != null)
                                                        participant.sendMessage(
                                                                DailySettings.color(message));
                                                }
                                            } else if (PlayerSessions.isCurrent(p))
                                                p.sendMessage(
                                                        switch (result.result()) {
                                                            case FUNDS ->
                                                                    "Écus insuffisants : offre"
                                                                            + " refusée.";
                                                            case TOO_LOW ->
                                                                    "Ce montant a déjà été"
                                                                            + " dépassé.";
                                                            case NOT_REGISTERED ->
                                                                    "Tu n’es pas inscrit à cette"
                                                                            + " enchère.";
                                                            default ->
                                                                    "Ce lot est fermé. Choisis le"
                                                                            + " lot actuel.";
                                                        });
                                            if (PlayerSessions.isCurrent(p)
                                                    && views.get(owner) == view)
                                                refreshBalance(p, view);
                                        }));
    }

    private void leave(Player p) {
        joining.remove(p.getUniqueId());
        View view = views.remove(p.getUniqueId());
        if (view != null && p.getOpenInventory().getTopInventory() == view.inventory)
            p.closeInventory();
        boolean wasPresent = room.present.contains(p.getUniqueId());
        room.remove(p, wasPresent);
        p.sendActionBar(Component.empty());
        if (state != null)
            store.leaveRegistration(state.id(), p.getUniqueId())
                    .whenComplete(
                            (v, e) -> {
                                if (e != null) log("Désinscription impossible", e);
                            });
        p.sendMessage(
                "§7Tu as quitté l’enchère. Les frais d’entrée ne sont pas remboursés et tes offres"
                    + " déjà déposées restent fermes.");
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof View v)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)
                || !p.getUniqueId().equals(v.owner)
                || e.getClickedInventory() != v.inventory
                || (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT)) return;
        int slot = e.getRawSlot();
        LiveAuctionStore.State clickedState = state;
        int clickedPage = v.page;
        ServerTasks.run(
                plugin,
                () -> {
                    if (stopped
                            || !PlayerSessions.isCurrent(p)
                            || p.getOpenInventory().getTopInventory() != v.inventory) return;
                    if (state == null
                            || clickedState == null
                            || !state.id().equals(v.event)
                            || !state.id().equals(clickedState.id())
                            || state.lot() != clickedState.lot()
                            || !state.phase().equals(clickedState.phase())
                            || v.page != clickedPage
                            || !state.phase().equals("ACTIVE")) return;
                    if (slot == 53) {
                        int page = Math.min((prices.size() - 1) / BidPrices.PAGE_SIZE, v.page + 1);
                        if (page != v.page) replaceView(p, v, page, false);
                        return;
                    }
                    int offset = BidPrices.indexAt(slot);
                    if (offset < 0) return;
                    int index = clickedPage * BidPrices.PAGE_SIZE + offset;
                    if (index < prices.size()) offer(p, v, prices.get(index));
                });
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof View v) views.remove(v.owner, v);
    }

    @EventHandler
    public void connected(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!ready || state == null || state.phase().equals("DONE") || !room.inside(player)) return;
        String id = state.id();
        store.member(id, player.getUniqueId())
                .whenComplete(
                        (member, error) ->
                                sync(
                                        () -> {
                                            if (error != null) {
                                                log("Reprise de l’inscription impossible", error);
                                                return;
                                            }
                                            if (PlayerSessions.isCurrent(player)
                                                    && Boolean.TRUE.equals(member)
                                                    && state != null
                                                    && state.id().equals(id)) join(player);
                                        }));
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        room.present.remove(e.getPlayer().getUniqueId());
        joining.remove(e.getPlayer().getUniqueId());
        views.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command c, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("setsalle") || sub.equals("setpresentoir")) {
            if (!sender.hasPermission("cliker.enchere.admin") || !(sender instanceof Player p)) {
                sender.sendMessage("§cPermission administrateur et présence en jeu requises.");
                return true;
            }
            fr.cliker.clikerv2.config.YamlFiles.location(
                    config.yaml, sub.equals("setsalle") ? "salle" : "presentoir", p.getLocation());
            config.save();
            room.clear();
            sender.sendMessage("§aPosition d’enchère sauvegardée.");
            return true;
        }
        if (sub.equals("forcer") || sub.equals("demarrer")) {
            if (!sender.hasPermission("cliker.enchere.admin")) {
                sender.sendMessage("Tu n’as pas la permission.");
                return true;
            }
            if (sub.equals("forcer")) create(sender);
            else if (ready && state != null && state.phase().equals("REGISTERING") && !changing)
                advance(true);
            else sender.sendMessage("Aucune inscription en cours à terminer.");
            return true;
        }
        if (sub.equals("rejoindre")) {
            if (sender instanceof Player p) join(p);
            else sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (sub.equals("quitter") || sub.equals("quit")) {
            if (sender instanceof Player p) leave(p);
            else sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (sub.equals("info")) {
            if (!ready) sender.sendMessage("Chargement des enchères en cours.");
            else if (state == null || state.phase().equals("DONE"))
                sender.sendMessage(
                        "Aucune enchère ouverte. Prochaine inscription dans "
                                + Math.max(0, (next - System.currentTimeMillis()) / 60000)
                                + " min.");
            else
                sender.sendMessage(
                        DailySettings.color(
                                        "&6Enchère : "
                                                + (state.phase().equals("REGISTERING")
                                                        ? "inscriptions ouvertes"
                                                        : "lot " + (state.lot() + 1) + "/3")
                                                + " &7— "
                                                + remaining()
                                                + "s &a/enchere rejoindre")
                                .clickEvent(ClickEvent.runCommand("/enchere rejoindre")));
            return true;
        }
        sender.sendMessage(
                "/enchere [rejoindre|quitter]"
                        + (sender.hasPermission("cliker.enchere.admin")
                                ? " · /enchere forcer · /enchere demarrer"
                                : ""));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] args) {
        if (args.length != 1) return List.of();
        var choices = new ArrayList<>(List.of("rejoindre", "quitter", "quit"));
        if (s.hasPermission("cliker.enchere.admin"))
            choices.addAll(List.of("forcer", "demarrer", "setsalle", "setpresentoir"));
        return choices.stream()
                .filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
    }

    private String text(String key) {
        return config.yaml.getString("messages." + key, key);
    }

    private void sync(Runnable r) {
        if (!stopped)
            ServerTasks.run(
                    plugin,
                    () -> {
                        if (!stopped) r.run();
                    });
    }

    private void log(String message, Throwable error) {
        plugin.getLogger().log(Level.SEVERE, message, error);
    }

    public void shutdown() {
        stopped = true;
        room.shutdown();
        if (task != null) task.cancel();
        for (View v : new ArrayList<>(views.values())) {
            var p = Bukkit.getPlayer(v.owner);
            if (p != null && p.getOpenInventory().getTopInventory() == v.inventory)
                p.closeInventory();
        }
        views.clear();
        profiles.clear();
        profileRequests.clear();
        joining.clear();
        HandlerList.unregisterAll(this);
    }
}
