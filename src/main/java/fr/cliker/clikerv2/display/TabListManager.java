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
    private final long animationStarted = System.currentTimeMillis();

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
        if (settings.yaml.getInt("animation.version", 0) < 1) {
            var footer = new ArrayList<String>();
            boolean replaced = false;
            for (String line : settings.yaml.getStringList("pied")) {
                if (line.contains("{boutique}")
                        || line.contains("{discord}")
                        || line.contains("{vote}")) {
                    if (!replaced) {
                        footer.add("{annonce}");
                        replaced = true;
                    }
                } else footer.add(line);
            }
            if (!replaced) footer.add("{annonce}");
            settings.yaml.set("pied", footer);
            settings.yaml.set("animation.version", 1);
            settings.yaml.set("animation.secondes-par-lien", 10);
            settings.save();
        }
        if (!settings.yaml.getStringList("pied").contains("{animation}")) {
            var footer = new ArrayList<>(settings.yaml.getStringList("pied"));
            footer.add(1, "{animation}");
            settings.yaml.set("pied", footer);
            settings.yaml.set("actualisation-ticks", 10);
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
                                Math.max(20, settings.yaml.getLong("actualisation-ticks", 20)));
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

    private Component batchHeader, batchFooter;

    private record Banner(Component header, Component footer) {}

    private final Map<UUID, Banner> banners = new HashMap<>();
    private final Map<UUID, Integer> orders = new HashMap<>();

    private void refresh() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        batchHeader = lines("entete");
        batchFooter = lines("pied");
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        originalNames.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        Map<Player, Integer> priorities = new HashMap<>();
        for (Player player : players) priorities.put(player, priority(player));
        players.sort(
                Comparator.comparingInt((Player p) -> priorities.get(p))
                        .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            int order = players.size() - i;
            if (!java.util.Objects.equals(orders.put(player.getUniqueId(), order), order))
                player.setPlayerListOrder(order);
            apply(player);
        }
        batchHeader = null;
        batchFooter = null;
    }

    public void apply(Player player) {
        originalNames.putIfAbsent(player.getUniqueId(), player.playerListName());
        var banner =
                new Banner(
                        batchHeader == null ? lines("entete") : batchHeader,
                        batchFooter == null ? lines("pied") : batchFooter);
        if (!banner.equals(banners.put(player.getUniqueId(), banner)))
            player.sendPlayerListHeaderAndFooter(banner.header(), banner.footer());
        Component name = buildTabName(player);
        if (!name.equals(player.playerListName())) player.playerListName(name);
    }

    private Component lines(String key) {
        String shop = settings.yaml.getString("liens.boutique", "");
        String vote = settings.yaml.getString("liens.vote", "");
        return DailySettings.color(
                String.join("\n", settings.yaml.getStringList(key))
                        .replace("{annonce}", rotatingLink())
                        .replace(
                                "{animation}",
                                "&b"
                                        + RgbText.marquee(
                                                "✦ Joue • Progresse • Découvre • Vote pour"
                                                        + " ClickHunter !",
                                                (System.currentTimeMillis() - animationStarted)
                                                        / 400,
                                                44))
                        .replace(
                                "&a&lClickHunter",
                                RgbText.gradient("ClickHunter", 0x35FF75, 0x25BBFF, true))
                        .replace(
                                "&fplay.clickhunter.fr",
                                RgbText.gradient("play.clickhunter.fr", 0x35FF75, 0x25BBFF, true))
                        .replace(
                                "&aplay.clickhunter.fr",
                                RgbText.gradient("play.clickhunter.fr", 0x35FF75, 0x25BBFF, true))
                        .replace("{connectes}", Integer.toString(Bukkit.getOnlinePlayers().size()))
                        .replace("{maximum}", Integer.toString(Bukkit.getMaxPlayers()))
                        .replace(
                                "{discord}",
                                settings.yaml.getString(
                                        "liens.discord", "https://discord.gg/WSwBwpnQTa"))
                        .replace(
                                "{site}",
                                settings.yaml.getString("liens.site", "https://clickhunter.fr"))
                        .replace("{boutique}", shop.isBlank() ? "Lien à configurer" : shop)
                        .replace(
                                "{vote}",
                                vote.isBlank() ? "Pense à voter pour le serveur !" : vote));
    }

    static int rotatingIndex(long elapsedMillis, int seconds, int count) {
        return count < 1
                ? 0
                : (int) (Math.max(0, elapsedMillis) / (Math.max(1, seconds) * 1000L) % count);
    }

    private String rotatingLink() {
        List<String> messages = settings.yaml.getStringList("animation.messages");
        if (messages.isEmpty())
            messages =
                    List.of(
                            "&aSite &8» &f{site}",
                            "&6Boutique &8» &f{boutique}",
                            "&9Discord &8» &f{discord}",
                            "&ePense à voter ! &8» &f{vote}");
        long elapsed = System.currentTimeMillis() - animationStarted;
        String[] colors = {"&a", "&b", "&e", "&d"};
        return colors[(int) (Math.max(0, elapsed) / 1000 % colors.length)]
                + "❯ "
                + messages.get(
                        rotatingIndex(
                                elapsed,
                                settings.yaml.getInt("animation.secondes-par-lien", 10),
                                messages.size()));
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

    @EventHandler
    public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        banners.remove(id);
        orders.remove(id);
        originalNames.remove(id);
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
        banners.clear();
        orders.clear();
        HandlerList.unregisterAll(this);
    }
}
