package fr.cliker.clikerv2.display;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.mobs.MobKillStore;
import fr.cliker.clikerv2.prestige.PrestigeManager;
import fr.cliker.clikerv2.prestige.PrestigeType;

import io.papermc.paper.scoreboard.numbers.NumberFormat;

import net.kyori.adventure.text.Component;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class MoneyScoreboard implements Listener {
    private static final String OBJECTIVE_ID = "cliker_money";
    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final MobKillStore killStore;
    private final PrestigeManager prestige;
    private final List<String> lines;
    private final Component title;
    private final long interval;
    private final java.util.function.Consumer<Score> scoreFormatter;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private BukkitTask task;
    private volatile boolean stopped;

    /**
     * Une activation unique demandée par la nouvelle présentation ; les désactivations ultérieures
     * sont conservées.
     */
    public static void installStyle(JavaPlugin plugin) {
        File file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "scoreboard.yml");
        if (!file.exists()) fr.cliker.clikerv2.config.ConfigFiles.install(plugin, "scoreboard.yml");
        if (plugin.getConfig().getInt("displays.scoreboard.style-version", 0) >= 1) return;
        File config = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "config.yml");
        try {
            if (config.exists()) {
                var backup =
                        plugin.getDataFolder()
                                .toPath()
                                .resolve(
                                        "backups/config-scoreboard-"
                                                + System.currentTimeMillis()
                                                + ".yml");
                Files.createDirectories(backup.getParent());
                Files.copy(config.toPath(), backup);
            }
            plugin.getConfig().set("displays.scoreboard.enabled", true);
            plugin.getConfig().set("displays.scoreboard.style-version", 1);
            plugin.saveConfig();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(
                    "Sauvegarde de la configuration du scoreboard impossible", e);
        }
    }

    public MoneyScoreboard(
            JavaPlugin plugin,
            EconomyService economy,
            MobKillStore killStore,
            PrestigeManager prestige) {
        this(
                plugin,
                economy,
                killStore,
                prestige,
                score -> score.numberFormat(NumberFormat.blank()));
    }

    MoneyScoreboard(
            JavaPlugin plugin,
            EconomyService economy,
            MobKillStore killStore,
            PrestigeManager prestige,
            java.util.function.Consumer<Score> scoreFormatter) {
        this.scoreFormatter = scoreFormatter;
        this.plugin = plugin;
        this.economy = economy;
        this.killStore = killStore;
        this.prestige = prestige;
        var config =
                YamlConfiguration.loadConfiguration(
                        fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "scoreboard.yml"));
        lines =
                config.getStringList("lignes").stream()
                        .map(
                                line -> {
                                    String color =
                                            line.contains("{niveau}")
                                                    ? "&b"
                                                    : line.contains("{ecus}")
                                                            ? "&e"
                                                            : line.contains("{gemmes}") ? "&5" : "";
                                    return color.isEmpty()
                                            ? line
                                            : color + line.replaceAll("(?i)&[0-9a-f]", "");
                                })
                        .toList();
        if (lines.isEmpty() || lines.size() > 15)
            throw new IllegalArgumentException("Le scoreboard doit contenir entre 1 et 15 lignes");
        title = DailySettings.color(config.getString("titre", "&a&lClickHunter"));
        interval = Math.max(20L, config.getLong("actualisation-ticks", 40));
    }

    public void start() {
        task =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(plugin, this::refreshAll, 0L, interval);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("comfort.no-player-collisions", true))
            for (var session : sessions.values()) {
                var team = session.board.getTeam("cliker_no_push");
                if (team != null) team.addEntry(event.getPlayer().getName());
            }
        setup(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        for (var session : sessions.values()) {
            var team = session.board.getTeam("cliker_no_push");
            if (team != null) team.removeEntry(event.getPlayer().getName());
        }
    }

    private void refreshAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) refresh(player);
    }

    private Session setup(Player player) {
        var board = plugin.getServer().getScoreboardManager().getNewScoreboard();
        var objective = board.registerNewObjective(OBJECTIVE_ID, Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < lines.size(); i++) {
            var team = board.registerNewTeam("cliker_line_" + i);
            team.addEntry(entry(i));
            var score = objective.getScore(entry(i));
            score.setScore(lines.size() - i);
            scoreFormatter.accept(score);
        }
        if (plugin.getConfig().getBoolean("comfort.no-player-collisions", true)) {
            var team = board.registerNewTeam("cliker_no_push");
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            for (var online : plugin.getServer().getOnlinePlayers())
                team.addEntry(online.getName());
            team.addEntry(player.getName());
        }
        Session session = new Session(board, player.getScoreboard());
        sessions.put(player.getUniqueId(), session);
        player.setScoreboard(board);
        update(player, session, null, null);
        return session;
    }

    private void refresh(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) session = setup(player);
        // Ne pas remplacer en boucle un tableau repris par un autre plugin.
        if (player.getScoreboard() != session.board || !pending.add(player.getUniqueId())) return;
        Session target = session;
        economy.playerSnapshot(player.getUniqueId())
                .whenComplete(
                        (snapshot, error) -> {
                            if (stopped || !plugin.isEnabled()) return;
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        pending.remove(player.getUniqueId());
                                        if (!player.isOnline()
                                                || sessions.get(player.getUniqueId()) != target
                                                || player.getScoreboard() != target.board) return;
                                        if (error != null) {
                                            plugin.getLogger()
                                                    .log(
                                                            Level.WARNING,
                                                            "Actualisation du scoreboard"
                                                                    + " impossible",
                                                            error);
                                            return;
                                        }
                                        update(
                                                player,
                                                target,
                                                snapshot.balances(),
                                                snapshot.kills());
                                    });
                        });
    }

    public static Map<String, String> values(
            String player,
            int level,
            int prestige,
            EconomyService.Balances balances,
            Long kills,
            int online) {
        Map<String, String> values = new HashMap<>();
        values.put("joueur", player);
        values.put("niveau", Integer.toString(level));
        values.put("prestige", prestige <= 0 ? "0" : PrestigeType.toRoman(prestige));
        values.put("ecus", balances == null ? "…" : CurrencyType.ECUS.format(balances.ecus()));
        values.put(
                "gemmes",
                balances == null
                        ? "…"
                        : CurrencyType.GEMMES_SOMBRES.format(balances.gemmesSombres()));
        values.put(
                "cendres",
                balances == null ? "…" : CurrencyType.CENDRES.format(balances.cendres()));
        values.put("kills", kills == null ? "…" : String.format(Locale.FRANCE, "%,d", kills));
        values.put("connectes", Integer.toString(online));
        return values;
    }

    private void update(
            Player player, Session session, EconomyService.Balances balances, Long kills) {
        Map<String, String> values =
                values(
                        player.getName(),
                        player.getLevel(),
                        prestige.getLevel(player.getUniqueId()),
                        balances,
                        kills,
                        plugin.getServer().getOnlinePlayers().size());
        for (int i = 0; i < lines.size(); i++) {
            Component content = DailySettings.color(DailySettings.replace(lines.get(i), values));
            Team team = session.board.getTeam("cliker_line_" + i);
            if (!content.equals(team.prefix())) team.prefix(content);
        }
    }

    public void shutdown() {
        stopped = true;
        if (task != null) task.cancel();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Session session = sessions.get(player.getUniqueId());
            if (session != null && player.getScoreboard() == session.board)
                player.setScoreboard(session.previous);
        }
        sessions.clear();
        pending.clear();
        HandlerList.unregisterAll(this);
    }

    private static String entry(int index) {
        return "§" + Integer.toHexString(index) + "§r";
    }

    private record Snapshot(EconomyService.Balances balances, Long kills) {}

    private record Session(Scoreboard board, Scoreboard previous) {}
}
