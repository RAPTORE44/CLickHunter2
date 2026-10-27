package fr.cliker.clikerv2.wiki;

import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.npc.ZnpcsNpc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.logging.Level;

public final class WikiModule implements CommandExecutor, TabCompleter {
    public static final String USE = "cliker.wiki.use";
    public static final String ADMIN = "cliker.wiki.admin";
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration config;
    private final ZnpcsNpc npc;
    private final String link;

    public WikiModule(JavaPlugin plugin) {
        this.plugin = plugin;
        file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "wiki.yml");
        if (!file.exists()) fr.cliker.clikerv2.config.ConfigFiles.install(plugin, "wiki.yml");
        config = fr.cliker.clikerv2.config.YamlFiles.load(file);
        link = config.getString("lien", "https://wiki.clickhunter.fr");
        URI uri = URI.create(link);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Le lien Wiki doit être une URL HTTPS valide");
        npc =
                new ZnpcsNpc(
                        plugin,
                        config,
                        this::save,
                        this::sendLink,
                        "clikerv2-wiki-npc",
                        "ClickHunterWiki");
    }

    public void start() {
        Objects.requireNonNull(plugin.getCommand("wiki")).setExecutor(this);
        plugin.getCommand("wiki").setTabCompleter(this);
        npc.start();
    }

    private void save() {
        try {
            config.save(file);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Sauvegarde du PNJ Wiki impossible", e);
        }
    }

    public Component clickableLink() {
        return Component.text(link, NamedTextColor.BLUE, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(
                        HoverEvent.showText(
                                DailySettings.color(
                                        config.getString(
                                                "messages.survol",
                                                "&aClique pour rejoindre le Wiki."))));
    }

    public void sendLink(CommandSender sender) {
        if (!sender.hasPermission(USE)) {
            message(sender, "interdit");
            return;
        }
        sender.sendMessage(
                DailySettings.color(config.getString("messages.titre", "&9Wiki ClickHunter")));
        sender.sendMessage(clickableLink());
        sender.sendMessage(
                Component.text("[Ouvrir le livre des commandes]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/wiki livre")));
    }

    private void message(CommandSender sender, String key) {
        sender.sendMessage(
                DailySettings.color(
                        config.getString("messages." + key, key).replace("Citizens", "ZNPCsPlus")));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("livre")) {
            if (sender instanceof Player player && player.hasPermission(USE))
                player.openBook(commandBook(player));
            return true;
        }
        if (args.length == 0) {
            sendLink(sender);
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            message(sender, "interdit");
            return true;
        }
        if (args.length != 2
                || !args[0].equalsIgnoreCase("pnj")
                || !(args[1].equalsIgnoreCase("spawn") || args[1].equalsIgnoreCase("remove"))) {
            sender.sendMessage(DailySettings.color("&e/wiki &7ou &e/wiki pnj <spawn|remove>"));
            return true;
        }
        if (!npc.available()) {
            message(sender, "citizens-absent");
            return true;
        }
        try {
            if (args[1].equalsIgnoreCase("spawn")) {
                if (!(sender instanceof Player player)) message(sender, "joueur-uniquement");
                else message(sender, npc.spawn(player.getLocation()) ? "cree" : "existant");
            } else message(sender, npc.remove() ? "supprime" : "absent");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Gestion du PNJ Wiki impossible", e);
            message(sender, "erreur");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        List<String> options =
                args.length == 1
                        ? (sender.hasPermission(ADMIN) ? List.of("livre", "pnj") : List.of("livre"))
                        : args.length == 2
                                        && args[0].equalsIgnoreCase("pnj")
                                        && sender.hasPermission(ADMIN)
                                ? List.of("spawn", "remove")
                                : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(prefix)).toList();
    }

    public void shutdown() {
        npc.shutdown();
    }

    private org.bukkit.inventory.ItemStack commandBook(Player player) {
        var book = new org.bukkit.inventory.ItemStack(org.bukkit.Material.WRITTEN_BOOK);
        var meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        meta.title(Component.text("Commandes ClickHunter"));
        meta.author(Component.text("ClickHunter"));
        meta.addPages(
                Component.text(
                                "Commandes ClickHunter\n\n"
                                    + "Les commandes accessibles avec tes permissions sont"
                                    + " présentées dans les pages suivantes.\n\n")
                        .append(clickableLink()));
        plugin.getDescription().getCommands().keySet().stream()
                .sorted()
                .forEach(
                        name -> {
                            var command = plugin.getCommand(name);
                            if (command == null
                                    || !command.testPermissionSilent(player)
                                    || meta.getPageCount() >= 100) return;
                            meta.addPages(
                                    Component.text("/" + name, NamedTextColor.DARK_GREEN)
                                            .append(
                                                    Component.text(
                                                            "\n\n"
                                                                    + command.getDescription()
                                                                    + "\n\n"
                                                                    + command.getUsage()
                                                                            .replace(
                                                                                    "<command>",
                                                                                    name),
                                                            NamedTextColor.BLACK))
                                            .clickEvent(
                                                    ClickEvent.suggestCommand("/" + name + " ")));
                        });
        book.setItemMeta(meta);
        return book;
    }
}
