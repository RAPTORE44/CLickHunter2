package fr.cliker.clikerv2.discord;

import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.npc.CitizensNpc;

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

public final class DiscordModule implements CommandExecutor, TabCompleter {
    public static final String USE = "cliker.discord.use";
    public static final String ADMIN = "cliker.discord.admin";
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration config;
    private final CitizensNpc npc;
    private final String link;

    public DiscordModule(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "discord.yml");
        if (!file.exists()) plugin.saveResource("discord.yml", false);
        config = fr.cliker.clikerv2.config.YamlFiles.load(file);
        link = config.getString("lien", "https://discord.gg/WSwBwpnQTa");
        URI uri = URI.create(link);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Le lien Discord doit être une URL HTTPS valide");
        npc =
                new CitizensNpc(
                        plugin,
                        config,
                        this::save,
                        this::sendLink,
                        "clikerv2-discord-npc",
                        "ClickHunterDiscord");
    }

    public void start() {
        Objects.requireNonNull(plugin.getCommand("discord")).setExecutor(this);
        plugin.getCommand("discord").setTabCompleter(this);
        npc.start();
    }

    private void save() {
        try {
            config.save(file);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Sauvegarde du PNJ Discord impossible", e);
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
                                                "&aClique pour rejoindre le Discord."))));
    }

    public void sendLink(CommandSender sender) {
        if (!sender.hasPermission(USE)) {
            message(sender, "interdit");
            return;
        }
        sender.sendMessage(
                DailySettings.color(config.getString("messages.titre", "&9Discord ClickHunter")));
        sender.sendMessage(clickableLink());
    }

    private void message(CommandSender sender, String key) {
        sender.sendMessage(DailySettings.color(config.getString("messages." + key, key)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
            sender.sendMessage(
                    DailySettings.color("&e/discord &7ou &e/discord pnj <spawn|remove>"));
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
            plugin.getLogger().log(Level.SEVERE, "Gestion du PNJ Discord impossible", e);
            message(sender, "erreur");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) return List.of();
        List<String> options =
                args.length == 1
                        ? List.of("pnj")
                        : args.length == 2 && args[0].equalsIgnoreCase("pnj")
                                ? List.of("spawn", "remove")
                                : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(prefix)).toList();
    }

    public void shutdown() {
        npc.shutdown();
    }
}
