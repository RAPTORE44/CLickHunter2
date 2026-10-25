package fr.cliker.clikerv2.display;

import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.prestige.PrestigeManager;
import fr.cliker.clikerv2.prestige.PrestigeType;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.math.*;
import java.util.*;

/** TAB Paper natif : aucun joueur fictif, aucune équipe de scoreboard remplacée. */
public final class TabListManager implements Listener {
    private final JavaPlugin plugin;
    private final PrestigeManager prestige;
    private final Settings settings;
    private final List<String> groups;
    private final Map<UUID, Component> originalNames = new HashMap<>();
    private BukkitTask task;

    public TabListManager(JavaPlugin plugin, PrestigeManager prestige) {
        this.plugin = plugin;
        this.prestige = prestige;
        settings = new Settings(plugin, "affichage/tab.yml");
        if (settings.yaml.getInt("liens.version", 0) < 1) {
            for (String key : List.of("liens.boutique", "liens.vote"))
                if (settings.yaml.getString(key, "").isBlank())
                    settings.yaml.set(key, "https://clickhunter.fr");
            settings.yaml.set("liens.version", 1);
            settings.save();
        }
        groups = List.copyOf(settings.yaml.getStringList("ordre-grades"));
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                this::refresh,
                                1,
                                Math.max(20, settings.yaml.getLong("actualisation-ticks", 40)));
    }

    private String group(Player p) {
        if (p.isOp() || p.hasPermission("cliker.tab.staff")) return "staff";
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                var user =
                        net.luckperms.api.LuckPermsProvider.get()
                                .getUserManager()
                                .getUser(p.getUniqueId());
                if (user != null) {
                    String group = user.getPrimaryGroup().toLowerCase(Locale.ROOT);
                    if (groups.contains(group)) return group;
                }
            } catch (IllegalStateException ignored) {
            }
        }
        for (String staff : List.of("admin", "moderateur", "modo", "helper", "staff"))
            if (p.hasPermission("group." + staff)) return staff;
        for (int i = groups.size() - 1; i >= 0; i--) {
            String group = groups.get(i);
            if (!group.equals("joueur") && p.hasPermission("group." + group)) return group;
        }
        return "joueur";
    }

    private int priority(Player p) {
        int rank = groups.indexOf(group(p));
        return rank < 0 ? groups.size() : rank;
    }

    private void refresh() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        originalNames.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        players.sort(
                Comparator.comparingInt(this::priority)
                        .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            player.setPlayerListOrder(players.size() - i);
            apply(player);
        }
    }

    public void apply(Player player) {
        originalNames.putIfAbsent(player.getUniqueId(), player.playerListName());
        player.sendPlayerListHeaderAndFooter(lines("entete"), lines("pied"));
        Component name = buildTabName(player);
        if (!name.equals(player.playerListName())) player.playerListName(name);
    }

    private Component lines(String key) {
        String shop = settings.yaml.getString("liens.boutique", "");
        String vote = settings.yaml.getString("liens.vote", "");
        return DailySettings.color(
                String.join("\n", settings.yaml.getStringList(key))
                        .replace("{connectes}", Integer.toString(Bukkit.getOnlinePlayers().size()))
                        .replace("{maximum}", Integer.toString(Bukkit.getMaxPlayers()))
                        .replace(
                                "{discord}",
                                settings.yaml.getString(
                                        "liens.discord", "https://discord.gg/WSwBwpnQTa"))
                        .replace("{boutique}", shop.isBlank() ? "Lien à configurer" : shop)
                        .replace(
                                "{vote}",
                                vote.isBlank() ? "Pense à voter pour le serveur !" : vote));
    }

    public static String compactLevel(int level) {
        if (level < 1000) return Integer.toString(Math.max(0, level));
        return BigDecimal.valueOf(level)
                        .divide(BigDecimal.valueOf(1000), 1, RoundingMode.DOWN)
                        .stripTrailingZeros()
                        .toPlainString()
                + "k";
    }

    static int colorBand(int level) {
        if (level <= 10) return Math.max(0, level - 1);
        if (level <= 100) return Math.min(9, (level - 1) / 10);
        if (level <= 1000) return Math.min(9, (level - 1) / 100);
        return 9;
    }

    public Component buildTabName(Player player) {
        String group = group(player);
        Component prefix = RankPrefix.of(player);
        if (prefix.equals(Component.empty()))
            prefix = DailySettings.color(settings.yaml.getString("grades." + group, "&7Joueur"));
        if (!prefix.equals(Component.empty())) prefix = prefix.append(Component.space());
        int level = player.getLevel(), p = prestige.getLevel(player.getUniqueId());
        List<String> colors = settings.yaml.getStringList("couleurs-niveau");
        String color =
                colors.isEmpty() ? "&a" : colors.get(Math.min(colorBand(level), colors.size() - 1));
        return prefix.append(
                DailySettings.color(
                        "&f"
                                + player.getName()
                                + " &8[&d"
                                + (p <= 0 ? "0" : PrestigeType.toRoman(p))
                                + " &8- "
                                + color
                                + compactLevel(level)
                                + "&8]"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (originalNames.containsKey(player.getUniqueId()))
                player.playerListName(originalNames.get(player.getUniqueId()));
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            player.setPlayerListOrder(0);
        }
        originalNames.clear();
        HandlerList.unregisterAll(this);
    }
}
