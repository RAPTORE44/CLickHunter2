package fr.cliker.clikerv2.donors;

import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.npc.ZnpcsNpc;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Podium manuel : aucun montant, aucune intégration de paiement. */
public final class DonorPodium implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final Map<Integer, Settings> configs = new LinkedHashMap<>();
    private final Map<Integer, ZnpcsNpc> npcs = new LinkedHashMap<>();

    public DonorPodium(JavaPlugin plugin) {
        this.plugin = plugin;
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
    }

    private static String title(int rank) {
        return (rank == 1 ? "&6&l1er" : rank == 2 ? "&7&l2e" : "&c&l3e") + " donateur";
    }

    private static void appearance(Settings settings) {
        String name = settings.yaml.getString("joueur", "RAPTORE44");
        if (!name.matches("[A-Za-z0-9_]{1,16}"))
            throw new IllegalArgumentException("Pseudo donateur invalide");
        settings.yaml.set("pnj.type", "player");
        settings.yaml.set("pnj.skin", name);
        settings.yaml.set("pnj.titre", settings.yaml.getString("titre-rang"));
        settings.yaml.set(
                "pnj.sous-titre",
                settings.yaml.getString("format-pseudo", "&f{joueur}").replace("{joueur}", name));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.donateurs.admin")) {
            sender.sendMessage("§cPermission administrateur requise.");
            return true;
        }
        if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("list")) {
            configs.forEach(
                    (rank, config) ->
                            sender.sendMessage(
                                    "§e#"
                                            + rank
                                            + " §f"
                                            + config.yaml.getString("joueur")
                                            + (config.yaml.getBoolean("pnj.actif")
                                                    ? " §a(placé)"
                                                    : " §7(non placé)")));
            sender.sendMessage(
                    "§7/donateurs set <1|2|3> <pseudo> · /donateurs spawn|remove <1|2|3>");
            return true;
        }
        if (args.length < 2 || !args[1].matches("[123]")) {
            usage(sender);
            return true;
        }
        int rank = Integer.parseInt(args[1]);
        var npc = npcs.get(rank);
        var settings = configs.get(rank);
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set" -> {
                    if (args.length != 3 || !args[2].matches("[A-Za-z0-9_]{1,16}")) {
                        usage(sender);
                        return true;
                    }
                    settings.yaml.set("joueur", args[2]);
                    appearance(settings);
                    settings.save();
                    if (npc.available()) npc.refresh();
                    sender.sendMessage(
                            "§aDonateur #"
                                    + rank
                                    + " : §f"
                                    + args[2]
                                    + "§a. Pseudo et skin sauvegardés.");
                }
                case "spawn" -> {
                    if (args.length != 2) {
                        usage(sender);
                        return true;
                    }
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§cPlace-toi en jeu pour poser ce PNJ.");
                        return true;
                    }
                    if (!npc.available()) {
                        sender.sendMessage("§cZNPCsPlus 2.0.0 est nécessaire.");
                        return true;
                    }
                    sender.sendMessage(
                            npc.spawn(player.getLocation())
                                    ? "§aPNJ donateur #" + rank + " placé."
                                    : "§eCe PNJ existe déjà. Retire-le avant de le déplacer.");
                }
                case "remove" -> {
                    if (args.length != 2) {
                        usage(sender);
                        return true;
                    }
                    sender.sendMessage(
                            npc.remove()
                                    ? "§aPNJ retiré. Le joueur associé est conservé."
                                    : "§7Aucun PNJ placé à ce rang.");
                }
                default -> usage(sender);
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Podium donateurs : modification impossible",
                            e);
            sender.sendMessage(
                    "§cImpossible d’actualiser le PNJ. Consulte les journaux ; la configuration"
                            + " sauvegardée sera relue au redémarrage.");
        }
        return true;
    }

    private static void usage(CommandSender s) {
        s.sendMessage("§e/donateurs set <1|2|3> <pseudo> §7ou §e/donateurs spawn|remove <1|2|3>");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("cliker.donateurs.admin")) return List.of();
        List<String> options =
                a.length == 1
                        ? List.of("set", "spawn", "remove", "list")
                        : a.length == 2
                                        && Set.of("set", "spawn", "remove")
                                                .contains(a[0].toLowerCase(Locale.ROOT))
                                ? List.of("1", "2", "3")
                                : a.length == 3 && a[0].equalsIgnoreCase("set")
                                        ? plugin.getServer().getOnlinePlayers().stream()
                                                .map(Player::getName)
                                                .toList()
                                        : List.of();
        String prefix = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    public void shutdown() {
        npcs.values().forEach(ZnpcsNpc::shutdown);
        npcs.clear();
        configs.clear();
    }
}
