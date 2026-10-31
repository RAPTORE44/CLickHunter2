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
        open(player, RankingMetric.ECUS, RankingPeriod.ALL, LocalDate.now(zone));
    }

    private void open(Player player, RankingMetric metric, RankingPeriod period, LocalDate date) {
        if (stopped || !player.hasPermission(USE)) return;
        // Une requête simultanée par joueur, sans bloquer le thread serveur.
        if (loads.containsKey(player.getUniqueId())) return;
        long load = ++requestId;
        loads.put(player.getUniqueId(), load);
        var request = requests.begin(player);
        store.board(metric, period, date)
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
        int[] ranks = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};
        var view =
                HuntUI.view(
                        player,
                        "&6Classements &8• &7" + period.label,
                        54,
                        slot -> {
                            if (!player.hasPermission(USE)) return;
                            if (slot >= 0 && slot < RankingMetric.values().length)
                                open(player, RankingMetric.values()[slot], period, date);
                            else if (slot >= 9 && slot < 9 + RankingPeriod.values().length) {
                                var selected = RankingPeriod.values()[slot - 9];
                                open(player, metric, selected, selected.start(date));
                            } else if (slot == 45 && hasPrevious)
                                open(player, metric, period, previous);
                            else if (slot == 53 && hasNext) open(player, metric, period, next);
                            else if (slot == 49) open(player, metric, period, period.start(today));
                            else if (slot == 50) open(player, metric, period, date);
                        });
        for (var category : RankingMetric.values())
            view.getInventory()
                    .setItem(
                            category.ordinal(),
                            HuntUI.item(
                                    category.icon,
                                    category.title,
                                    List.of(
                                            category == metric
                                                    ? "&aCatégorie sélectionnée"
                                                    : "&eClique pour sélectionner.")));
        Material[] periodIcons = {
            Material.SUNFLOWER, Material.PAPER, Material.MAP, Material.BOOK, Material.NETHER_STAR
        };
        for (var selected : RankingPeriod.values())
            view.getInventory()
                    .setItem(
                            9 + selected.ordinal(),
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
        info.add("&7" + metric.meaning(all));
        info.add(
                "&7Suivi par période depuis le "
                        + board.since().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")));
        if (tracked && !all && !period.start(date).isAfter(board.since()))
            info.add("&eLa première période peut être partielle.");
        if (!tracked) info.add("&cHistorique indisponible pour cette période.");
        if (metric == RankingMetric.DONORS) info.add("&7Les points de dons restent privés.");
        view.getInventory().setItem(15, HuntUI.item(Material.CLOCK, metric.title, info));
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
