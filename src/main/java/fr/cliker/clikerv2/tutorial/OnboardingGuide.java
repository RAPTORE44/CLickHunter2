package fr.cliker.clikerv2.tutorial;

import fr.cliker.clikerv2.features.HuntKillEvent;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.outpost.WorldInventories;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Progression basée sur les actions confirmées, sans tâche périodique ni récompense exploitable.
 */
public final class OnboardingGuide implements Listener, CommandExecutor, TabCompleter {
    public enum Action {
        MENU,
        HUNT,
        SELL,
        UPGRADE,
        DAILY
    }

    private final JavaPlugin plugin;
    private final NamespacedKey steps, kills, hidden;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Settings config;

    public OnboardingGuide(JavaPlugin plugin) {
        this.plugin = plugin;
        steps = new NamespacedKey(plugin, "guide_steps");
        kills = new NamespacedKey(plugin, "guide_kills");
        hidden = new NamespacedKey(plugin, "guide_hidden");
        config = new Settings(plugin, "progression/tutoriel.yml");
    }

    public void start() {
        Bukkit.getServicesManager()
                .register(OnboardingGuide.class, this, plugin, ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        var cmd = plugin.getCommand("tutoriel");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    public static void record(Player p, Action action) {
        var guide = Bukkit.getServicesManager().load(OnboardingGuide.class);
        if (guide != null) guide.complete(p, action);
    }

    public int progress(Player p) {
        return p.getPersistentDataContainer().getOrDefault(steps, PersistentDataType.INTEGER, 0);
    }

    public void complete(Player p, Action action) {
        int old = progress(p), value = old | (1 << action.ordinal());
        if (old == value) return;
        p.getPersistentDataContainer().set(steps, PersistentDataType.INTEGER, value);
        refresh(p);
        if (value == (1 << Action.values().length) - 1)
            p.sendMessage("§a✓ Guide terminé ! Bonne aventure sur ClickHunter.");
    }

    private void remove(Player p) {
        var bar = bars.remove(p.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    public void refresh(Player p) {
        int flags = progress(p), next = 0;
        while (next < Action.values().length && (flags & (1 << next)) != 0) next++;
        if (next == Action.values().length
                || p.getPersistentDataContainer().has(hidden)
                || WorldInventories.isolated(p)) {
            remove(p);
            return;
        }
        String text =
                config.yaml.getString(
                        "etapes." + Action.values()[next].name().toLowerCase(Locale.ROOT),
                        Action.values()[next].name());
        var bar =
                bars.computeIfAbsent(
                        p.getUniqueId(),
                        id -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_6));
        bar.setTitle("✦ Guide " + (next + 1) + "/5 — " + text);
        bar.setProgress(Integer.bitCount(flags & 31) / 5.0);
        bar.addPlayer(p);
    }

    @EventHandler
    public void hunt(HuntKillEvent e) {
        var p = Bukkit.getPlayer(e.owner);
        if (p == null || e.boss) return;
        int n = p.getPersistentDataContainer().getOrDefault(kills, PersistentDataType.INTEGER, 0);
        if (n >= 5) return;
        p.getPersistentDataContainer().set(kills, PersistentDataType.INTEGER, n + 1);
        if (n + 1 >= 5) complete(p, Action.HUNT);
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        refresh(e.getPlayer());
    }

    @EventHandler
    public void world(PlayerChangedWorldEvent e) {
        refresh(e.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        remove(e.getPlayer());
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.hasPermission("cliker.tutoriel")) return true;
        String mode = args.length == 0 ? "reprendre" : args[0].toLowerCase(Locale.ROOT);
        if (mode.equals("masquer"))
            p.getPersistentDataContainer().set(hidden, PersistentDataType.BYTE, (byte) 1);
        else if (mode.equals("reprendre") || mode.equals("recommencer")) {
            p.getPersistentDataContainer().remove(hidden);
            if (mode.equals("recommencer")) {
                p.getPersistentDataContainer().remove(steps);
                p.getPersistentDataContainer().remove(kills);
            }
        } else {
            p.sendMessage("§e/tutoriel reprendre|masquer|recommencer");
            return true;
        }
        refresh(p);
        return true;
    }

    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] args) {
        return args.length == 1
                ? List.of("reprendre", "masquer", "recommencer").stream()
                        .filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT)))
                        .toList()
                : List.of();
    }

    public void shutdown() {
        bars.values().forEach(BossBar::removeAll);
        bars.clear();
        HandlerList.unregisterAll(this);
        Bukkit.getServicesManager().unregister(OnboardingGuide.class, this);
    }
}
