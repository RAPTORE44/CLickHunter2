package fr.cliker.clikerv2.rankings;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.hunting.HuntUI;
import fr.cliker.clikerv2.npc.ZnpcsNpc;
import fr.cliker.clikerv2.runtime.*;
import fr.cliker.clikerv2.ui.ViewRequests;

import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Un même menu pour les catégories et toutes les périodes calendaires. */
public final class RankingsModule implements CommandExecutor, TabCompleter, Listener {
    public static final String USE = "cliker.classement.use", ADMIN = "cliker.classement.admin";
    private final JavaPlugin plugin;
    private final RankingStore store;
    private final ViewRequests requests;
    private final ZoneId zone;
    private final Settings settings;
    private final ZnpcsNpc npc;
    private final Map<UUID, Long> loads = new HashMap<>();
    private long requestId;
    private volatile boolean stopped;

    public RankingsModule(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        store = new RankingStore(database);
        zone = database.rankingZone();
        requests = new ViewRequests(plugin);
        settings = new Settings(plugin, "pnj/classements.yml");
        npc =
                new ZnpcsNpc(
                        plugin,
                        settings.yaml,
                        settings::save,
                        this::open,
                        "classements",
                        "ClickHunterClassements");
    }

    public void start() {
        var command = plugin.getCommand("top");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        npc.start();
    }

    public void open(Player player) {
        if (stopped || !player.hasPermission(USE)) return;
        int[] slots = {20, 22, 24, 29, 31, 33};
        var view =
                HuntUI.view(
                        player,
                        "&6🏆 Classements",
                        54,
                        slot -> {
                            if (!player.hasPermission(USE)) return;
                            for (int i = 0; i < slots.length; i++)
                                if (slot == slots[i]) {
                                    open(
                                            player,
                                            RankingMetric.values()[i],
                                            RankingPeriod.ALL,
                                            LocalDate.now(zone));
                                    return;
                                }
                            if (slot == 49) player.performCommand("menu");
                        });
        view.getInventory()
                .setItem(
                        4,
                        HuntUI.item(
                                Material.BOOK,
                                "&6🏆 Choisis un classement",
                                List.of(
                                        "&7Une catégorie, puis une période.",
                                        "&7Top 10 • Jour • Semaine • Mois • Année")));
        for (var metric : RankingMetric.values())
            view.getInventory()
                    .setItem(
                            slots[metric.ordinal()],
                            HuntUI.item(
                                    metric.icon,
                                    metric.title,
                                    List.of(
                                            "&7Consulte les dix premiers joueurs.",
                                            "&eClique pour ouvrir.")));
        view.getInventory().setItem(49, HuntUI.item(Material.ARROW, "&eRetour au menu", List.of()));
        player.openInventory(view.getInventory());
    }

    private void open(Player player, RankingMetric metric, RankingPeriod period, LocalDate date) {
        if (stopped || !player.hasPermission(USE)) return;
        // Une requête simultanée par joueur, sans bloquer le thread serveur.
        if (loads.containsKey(player.getUniqueId())) return;
        long load = ++requestId;
        loads.put(player.getUniqueId(), load);
        var request = requests.begin(player);
        store.board(metric, period, date, endViews.contains(player.getUniqueId()))
                .whenComplete(
                        (board, error) -> {
                            if (stopped || !plugin.isEnabled()) return;
                            ServerTasks.run(
                                    plugin,
                                    () -> {
                                        if (!Objects.equals(loads.get(player.getUniqueId()), load))
                                            return;
                                        loads.remove(player.getUniqueId());
                                        if (!requests.complete(player, request)
                                                || !player.hasPermission(USE)) return;
                                        if (error != null) {
                                            plugin.getLogger()
                                                    .log(
                                                            java.util.logging.Level.SEVERE,
                                                            "Classement indisponible",
                                                            error);
                                            player.sendMessage(
                                                    "§cClassement temporairement indisponible."
                                                            + " Réessaie plus tard.");
                                            return;
                                        }
                                        render(player, metric, period, date, board);
                                    });
                        });
    }

    private final java.util.Set<UUID> endViews = new java.util.HashSet<>();
    private fr.cliker.clikerv2.features.Menus dateMenus;

    private void calendar(Player player, RankingMetric metric, LocalDate month) {
        var menus = dateMenus;
        if (menus == null) {
            dateMenus = new fr.cliker.clikerv2.features.Menus(plugin);
            menus = dateMenus;
        }
        var view =
                menus.create(
                        player,
                        54,
                        "Calendrier — " + month.getMonthValue() + "/" + month.getYear());
        int offset = month.getDayOfWeek().getValue() - 1;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate chosen = month.withDayOfMonth(day);
            int index = offset + day - 1;
            int slot = (index / 7) * 9 + 1 + index % 7;
            if (chosen.isAfter(LocalDate.now(zone))) continue;
            view.put(
                    slot,
                    fr.cliker.clikerv2.features.Menus.item(
                            Material.PAPER, "&e" + day, "&7" + chosen),
                    (who, right) -> duration(who, metric, chosen));
        }
        view.put(
                45,
                fr.cliker.clikerv2.features.Menus.item(Material.ARROW, "&eMois précédent"),
                (who, right) -> calendar(who, metric, month.minusMonths(1)));
        view.put(
                8,
                fr.cliker.clikerv2.features.Menus.item(Material.NETHER_STAR, "&aDepuis toujours"),
                (who, right) -> open(who, metric, RankingPeriod.ALL, month));
        if (month.plusMonths(1).isBefore(LocalDate.now(zone).plusDays(1)))
            view.put(
                    53,
                    fr.cliker.clikerv2.features.Menus.item(Material.ARROW, "&eMois suivant"),
                    (who, right) -> calendar(who, metric, month.plusMonths(1)));
        view.show(player);
    }

    private void duration(Player player, RankingMetric metric, LocalDate day) {
        var menus = dateMenus;
        if (menus == null) {
            dateMenus = new fr.cliker.clikerv2.features.Menus(plugin);
            menus = dateMenus;
        }
        var v = menus.create(player, 27, "Période autour du " + day);
        int n = 0;
        for (var period : RankingPeriod.values())
            v.put(
                    10 + n++,
                    fr.cliker.clikerv2.features.Menus.item(Material.CLOCK, "&b" + period.label),
                    (who, right) -> {
                        endViews.add(who.getUniqueId());
                        open(who, metric, period, day);
                    });
        v.put(
                22,
                fr.cliker.clikerv2.features.Menus.item(Material.ARROW, "&eCalendrier"),
                (who, right) -> calendar(who, metric, day.withDayOfMonth(1)));
        v.show(player);
    }

    private void render(
            Player player,
            RankingMetric metric,
            RankingPeriod period,
            LocalDate date,
            RankingStore.Board board) {
        LocalDate today = LocalDate.now(zone);
        boolean all = period == RankingPeriod.ALL;
        boolean tracked = all || period.shift(date, 1).isAfter(board.since());
        LocalDate previous = all ? date : period.shift(date, -1),
                next = all ? date : period.shift(date, 1);
        boolean hasPrevious = !all && period.available(previous, board.since(), today);
        boolean hasNext = !all && !next.isAfter(period.start(today));
        int[] ranks = {20, 22, 24, 28, 29, 30, 31, 32, 33, 34};
        int[] periods = {10, 12, 13, 14, 16};
        var view =
                HuntUI.view(
                        player,
                        "&6Classements &8• &7" + period.label,
                        54,
                        slot -> {
                            if (!player.hasPermission(USE)) return;
                            if (slot == 46) {
                                calendar(player, metric, date.withDayOfMonth(1));
                                return;
                            }
                            if (slot == 48) {
                                open(player);
                                return;
                            }
                            for (int i = 0; i < periods.length; i++)
                                if (slot == periods[i]) {
                                    var selected = RankingPeriod.values()[i];
                                    open(player, metric, selected, selected.start(date));
                                    return;
                                }
                            if (slot == 45 && hasPrevious) open(player, metric, period, previous);
                            else if (slot == 53 && hasNext) open(player, metric, period, next);
                            else if (slot == 49) open(player, metric, period, period.start(today));
                            else if (slot == 50) open(player, metric, period, date);
                        });
        view.getInventory()
                .setItem(
                        46,
                        HuntUI.item(
                                Material.CLOCK,
                                "&bChoisir une date",
                                List.of("&7Calendrier mensuel, puis choix de la durée.")));
        Material[] periodIcons = {
            Material.SUNFLOWER, Material.PAPER, Material.MAP, Material.BOOK, Material.NETHER_STAR
        };
        for (var selected : RankingPeriod.values())
            view.getInventory()
                    .setItem(
                            periods[selected.ordinal()],
                            HuntUI.item(
                                    periodIcons[selected.ordinal()],
                                    "&e" + selected.label,
                                    List.of(
                                            selected == period
                                                    ? "&aPériode sélectionnée"
                                                    : "&7Naviguer entre les périodes de ce"
                                                            + " type.")));
        var info = new ArrayList<String>();
        info.add("&f" + period.range(date));
        info.add("&7Fuseau : " + zone);
        info.add(
                "&7"
                        + (endViews.contains(player.getUniqueId())
                                        && !all
                                        && metric != RankingMetric.LEVELS
                                ? "Situation à la fin de la période sélectionnée."
                                : metric.meaning(all)));
        info.add(
                "&7Suivi par période depuis le "
                        + board.since().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")));
        if (tracked && !all && !period.start(date).isAfter(board.since()))
            info.add("&eLa première période peut être partielle.");
        if (!tracked) info.add("&cHistorique indisponible pour cette période.");
        if (metric == RankingMetric.DONORS) info.add("&7Les points de dons restent privés.");
        view.getInventory().setItem(4, HuntUI.item(Material.CLOCK, metric.title, info));
        var entries = tracked ? board.entries() : List.<RankingStore.Entry>of();
        for (int rank = 0; rank < 10; rank++) {
            if (rank >= entries.size()) {
                view.getInventory()
                        .setItem(
                                ranks[rank],
                                HuntUI.item(
                                        Material.GRAY_DYE,
                                        "&7#" + (rank + 1) + " &8—",
                                        List.of(
                                                !tracked
                                                        ? "&7Historique indisponible."
                                                        : "&7Aucun joueur classé à cette place.")));
                continue;
            }
            var entry = entries.get(rank);
            var lore = new ArrayList<String>();
            if (metric != RankingMetric.DONORS)
                lore.add("&f" + metric.format(entry.value()) + " &7" + metric.unit);
            else lore.add("&7Donateur classé pour cette période.");
            lore.add("&7" + period.range(date));
            view.getInventory()
                    .setItem(
                            ranks[rank],
                            HuntUI.item(
                                    Material.PLAYER_HEAD,
                                    "&e#" + (rank + 1) + " &f" + entry.name(),
                                    lore));
        }
        view.getInventory()
                .setItem(
                        45,
                        HuntUI.item(
                                hasPrevious ? Material.ARROW : Material.GRAY_DYE,
                                "&ePériode précédente",
                                List.of(
                                        hasPrevious
                                                ? "&7" + period.range(previous)
                                                : all
                                                        ? "&7Choisis Jour, Semaine, Mois ou Année."
                                                        : "&7Début de l’historique atteint.")));
        view.getInventory()
                .setItem(
                        49,
                        HuntUI.item(
                                Material.COMPASS, "&aRevenir à la période actuelle", List.of()));
        view.getInventory().setItem(50, HuntUI.item(Material.CLOCK, "&bActualiser", List.of()));
        view.getInventory()
                .setItem(
                        53,
                        HuntUI.item(
                                hasNext ? Material.ARROW : Material.GRAY_DYE,
                                "&ePériode suivante",
                                List.of(
                                        hasNext
                                                ? "&7" + period.range(next)
                                                : "&7Tu es à la période actuelle.")));
        view.getInventory()
                .setItem(
                        48,
                        HuntUI.item(
                                Material.COMPASS,
                                "&6Changer de catégorie",
                                List.of("&7Revenir aux six classements.")));
        player.openInventory(view.getInventory());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) open(player);
            else sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        var metric = RankingMetric.parse(args[0]);
        if (args.length == 1 && metric != null) {
            if (sender instanceof Player player)
                open(player, metric, RankingPeriod.ALL, LocalDate.now(zone));
            return true;
        }
        if (args[0].equalsIgnoreCase("pnj")) {
            if (!sender.hasPermission(ADMIN)) {
                sender.sendMessage("§cPermission administrateur requise.");
                return true;
            }
            if (args.length != 2
                    || !Set.of("spawn", "remove").contains(args[1].toLowerCase(Locale.ROOT))) {
                usage(sender);
                return true;
            }
            if (!npc.available()) {
                sender.sendMessage("§cZNPCsPlus 2.0.0 est nécessaire pour ce PNJ.");
                return true;
            }
            try {
                if (args[1].equalsIgnoreCase("spawn")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§cPlace-toi en jeu pour poser le PNJ.");
                        return true;
                    }
                    sender.sendMessage(
                            npc.spawn(player.getLocation())
                                    ? "§aPNJ des classements placé."
                                    : "§eCe PNJ existe déjà. Retire-le pour le déplacer.");
                } else
                    sender.sendMessage(
                            npc.remove()
                                    ? "§aPNJ des classements retiré."
                                    : "§7Aucun PNJ des classements placé.");
            } catch (Exception error) {
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.SEVERE,
                                "PNJ des classements indisponible",
                                error);
                sender.sendMessage("§cImpossible de modifier le PNJ. Consulte les journaux.");
            }
            return true;
        }
        usage(sender);
        return true;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(
                "§e/top [ecus|gemmes|cendres|kills|niveaux|donateurs]"
                        + (sender.hasPermission(ADMIN) ? " §7ou §e/top pnj spawn|remove" : ""));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (var metric : RankingMetric.values()) options.add(metric.id);
            if (sender.hasPermission(ADMIN)) options.add("pnj");
        } else if (args.length == 2
                && args[0].equalsIgnoreCase("pnj")
                && sender.hasPermission(ADMIN)) options.addAll(List.of("spawn", "remove"));
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(prefix)).toList();
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        endViews.remove(event.getPlayer().getUniqueId());
        loads.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        stopped = true;
        loads.clear();
        HandlerList.unregisterAll(this);
        HandlerList.unregisterAll(requests);
        npc.shutdown();
    }
}
