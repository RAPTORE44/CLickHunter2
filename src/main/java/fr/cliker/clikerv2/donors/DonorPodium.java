package fr.cliker.clikerv2.donors;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.hunting.HuntUI;
import fr.cliker.clikerv2.npc.ZnpcsNpc;
import fr.cliker.clikerv2.runtime.ServerTasks;
import fr.cliker.clikerv2.ui.ViewRequests;

import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Classement automatique de points administrateur, indépendant des monnaies du jeu. */
public final class DonorPodium implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "cliker.donateurs.admin";
    private final JavaPlugin plugin;
    private final DonorPointsStore store;
    private final ViewRequests requests;
    private final Map<Integer, Settings> configs = new LinkedHashMap<>();
    private final Map<Integer, ZnpcsNpc> npcs = new LinkedHashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final Set<Integer> dirtyRanks = new HashSet<>();
    private volatile boolean stopped;
    private long rankingRequest;

    public DonorPodium(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        store = new DonorPointsStore(database);
        requests = new ViewRequests(plugin);
    }

    public void start() {
        for (int rank = 1; rank <= 3; rank++) {
            var settings = new Settings(plugin, "pnj/donateurs-" + rank + ".yml");
            configs.put(rank, settings);
            if (!settings.yaml.contains("joueur")) settings.yaml.set("joueur", "RAPTORE44");
            if (!settings.yaml.contains("titre-rang")) settings.yaml.set("titre-rang", title(rank));
            if (!settings.yaml.contains("format-pseudo"))
                settings.yaml.set("format-pseudo", "&f{joueur}");
            if (!settings.yaml.contains("pnj.echelle")) settings.yaml.set("pnj.echelle", 2.0);
            appearance(settings);
            settings.save();
            final int position = rank;
            var npc =
                    new ZnpcsNpc(
                            plugin,
                            settings.yaml,
                            settings::save,
                            player ->
                                    player.sendMessage(
                                            fr.cliker.clikerv2.daily.DailySettings.color(
                                                    title(position)
                                                            + " &8• &f"
                                                            + settings.yaml.getString("joueur"))),
                            "donateur-" + rank,
                            "ClickHunterDonateur" + rank);
            npcs.put(rank, npc);
            npc.start();
        }
        var command = plugin.getCommand("donateurs");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        org.bukkit.Bukkit.getServicesManager()
                .register(
                        DonorPodium.class, this, plugin, org.bukkit.plugin.ServicePriority.Normal);
        refreshRanking();
    }

    public List<String> diagnostics() {
        long placed =
                configs.values().stream()
                        .filter(c -> c.yaml.getBoolean("pnj.actif", false))
                        .count();
        return List.of(
                "Donateurs : module actif", "Podium : " + placed + "/3 positions PNJ sauvegardées");
    }

    private static String title(int rank) {
        return (rank == 1 ? "&6&l1er" : rank == 2 ? "&7&l2e" : "&c&l3e") + " donateur";
    }

    private static void appearance(Settings settings) {
        String name = settings.yaml.getString("joueur", "RAPTORE44");
        DonorPointsStore.key(name);
        settings.yaml.set("pnj.type", "player");
        settings.yaml.set("pnj.skin", name);
        settings.yaml.set("pnj.titre", settings.yaml.getString("titre-rang"));
        settings.yaml.set(
                "pnj.sous-titre",
                settings.yaml.getString("format-pseudo", "&f{joueur}").replace("{joueur}", name));
    }

    private <T> void result(CompletableFuture<T> future, CommandSender sender, Consumer<T> action) {
        result(future, sender, action, () -> {});
    }

    private <T> void result(
            CompletableFuture<T> future,
            CommandSender sender,
            Consumer<T> action,
            Runnable completed) {
        future.whenComplete(
                (value, error) -> {
                    if (stopped || !plugin.isEnabled()) return;
                    ServerTasks.run(
                            plugin,
                            () -> {
                                if (stopped) return;
                                completed.run();
                                if (error != null) {
                                    Throwable cause = error;
                                    while (cause instanceof java.util.concurrent.CompletionException
                                            && cause.getCause() != null) cause = cause.getCause();
                                    if (cause instanceof IllegalArgumentException) {
                                        if (sender != null)
                                            sender.sendMessage("§c" + cause.getMessage());
                                    } else {
                                        plugin.getLogger()
                                                .log(
                                                        java.util.logging.Level.SEVERE,
                                                        "Points donateurs indisponibles",
                                                        error);
                                        if (sender != null)
                                            sender.sendMessage(
                                                    "§cOpération impossible. Consulte les journaux"
                                                            + " avant de réessayer.");
                                    }
                                } else action.accept(value);
                            });
                });
    }

    private void refreshRanking() {
        refreshRanking(false);
    }

    private void refreshRanking(boolean force) {
        long request = ++rankingRequest;
        result(
                store.top(),
                null,
                top -> {
                    if (request != rankingRequest) return;
                    for (int rank = 1; rank <= 3; rank++) {
                        String name = rank <= top.size() ? top.get(rank - 1).name() : "RAPTORE44";
                        var settings = configs.get(rank);
                        if (!force
                                && !dirtyRanks.contains(rank)
                                && name.equals(settings.yaml.getString("joueur"))
                                && name.equals(settings.yaml.getString("pnj.skin"))) continue;
                        try {
                            settings.yaml.set("joueur", name);
                            appearance(settings);
                            settings.save();
                            var npc = npcs.get(rank);
                            if (npc.available()) npc.refresh();
                            dirtyRanks.remove(rank);
                        } catch (Exception error) {
                            dirtyRanks.add(rank);
                            plugin.getLogger()
                                    .log(
                                            java.util.logging.Level.SEVERE,
                                            "Actualisation du donateur #" + rank + " impossible",
                                            error);
                        }
                    }
                });
    }

    private void open(Player player, int page) {
        if (!player.hasPermission(ADMIN)) return;
        int index = Math.max(0, Math.min(page, 1_000_000));
        var request = requests.begin(player);
        result(
                store.page(index),
                player,
                data -> {
                    if (!requests.complete(player, request) || !player.hasPermission(ADMIN)) return;
                    var rows = data.donors();
                    var view =
                            HuntUI.view(
                                    player,
                                    "&6Donateurs &8• &7Page " + (index + 1),
                                    54,
                                    slot -> {
                                        if (!player.hasPermission(ADMIN)) return;
                                        if (slot >= 0 && slot < rows.size())
                                            detail(player, rows.get(slot).name(), index);
                                        else if (slot == 45 && index > 0) open(player, index - 1);
                                        else if (slot == 53 && data.hasNext())
                                            open(player, index + 1);
                                        else if (slot == 49) open(player, index);
                                        else if (slot == 48) {
                                            player.closeInventory();
                                            player.sendMessage(
                                                    net.kyori.adventure.text.Component.text(
                                                                    "Clique ici pour préparer"
                                                                        + " /donateurs add <pseudo>"
                                                                        + " <points>")
                                                            .clickEvent(
                                                                    net.kyori.adventure.text.event
                                                                            .ClickEvent
                                                                            .suggestCommand(
                                                                                    "/donateurs add"
                                                                                        + " ")));
                                        } else if (slot == 50) player.closeInventory();
                                    });
                    for (int i = 0; i < rows.size(); i++) {
                        var donor = rows.get(i);
                        view.getInventory()
                                .setItem(
                                        i,
                                        HuntUI.item(
                                                Material.PLAYER_HEAD,
                                                "&e#" + (index * 45 + i + 1) + " &f" + donor.name(),
                                                List.of(
                                                        "&7Points : &6"
                                                                + DonorPointsStore.format(
                                                                        donor.units()),
                                                        "&71 € = 1 point",
                                                        "",
                                                        "&eClique pour gérer ses points.")));
                    }
                    if (rows.isEmpty())
                        view.getInventory()
                                .setItem(
                                        22,
                                        HuntUI.item(
                                                Material.PAPER,
                                                "&7Aucun donateur enregistré",
                                                List.of(
                                                        "&7Ajoute un joueur avec le bouton"
                                                                + " ci-dessous.")));
                    if (index > 0)
                        view.getInventory()
                                .setItem(
                                        45,
                                        HuntUI.item(
                                                Material.ARROW, "&ePage précédente", List.of()));
                    view.getInventory()
                            .setItem(
                                    48,
                                    HuntUI.item(
                                            Material.EMERALD,
                                            "&aAjouter un donateur",
                                            List.of("&7Préparer la commande dans le chat.")));
                    view.getInventory()
                            .setItem(
                                    49,
                                    HuntUI.item(
                                            Material.CLOCK,
                                            "&bActualiser",
                                            List.of(
                                                    "&7Les points ne sont visibles que par les"
                                                            + " administrateurs.")));
                    view.getInventory()
                            .setItem(50, HuntUI.item(Material.BARRIER, "&cFermer", List.of()));
                    if (data.hasNext())
                        view.getInventory()
                                .setItem(
                                        53,
                                        HuntUI.item(Material.ARROW, "&ePage suivante", List.of()));
                    player.openInventory(view.getInventory());
                });
    }

    private void detail(Player player, String name, int page) {
        if (!player.hasPermission(ADMIN)) return;
        var request = requests.begin(player);
        result(
                store.find(name),
                player,
                donor -> {
                    if (!requests.complete(player, request) || !player.hasPermission(ADMIN)) return;
                    int[] slots = {10, 11, 12, 14, 15, 16};
                    long[] deltas = {100, 1000, 10000, -100, -1000, -10000};
                    var view =
                            HuntUI.view(
                                    player,
                                    "&6Points &8• &f" + donor.name(),
                                    27,
                                    slot -> {
                                        if (!player.hasPermission(ADMIN)) return;
                                        if (slot == 22) {
                                            open(player, page);
                                            return;
                                        }
                                        for (int i = 0; i < slots.length; i++)
                                            if (slots[i] == slot) {
                                                if (!busy.add(player.getUniqueId())) return;
                                                change(
                                                        player,
                                                        donor.name(),
                                                        deltas[i],
                                                        null,
                                                        player.getOpenInventory().getTopInventory(),
                                                        page);
                                                return;
                                            }
                                    });
                    view.getInventory()
                            .setItem(
                                    4,
                                    HuntUI.item(
                                            Material.PLAYER_HEAD,
                                            "&f" + donor.name(),
                                            List.of(
                                                    "&7Points : &6"
                                                            + DonorPointsStore.format(
                                                                    donor.units()),
                                                    "&71 € = 1 point")));
                    for (int i = 0; i < slots.length; i++)
                        view.getInventory()
                                .setItem(
                                        slots[i],
                                        HuntUI.item(
                                                deltas[i] > 0
                                                        ? Material.LIME_DYE
                                                        : Material.RED_DYE,
                                                (deltas[i] > 0 ? "&a+" : "&c")
                                                        + DonorPointsStore.format(deltas[i])
                                                        + " points",
                                                List.of(
                                                        "&7Application immédiate.",
                                                        "&7Pour un autre montant :",
                                                        "&f/donateurs "
                                                                + (deltas[i] > 0
                                                                        ? "add "
                                                                        : "retirer ")
                                                                + donor.name()
                                                                + " <points>")));
                    view.getInventory()
                            .setItem(
                                    22,
                                    HuntUI.item(
                                            Material.ARROW, "&eRetour au classement", List.of()));
                    player.openInventory(view.getInventory());
                });
    }

    private void change(
            CommandSender sender,
            String name,
            long delta,
            String reference,
            Inventory previous,
            int page) {
        result(
                store.change(name, delta, reference, sender.getName()),
                sender,
                changed -> {
                    refreshRanking();
                    if (sender instanceof Player player
                            && (!fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(player)
                                    || !player.hasPermission(ADMIN))) return;
                    sender.sendMessage(
                            changed.applied()
                                    ? "§a"
                                            + changed.donor().name()
                                            + " : §e"
                                            + DonorPointsStore.format(changed.donor().units())
                                            + " points§a. Podium actualisé automatiquement."
                                    : "§7Cette référence a déjà été traitée : aucun point ajouté ou"
                                            + " retiré à nouveau.");
                    if (sender instanceof Player player
                            && previous != null
                            && player.getOpenInventory().getTopInventory() == previous)
                        detail(player, changed.donor().name(), page);
                },
                () -> {
                    if (previous != null && sender instanceof Player p)
                        busy.remove(p.getUniqueId());
                });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage("§cPermission administrateur requise.");
            return true;
        }
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        try {
            if ((sub.equals("menu") || sub.equals("list")) && args.length <= 1) {
                if (sender instanceof Player p && sub.equals("menu")) open(p, 0);
                else
                    result(
                            store.page(0),
                            sender,
                            data -> {
                                if (data.donors().isEmpty())
                                    sender.sendMessage("§7Aucun donateur enregistré.");
                                int rank = 1;
                                for (var donor :
                                        data.donors()
                                                .subList(0, Math.min(10, data.donors().size())))
                                    sender.sendMessage(
                                            "§e#"
                                                    + rank++
                                                    + " §f"
                                                    + donor.name()
                                                    + " §7— §6"
                                                    + DonorPointsStore.format(donor.units())
                                                    + " points");
                            });
                return true;
            }
            if (Set.of("add", "ajouter", "retirer", "take", "remove").contains(sub)
                    && (args.length == 3 || args.length == 4)) {
                long units = DonorPointsStore.parse(args[2]);
                change(
                        sender,
                        args[1],
                        sub.equals("add") || sub.equals("ajouter") ? units : -units,
                        args.length == 4 ? args[3] : null,
                        null,
                        0);
                return true;
            }
            if (sub.equals("points") && args.length == 2) {
                result(
                        store.find(args[1]),
                        sender,
                        donor ->
                                sender.sendMessage(
                                        "§e"
                                                + donor.name()
                                                + " : §6"
                                                + DonorPointsStore.format(donor.units())
                                                + " points"));
                return true;
            }
            if (sub.equals("refresh") && args.length == 1) {
                refreshRanking(true);
                sender.sendMessage("§aActualisation du podium demandée.");
                return true;
            }
            if (sub.equals("set")) {
                sender.sendMessage(
                        "§eLe classement est automatique. Utilise /donateurs add <pseudo> <points>"
                                + " ou /donateurs retirer <pseudo> <points>.");
                return true;
            }
            if (args.length == 2
                    && args[1].matches("[123]")
                    && (sub.equals("spawn") || sub.equals("remove"))) {
                int rank = Integer.parseInt(args[1]);
                var npc = npcs.get(rank);
                if (sub.equals("spawn")) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage("§cPlace-toi en jeu pour poser ce PNJ.");
                        return true;
                    }
                    if (!npc.available()) {
                        sender.sendMessage("§cZNPCsPlus 2.0.0 est nécessaire.");
                        return true;
                    }
                    sender.sendMessage(
                            npc.spawn(p.getLocation())
                                    ? "§aPNJ donateur #" + rank + " placé."
                                    : "§eCe PNJ existe déjà. Retire-le avant de le déplacer.");
                } else
                    sender.sendMessage(
                            npc.remove()
                                    ? "§aPNJ retiré ; les points sont conservés."
                                    : "§7Aucun PNJ placé à ce rang.");
                return true;
            }
            usage(sender);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c" + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger()
                    .log(java.util.logging.Level.SEVERE, "Modification du podium impossible", e);
            sender.sendMessage("§cImpossible de modifier le PNJ. Consulte les journaux.");
        }
        return true;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(
                "§e/donateurs [menu|list] · /donateurs add|retirer <pseudo> <points> [référence] ·"
                    + " /donateurs points <pseudo> · /donateurs spawn|remove <1|2|3> · /donateurs"
                    + " refresh");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission(ADMIN)) return List.of();
        List<String> values =
                a.length == 1
                        ? List.of(
                                "menu", "list", "add", "ajouter", "retirer", "remove", "take",
                                "points", "spawn", "refresh")
                        : a.length == 2
                                        && (a[0].equalsIgnoreCase("spawn")
                                                || a[0].equalsIgnoreCase("remove"))
                                ? List.of("1", "2", "3")
                                : a.length == 2
                                                && Set.of(
                                                                "add", "ajouter", "retirer", "take",
                                                                "points")
                                                        .contains(a[0].toLowerCase(Locale.ROOT))
                                        ? plugin.getServer().getOnlinePlayers().stream()
                                                .map(Player::getName)
                                                .toList()
                                        : a.length == 3
                                                        && Set.of(
                                                                        "add", "ajouter", "retirer",
                                                                        "take", "remove")
                                                                .contains(
                                                                        a[0].toLowerCase(
                                                                                Locale.ROOT))
                                                ? List.of("1", "5", "10", "20", "50", "100")
                                                : List.of();
        String prefix = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    public void shutdown() {
        org.bukkit.Bukkit.getServicesManager().unregister(DonorPodium.class, this);
        stopped = true;
        org.bukkit.event.HandlerList.unregisterAll(requests);
        busy.clear();
        npcs.values().forEach(ZnpcsNpc::shutdown);
        npcs.clear();
        configs.clear();
    }
}
