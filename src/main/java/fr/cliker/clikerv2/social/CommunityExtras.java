package fr.cliker.clikerv2.social;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.features.*;
import fr.cliker.clikerv2.hunting.*;
import fr.cliker.clikerv2.rewards.ClaimService;
import fr.cliker.clikerv2.runtime.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Tags, couleurs et accueil : aucun SQL dans le rendu asynchrone du chat. */
public final class CommunityExtras implements Listener, CommandExecutor, TabCompleter {
    public record Style(Component tag, NamedTextColor color, int level) {}

    public static final Style DEFAULT = new Style(Component.empty(), NamedTextColor.WHITE, 0);

    private record Category(
            String id,
            String name,
            Material icon,
            String emoji,
            String unit,
            List<Long> thresholds,
            List<String> colors) {}

    private final JavaPlugin plugin;
    private final CommunityStore store;
    private final RewardLedger ledger;
    private final ClaimService claims;
    private final Menus menus;
    private final fr.cliker.clikerv2.ui.ViewRequests requests;
    private final Settings tags, welcome, chat;
    private final List<Category> categories = new ArrayList<>();
    private final Map<UUID, CommunityStore.Profile> profiles = new HashMap<>();
    private final Map<UUID, Style> styles = new ConcurrentHashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private CommunityStore.Welcome newcomer;
    private BukkitTask task;
    private boolean ready, refreshing, stopped;
    private long tick;

    public CommunityExtras(
            JavaPlugin plugin, DatabaseManager db, RewardLedger ledger, ClaimService claims) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.claims = claims;
        store = new CommunityStore(db, ledger);
        menus = new Menus(plugin);
        requests = new fr.cliker.clikerv2.ui.ViewRequests(plugin);
        tags = new Settings(plugin, "communaute/tags.yml");
        welcome = new Settings(plugin, "communaute/bienvenue.yml");
        chat = new Settings(plugin, "communaute/tchat.yml");
        var root = Objects.requireNonNull(tags.yaml.getConfigurationSection("categories"));
        for (String id : root.getKeys(false)) {
            var section = root.getConfigurationSection(id);
            var thresholds = section.getLongList("seuils");
            var colors = section.getStringList("couleurs");
            if (!id.matches("[a-z_]+") || thresholds.size() != 15 || colors.size() != 15)
                throw new IllegalArgumentException("Chaque catégorie exige 15 grades : " + id);
            long last = -1;
            for (long value : thresholds) {
                if (value <= last)
                    throw new IllegalArgumentException("Seuils de tags non croissants");
                last = value;
            }
            for (String color : colors)
                if (NamedTextColor.NAMES.value(color) == null)
                    throw new IllegalArgumentException("Couleur inconnue : " + color);
            categories.add(
                    new Category(
                            id,
                            section.getString("nom", id),
                            Material.valueOf(section.getString("icone", "PAPER")),
                            section.getString("emoji", "★"),
                            section.getString("unite", "points"),
                            List.copyOf(thresholds),
                            List.copyOf(colors)));
        }
        if (categories.size() > 21)
            throw new IllegalArgumentException("Trop de catégories de tags");
        if (welcome.yaml.getLong("duree-secondes", 300) < 1
                || welcome.yaml.getLong("duree-secondes", 300) > 300)
            throw new IllegalArgumentException("Accueil limité à cinq minutes");
        if (welcome.yaml.getLong("recompenses.ecus", 250) < 0
                || welcome.yaml.getLong("recompenses.xp", 50) < 0)
            throw new IllegalArgumentException("Gain d’accueil négatif");
    }

    private static Component color(String s) {
        return fr.cliker.clikerv2.daily.DailySettings.color(s);
    }

    private void sync(Runnable action) {
        if (!stopped)
            ServerTasks.run(
                    plugin,
                    () -> {
                        if (!stopped) action.run();
                    });
    }

    private void error(Throwable e) {
        plugin.getLogger()
                .log(java.util.logging.Level.SEVERE, "Communauté : action non validée", e);
    }

    public boolean welcomeEnabled() {
        return ready && !stopped && welcome.yaml.getBoolean("actif", true);
    }

    public void start() {
        for (String root : List.of("tag", "chatcouleur", "bvn")) {
            plugin.getCommand(root).setExecutor(this);
            plugin.getCommand(root).setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        store.initialize()
                .whenComplete(
                        (last, e) ->
                                sync(
                                        () -> {
                                            if (e != null) {
                                                error(e);
                                                return;
                                            }
                                            newcomer = last;
                                            ready = true;
                                            refresh();
                                        }));
        task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    tick++;
                                    for (Player p : Bukkit.getOnlinePlayers()) updateStyle(p);
                                    if (tick
                                                    % Math.max(
                                                            1,
                                                            tags.yaml.getLong(
                                                                            "actualisation-secondes",
                                                                            30)
                                                                    / 5)
                                            == 0) refresh();
                                },
                                100,
                                100);
    }

    private void refresh() {
        if (!ready || refreshing) return;
        refreshing = true;
        var players = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        store.profiles(players)
                .whenComplete(
                        (loaded, e) ->
                                sync(
                                        () -> {
                                            refreshing = false;
                                            if (e != null) {
                                                error(e);
                                                return;
                                            }
                                            for (var entry : loaded.entrySet()) {
                                                Player p = Bukkit.getPlayer(entry.getKey());
                                                if (p != null) {
                                                    profiles.put(entry.getKey(), entry.getValue());
                                                    updateStyle(p);
                                                }
                                            }
                                        }));
    }

    private long amount(Player p, CommunityStore.Profile profile, Category c) {
        return switch (c.id()) {
            case "bugs" -> profile.bugs();
            case "mobs" -> profile.kills();
            case "boss" -> profile.bosses();
            case "jeu" -> Math.max(0, p.getStatistic(Statistic.PLAY_ONE_MINUTE)) / 20;
            case "popularite" -> profile.popularity();
            case "prestige" -> profile.prestige();
            default -> 0;
        };
    }

    private String permission(Category category, int tier) {
        return "cliker.tag." + category.id() + "." + (tier + 1);
    }

    private Component badge(Player p, CommunityStore.Profile profile) {
        for (Category c : categories)
            for (int tier = 0; tier < 15; tier++)
                if (profile.tag().equals(c.id() + "_" + (tier + 1))
                        && p.hasPermission(permission(c, tier))
                        && amount(p, profile, c) >= c.thresholds().get(tier)) {
                    String label =
                            c.emoji()
                                    + (c.id().equals("bugs")
                                            ? Long.toString(profile.bugs())
                                            : fr.cliker.clikerv2.prestige.PrestigeType.toRoman(
                                                    tier + 1));
                    return Component.text(
                                    " " + label, NamedTextColor.NAMES.value(c.colors().get(tier)))
                            .hoverEvent(
                                    color(
                                            "&f"
                                                    + c.name()
                                                    + " &7— grade "
                                                    + (tier + 1)
                                                    + "/15\n&7Progression : &f"
                                                    + amount(p, profile, c)
                                                    + " "
                                                    + c.unit()));
                }
        return Component.empty();
    }

    private void updateStyle(Player p) {
        var profile = profiles.get(p.getUniqueId());
        if (profile == null) return;
        String selected = profile.color();
        NamedTextColor chosen = NamedTextColor.NAMES.value(selected);
        if (chosen == null
                || (!selected.equals("white")
                        && !p.hasPermission("cliker.chat.couleur." + selected))) {
            String fallback = chat.yaml.getString("couleur-de-secours", "white");
            chosen = NamedTextColor.NAMES.value(fallback);
            if (chosen == null
                    || !fallback.equals("white")
                            && !p.hasPermission("cliker.chat.couleur." + fallback))
                chosen = NamedTextColor.WHITE;
        }
        styles.put(p.getUniqueId(), new Style(badge(p, profile), chosen, p.getLevel()));
    }

    public Style style(UUID owner) {
        return styles.getOrDefault(owner, DEFAULT);
    }

    private void load(Player p, Runnable complete) {
        if (!ready || !pending.add(p.getUniqueId())) {
            p.sendMessage(color("&eChargement…"));
            return;
        }
        store.profiles(List.of(p.getUniqueId()))
                .whenComplete(
                        (loaded, e) ->
                                sync(
                                        () -> {
                                            pending.remove(p.getUniqueId());
                                            if (!PlayerSessions.isCurrent(p)) return;
                                            if (e != null) {
                                                error(e);
                                                p.sendMessage(color("&cChargement indisponible."));
                                                return;
                                            }
                                            profiles.put(
                                                    p.getUniqueId(), loaded.get(p.getUniqueId()));
                                            updateStyle(p);
                                            complete.run();
                                        }));
    }

    private void categories(Player p) {
        var view = menus.create(p, 27, "Tags — Catégories");
        for (int i = 0; i < categories.size(); i++) {
            Category c = categories.get(i);
            view.put(
                    i,
                    Menus.item(
                            c.icon(),
                            "&b" + c.name(),
                            "&715 grades à collectionner.",
                            "&7Progression et permission nécessaires.",
                            "&aClic : ouvrir cette catégorie."),
                    (who, right) -> category(who, c));
        }
        view.put(
                22,
                Menus.item(Material.BARRIER, "&cRetirer mon tag"),
                (who, r) -> save(who, "", null, () -> categories(who)));
        view.show(p);
    }

    private void category(Player p, Category c) {
        var profile = profiles.get(p.getUniqueId());
        if (profile == null) return;
        var view = menus.create(p, 27, "Tags — " + c.name());
        long current = amount(p, profile, c);
        for (int tier = 0; tier < 15; tier++) {
            int index = tier;
            String id = c.id() + "_" + (tier + 1);
            boolean earned = current >= c.thresholds().get(tier),
                    allowed = p.hasPermission(permission(c, tier));
            String state =
                    profile.tag().equals(id)
                            ? "&aÉquipé"
                            : earned && allowed ? "&aDisponible" : "&cVerrouillé";
            view.put(
                    tier,
                    Menus.item(
                            earned && allowed ? c.icon() : Material.GRAY_DYE,
                            "&"
                                    + legacyColor(c.colors().get(tier))
                                    + c.emoji()
                                    + " "
                                    + c.name()
                                    + " "
                                    + fr.cliker.clikerv2.prestige.PrestigeType.toRoman(tier + 1),
                            state,
                            "&7Objectif : &f" + c.thresholds().get(tier) + " " + c.unit(),
                            "&7Progression : &f" + current,
                            "&7Permission : " + (allowed ? "&aaccordée" : "&cmanquante"),
                            "&8" + permission(c, tier),
                            earned && allowed
                                    ? "&eClic : équiper ce tag."
                                    : "&7Remplis les deux conditions pour l’équiper."),
                    (who, r) -> {
                        var fresh = profiles.get(who.getUniqueId());
                        if (fresh == null
                                || !who.hasPermission(permission(c, index))
                                || amount(who, fresh, c) < c.thresholds().get(index)) {
                            who.sendMessage(color("&cProgression ou permission manquante."));
                            return;
                        }
                        save(who, id, null, () -> category(who, c));
                    });
        }
        view.put(
                22,
                Menus.item(Material.ARROW, "&aRetour aux catégories"),
                (who, r) -> categories(who));
        view.show(p);
    }

    private static String colorName(String key) {
        return switch (key) {
            case "aqua" -> "Cyan";
            case "black" -> "Noir";
            case "blue" -> "Bleu";
            case "dark_aqua" -> "Cyan foncé";
            case "dark_blue" -> "Bleu foncé";
            case "dark_gray" -> "Gris foncé";
            case "dark_green" -> "Vert foncé";
            case "dark_purple" -> "Violet";
            case "dark_red" -> "Rouge foncé";
            case "gold" -> "Doré";
            case "gray" -> "Gris";
            case "green" -> "Vert";
            case "light_purple" -> "Rose";
            case "red" -> "Rouge";
            case "yellow" -> "Jaune";
            default -> "Blanc";
        };
    }

    private static String legacyColor(String key) {
        return switch (key) {
            case "black" -> "0";
            case "dark_blue" -> "1";
            case "dark_green" -> "2";
            case "dark_aqua" -> "3";
            case "dark_red" -> "4";
            case "dark_purple" -> "5";
            case "gold" -> "6";
            case "gray" -> "7";
            case "dark_gray" -> "8";
            case "blue" -> "9";
            case "green" -> "a";
            case "aqua" -> "b";
            case "red" -> "c";
            case "light_purple" -> "d";
            case "yellow" -> "e";
            default -> "f";
        };
    }

    private void colors(Player p) {
        var view = menus.create(p, 27, "Couleur de tes messages");
        int slot = 0;
        for (String key : NamedTextColor.NAMES.keys().stream().sorted().toList()) {
            boolean allowed = key.equals("white") || p.hasPermission("cliker.chat.couleur." + key);
            view.put(
                    slot++,
                    Menus.item(
                            allowed ? Material.LIME_DYE : Material.GRAY_DYE,
                            "&" + legacyColor(key) + colorName(key),
                            allowed
                                    ? "&aClic : utiliser cette couleur."
                                    : "&cPermission manquante.",
                            "&8cliker.chat.couleur." + key),
                    (who, r) -> {
                        if (!key.equals("white")
                                && !who.hasPermission("cliker.chat.couleur." + key)) {
                            who.sendMessage(color("&cTu n’as pas cette couleur."));
                            return;
                        }
                        save(who, null, key, () -> colors(who));
                    });
        }
        view.show(p);
    }

    private void save(Player p, String tag, String chosen, Runnable done) {
        if (!pending.add(p.getUniqueId())) return;
        var request = requests.begin(p);
        store.preference(p.getUniqueId(), tag, chosen)
                .whenComplete(
                        (v, e) ->
                                sync(
                                        () -> {
                                            pending.remove(p.getUniqueId());
                                            if (e != null) {
                                                error(e);
                                                return;
                                            }
                                            if (PlayerSessions.isCurrent(p))
                                                load(
                                                        p,
                                                        () -> {
                                                            p.sendMessage(
                                                                    color("&aChoix sauvegardé."));
                                                            if (requests.complete(p, request))
                                                                done.run();
                                                        });
                                        }));
    }

    private void welcome(Player p) {
        var target = newcomer;
        if (!ready
                || target == null
                || !welcome.yaml.getBoolean("actif", true)
                || System.currentTimeMillis() - target.at()
                        > welcome.yaml.getLong("duree-secondes", 300) * 1000) {
            p.sendMessage(color("&7Aucun nouveau joueur à accueillir pour le moment."));
            return;
        }
        if (target.owner().equals(p.getUniqueId())) {
            p.sendMessage(color("&7Tu ne peux pas te souhaiter la bienvenue."));
            return;
        }
        if (!pending.add(p.getUniqueId())) return;
        long coins = welcome.yaml.getLong("recompenses.ecus", 250),
                xp = welcome.yaml.getLong("recompenses.xp", 50);
        store.welcome(
                        p.getUniqueId(),
                        p.getName(),
                        target.owner(),
                        System.currentTimeMillis(),
                        welcome.yaml.getLong("duree-secondes", 300) * 1000,
                        Gains.of(coins, 0, 0, xp))
                .whenComplete(
                        (result, e) ->
                                sync(
                                        () -> {
                                            pending.remove(p.getUniqueId());
                                            if (e != null) {
                                                error(e);
                                                return;
                                            }
                                            if (result != 2) {
                                                if (PlayerSessions.isCurrent(p))
                                                    p.sendMessage(
                                                            color(
                                                                    result == 1
                                                                            ? "&7Tu as déjà"
                                                                                  + " accueilli ce"
                                                                                  + " joueur."
                                                                            : "&7Ce délai d’accueil"
                                                                                  + " est terminé."));
                                                return;
                                            }
                                            Bukkit.broadcast(
                                                    color(
                                                            welcome.yaml
                                                                    .getString(
                                                                            "messages.souhait",
                                                                            "&a{joueur} souhaite la"
                                                                                + " bienvenue à"
                                                                                + " {nouveau} !")
                                                                    .replace(
                                                                            "{joueur}", p.getName())
                                                                    .replace(
                                                                            "{nouveau}",
                                                                            target.name())));
                                            Player online = Bukkit.getPlayer(p.getUniqueId());
                                            if (online != null)
                                                online.sendMessage(
                                                        color(
                                                                welcome.yaml
                                                                        .getString(
                                                                                "messages.gagne",
                                                                                "&aMerci !"
                                                                                    + " &e+{ecus}"
                                                                                    + " Écus"
                                                                                    + " &b+{xp} XP")
                                                                        .replace(
                                                                                "{ecus}",
                                                                                Long.toString(
                                                                                        coins))
                                                                        .replace(
                                                                                "{xp}",
                                                                                Long.toString(
                                                                                        xp))));
                                            ledger.flushXP(p.getUniqueId());
                                        }));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String root = command.getName();
        if (root.equals("tag") && args.length == 3 && args[0].equalsIgnoreCase("bugs")) {
            if (!sender.hasPermission("cliker.tag.admin")) {
                sender.sendMessage(color("&cPermission administrateur requise."));
                return true;
            }
            long count;
            try {
                count = Long.parseLong(args[2]);
                if (count < 0 || count > 1000000000) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(color("&cCompteur de bugs invalide."));
                return true;
            }
            claims.resolve(args[1])
                    .thenCompose(
                            owner -> {
                                if (owner == null)
                                    throw new IllegalArgumentException("Joueur inconnu");
                                return store.bugs(owner, count);
                            })
                    .whenComplete(
                            (v, e) ->
                                    sync(
                                            () -> {
                                                if (e != null) {
                                                    sender.sendMessage(
                                                            color(
                                                                    "&cJoueur inconnu ou sauvegarde"
                                                                            + " indisponible."));
                                                    return;
                                                }
                                                sender.sendMessage(
                                                        color(
                                                                "&aCompteur de bugs validés"
                                                                        + " sauvegardé."));
                                                refresh();
                                            }));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        String permission =
                root.equals("tag")
                        ? "cliker.tag.use"
                        : root.equals("bvn") ? "cliker.bienvenue.use" : "cliker.chatcouleur.use";
        if (!p.hasPermission(permission)) {
            p.sendMessage(color("&cTu n’as pas la permission."));
            return true;
        }
        if (args.length != 0) {
            p.sendMessage(
                    color(
                            "&e/"
                                    + root
                                    + (root.equals("tag") && p.hasPermission("cliker.tag.admin")
                                            ? " &7ou /tag bugs <joueur> <nombre validé>"
                                            : "")));
            return true;
        }
        if (root.equals("bvn")) welcome(p);
        else {
            var request = requests.begin(p);
            load(
                    p,
                    () -> {
                        if (!requests.complete(p, request) || !p.hasPermission(permission)) return;
                        if (root.equals("tag")) categories(p);
                        else colors(p);
                    });
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!ready) return;
        load(p, () -> {});
        if (p.hasPlayedBefore() || !welcome.yaml.getBoolean("actif", true)) return;
        var candidate =
                new CommunityStore.Welcome(
                        p.getUniqueId(), p.getName(), System.currentTimeMillis());
        store.newcomer(candidate)
                .whenComplete(
                        (v, error) ->
                                sync(
                                        () -> {
                                            if (error != null) {
                                                error(error);
                                                return;
                                            }
                                            if (newcomer == null || candidate.at() >= newcomer.at())
                                                newcomer = candidate;
                                            Bukkit.broadcast(
                                                    color(
                                                                    welcome.yaml
                                                                            .getString(
                                                                                    "messages.nouveau",
                                                                                    "&aBienvenue à"
                                                                                        + " {joueur}"
                                                                                        + " ! &e/bvn")
                                                                            .replace(
                                                                                    "{joueur}",
                                                                                    p.getName()))
                                                            .clickEvent(
                                                                    net.kyori.adventure.text.event
                                                                            .ClickEvent.runCommand(
                                                                            "/bvn")));
                                        }));
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        profiles.remove(e.getPlayer().getUniqueId());
        styles.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command c, String label, String[] a) {
        if (!c.getName().equals("tag") || !sender.hasPermission("cliker.tag.admin"))
            return List.of();
        if (a.length == 1)
            return "bugs".startsWith(a[0].toLowerCase(Locale.ROOT)) ? List.of("bugs") : List.of();
        if (a.length == 2)
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(
                            n ->
                                    n.toLowerCase(Locale.ROOT)
                                            .startsWith(a[1].toLowerCase(Locale.ROOT)))
                    .toList();
        return List.of();
    }

    public void shutdown() {
        stopped = true;
        if (task != null) task.cancel();
        HandlerList.unregisterAll(this);
        styles.clear();
        profiles.clear();
        pending.clear();
    }
}
