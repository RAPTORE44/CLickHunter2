package fr.cliker.clikerv2.achievements;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.buyzone.BuyZoneModule;
import fr.cliker.clikerv2.community.ToastNotifications;
import fr.cliker.clikerv2.features.Menus;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.runtime.PlayerSessions;
import fr.cliker.clikerv2.runtime.ServerTasks;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** 500 succès honorifiques ; un passage espacé par joueur, aucune requête par coup. */
public final class AchievementModule implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final AchievementStore store;
    private final BuyZoneModule zones;
    private final Settings settings;
    private final Menus menus;
    private final List<AchievementCatalog.Entry> catalog;
    private final Map<UUID, AchievementStore.Progress> cache = new HashMap<>();
    private final Map<UUID, Long> next = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private final Map<UUID, Integer> opening = new HashMap<>();
    private final Map<UUID, Integer> observedLevels = new HashMap<>();
    private final NamespacedKey clicks;
    private BukkitTask task;
    private boolean stopped;
    private final long interval;

    public AchievementModule(JavaPlugin plugin, DatabaseManager db, BuyZoneModule zones) {
        this.plugin = plugin;
        this.zones = zones;
        settings = new Settings(plugin, "progression/succes.yml");
        catalog = AchievementCatalog.create(settings.yaml);
        store = new AchievementStore(db);
        menus = new Menus(plugin);
        clicks = new NamespacedKey(plugin, "farm_clicks");
        interval =
                Math.max(30, Math.min(3600, settings.yaml.getLong("intervalle-secondes", 60)))
                        * 1000;
    }

    public void start() {
        var command = Objects.requireNonNull(plugin.getCommand("succes"));
        command.setExecutor(this);
        command.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getOnlinePlayers().forEach(p -> next.put(p.getUniqueId(), 0L));
        // Au maximum un joueur soumis par tick de cette tâche, jamais un lot global.
        task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    long now = System.currentTimeMillis();
                                    for (var p : Bukkit.getOnlinePlayers())
                                        if (now >= next.getOrDefault(p.getUniqueId(), 0L)
                                                && !pending.contains(p.getUniqueId())) {
                                            refresh(p, false);
                                            break;
                                        }
                                },
                                20,
                                5);
    }

    private Map<String, Long> live(Player p) {
        Map<String, Long> values = new HashMap<>();
        values.put(
                "clicks",
                Math.max(
                        0,
                        p.getPersistentDataContainer()
                                .getOrDefault(clicks, PersistentDataType.LONG, 0L)));
        values.put("minutes", p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200L);
        values.put(
                "level",
                (long) Math.max(p.getLevel(), observedLevels.getOrDefault(p.getUniqueId(), 0)));
        long count = 0;
        if (zones != null)
            for (var tier : MobTier.values()) if (zones.canTravel(p, tier.id())) count++;
        values.put("zones", count);
        return values;
    }

    private void refresh(Player p, boolean open) {
        UUID owner = p.getUniqueId();
        if (open) opening.putIfAbsent(owner, -1);
        if (!pending.add(owner)) return;
        next.put(owner, System.currentTimeMillis() + interval);
        store.refresh(owner, live(p), catalog)
                .whenComplete(
                        (progress, error) ->
                                ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (stopped) return;
                                            pending.remove(owner);
                                            Integer wanted = opening.remove(owner);
                                            if (!PlayerSessions.isCurrent(p)) return;
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.WARNING,
                                                                "Lecture des succès impossible pour"
                                                                        + " "
                                                                        + owner,
                                                                error);
                                                if (wanted != null)
                                                    p.sendMessage(
                                                            "§cLes succès sont temporairement"
                                                                    + " indisponibles. Réessaie"
                                                                    + " bientôt.");
                                                return;
                                            }
                                            cache.put(owner, progress);
                                            if (!progress.added().isEmpty()
                                                    && settings.yaml.getBoolean(
                                                            "notifications", true)) {
                                                String title =
                                                        progress.added().size() == 1
                                                                ? catalog.stream()
                                                                        .filter(
                                                                                e ->
                                                                                        e.id().equals(
                                                                                                        progress.added()
                                                                                                                .getFirst()))
                                                                        .findFirst()
                                                                        .orElseThrow()
                                                                        .title()
                                                                : progress.added().size()
                                                                        + " succès débloqués !";
                                                ToastNotifications.show(
                                                        p,
                                                        Material.NETHER_STAR,
                                                        title,
                                                        "Collection : "
                                                                + progress.unlocked().size()
                                                                + "/500 — /succes");
                                            }
                                            if (wanted != null) {
                                                if (wanted < 0) categories(p, 0);
                                                else page(p, null, wanted, 0);
                                            }
                                        }));
    }

    private long completed(
            AchievementStore.Progress progress, List<AchievementCatalog.Entry> entries) {
        return entries.stream().filter(e -> progress.unlocked().containsKey(e.id())).count();
    }

    private void header(Menus.View view, AchievementStore.Progress progress) {
        view.put(
                4,
                Menus.item(
                        Material.NETHER_STAR,
                        "&6&lMES SUCCÈS",
                        "&a" + progress.unlocked().size() + " &7/ &f500 débloqués",
                        "&7Objectifs honorifiques, enregistrés définitivement.",
                        "&7Aucune récompense à retirer dans ce menu."),
                null);
    }

    private void categories(Player p, int filter) {
        var progress = cache.get(p.getUniqueId());
        if (progress == null) return;
        var view = menus.create(p, 54, "Succès • Catégories");
        header(view, progress);
        var groups = catalog.stream().map(AchievementCatalog.Entry::category).distinct().toList();
        for (int i = 0; i < groups.size(); i++) {
            String category = groups.get(i);
            var entries = catalog.stream().filter(e -> e.category().equals(category)).toList();
            long n = completed(progress, entries);
            int slot = 10 + (i / 7) * 9 + i % 7;
            view.put(
                    slot,
                    Menus.item(
                            entries.getFirst().icon(),
                            "&e" + category,
                            "&a" + n + " &7/ &f" + entries.size() + " débloqués",
                            "&eClique pour parcourir les objectifs."),
                    (who, r) -> page(who, category, filter, 0));
        }
        view.put(
                40,
                Menus.item(
                        Material.BOOK,
                        "&bTous les succès",
                        "&7500 objectifs répartis entre tes activités."),
                (who, r) -> page(who, null, filter, 0));
        view.put(
                49,
                Menus.item(Material.ARROW, "&eRetour au menu"),
                (who, r) -> who.performCommand("menu"));
        view.put(
                51,
                Menus.item(
                        Material.CLOCK,
                        "&bActualiser",
                        "&7Mise à jour automatique espacée pour les performances."),
                (who, r) -> {
                    if (System.currentTimeMillis() < next.getOrDefault(who.getUniqueId(), 0L))
                        categories(who, filter);
                    else refresh(who, true);
                });
        view.show(p);
    }

    private void page(Player p, String category, int filter, int requested) {
        var progress = cache.get(p.getUniqueId());
        if (progress == null) return;
        var entries =
                catalog.stream()
                        .filter(e -> category == null || e.category().equals(category))
                        .filter(
                                e ->
                                        filter == 0
                                                || (filter == 1)
                                                        == progress.unlocked().containsKey(e.id()))
                        .toList();
        int pages = Math.max(1, (entries.size() + 27) / 28),
                page = Math.max(0, Math.min(requested, pages - 1));
        var view = menus.create(p, 54, "Succès • " + (page + 1) + "/" + pages);
        header(view, progress);
        for (int i = 0; i < 28 && page * 28 + i < entries.size(); i++) {
            var e = entries.get(page * 28 + i);
            boolean done = progress.unlocked().containsKey(e.id());
            long value = progress.values().getOrDefault(e.metric(), 0L);
            boolean hidden =
                    e.tier() != null
                            && progress.values().getOrDefault("mob_" + e.tier().id(), 0L) == 0
                            && !done
                            && value == 0;
            String title =
                    hidden ? "Zone " + (e.tier().ordinal() + 1) + " • À découvrir" : e.title();
            String description =
                    hidden ? "Découvre cette créature pour révéler son objectif." : e.description();
            List<String> lore =
                    new ArrayList<>(
                            List.of(
                                    "&8────────────────────",
                                    "&7" + description,
                                    "",
                                    done ? "&a✓ Débloqué définitivement" : "&e⌛ En progression",
                                    "&b"
                                            + Numbers.compact(Math.min(value, e.target()))
                                            + " &7/ &f"
                                            + Numbers.compact(e.target()),
                                    "&7"
                                            + Math.min(
                                                    100,
                                                    (int)
                                                            (Math.min(value, e.target())
                                                                    * 100.0
                                                                    / e.target()))
                                            + " %"));
            if (done)
                lore.add(
                        "&7Obtenu le "
                                + java.time.Instant.ofEpochMilli(progress.unlocked().get(e.id()))
                                        .atZone(java.time.ZoneId.of("Europe/Paris"))
                                        .format(
                                                java.time.format.DateTimeFormatter.ofPattern(
                                                        "dd/MM/yyyy")));
            view.put(
                    10 + (i / 7) * 9 + i % 7,
                    Menus.item(
                            hidden ? Material.GRAY_DYE : done ? e.icon() : Material.GRAY_DYE,
                            (done ? "&a" : "&e") + title,
                            lore.toArray(String[]::new)),
                    null);
        }
        if (page > 0)
            view.put(
                    45,
                    Menus.item(Material.ARROW, "&ePage précédente"),
                    (who, r) -> page(who, category, filter, page - 1));
        view.put(
                48,
                Menus.item(
                        Material.HOPPER,
                        "&bFiltre : " + List.of("Tous", "Débloqués", "À débloquer").get(filter),
                        "&eClique pour changer."),
                (who, r) -> page(who, category, (filter + 1) % 3, 0));
        view.put(
                49,
                Menus.item(Material.ARROW, "&eCatégories"),
                (who, r) -> categories(who, filter));
        if (page + 1 < pages)
            view.put(
                    53,
                    Menus.item(Material.ARROW, "&ePage suivante"),
                    (who, r) -> page(who, category, filter, page + 1));
        view.show(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (!p.hasPermission("cliker.succes")) {
            p.sendMessage("§cTu n’as pas la permission.");
            return true;
        }
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)) {
            p.sendMessage("§eReviens au /spawn pour consulter tes succès.");
            return true;
        }
        int filter =
                args.length == 0
                        ? 0
                        : switch (args[0].toLowerCase(Locale.ROOT)) {
                            case "tout" -> 0;
                            case "debloques" -> 1;
                            case "a-faire" -> 2;
                            default -> -1;
                        };
        if (args.length > 1 || filter < 0) {
            p.sendMessage("§e/succes [tout|debloques|a-faire]");
            return true;
        }
        if (cache.containsKey(p.getUniqueId())) {
            if (args.length == 0) categories(p, 0);
            else page(p, null, filter, 0);
        } else {
            opening.put(p.getUniqueId(), args.length == 0 ? -1 : filter);
            refresh(p, true);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] args) {
        return args.length == 1
                ? List.of("tout", "debloques", "a-faire").stream()
                        .filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT)))
                        .toList()
                : List.of();
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        next.put(e.getPlayer().getUniqueId(), 0L);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        cache.remove(id);
        next.remove(id);
        opening.remove(id);
        observedLevels.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void level(PlayerLevelChangeEvent e) {
        observedLevels.merge(
                e.getPlayer().getUniqueId(), Math.max(e.getOldLevel(), e.getNewLevel()), Math::max);
    }

    public void shutdown() {
        stopped = true;
        if (task != null) task.cancel();
        HandlerList.unregisterAll(this);
        cache.clear();
        next.clear();
        pending.clear();
        opening.clear();
        observedLevels.clear();
    }
}
