package fr.cliker.clikerv2.hologram;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.level.PlayerLevelStore;
import fr.cliker.clikerv2.mobs.MobKillStore;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Classements (top 10) affichés via des hologrammes DecentHolograms : niveau, kills totaux, et soldes actuels des 3 monnaies. */
public final class HologramManager {
    public enum Type { LEVEL, KILLS, ECUS, GEMMES, CENDRES, BOSS_TIMER }

    private final JavaPlugin plugin;
    private final HologramStore store;
    private final PlayerLevelStore levelStore;
    private final MobKillStore killStore;
    private final EconomyService economy;
    private final boolean available;

    private final Map<String, Type> holograms = new HashMap<>();

    public HologramManager(JavaPlugin plugin, HologramStore store, PlayerLevelStore levelStore, MobKillStore killStore, EconomyService economy) {
        this.plugin = plugin;
        this.store = store;
        this.levelStore = levelStore;
        this.killStore = killStore;
        this.economy = economy;
        this.available = Bukkit.getPluginManager().getPlugin("DecentHolograms") != null;
    }

    public boolean isAvailable() { return available; }

    public void load() {
        if (!available) return;
        store.loadAll().whenComplete((points, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) { plugin.getLogger().severe("Impossible de charger les hologrammes : " + error.getMessage()); return; }
            for (HologramStore.HologramPoint point : points) {
                Type type = parseType(point.type());
                if (type == null) continue;
                holograms.put(point.id(), type);
                String name = hologramName(point.id());
                Hologram existing = DHAPI.getHologram(name);
                if (existing != null) {
                    // DecentHolograms garde ses hologrammes en mémoire/disque indépendamment de notre reload : on réutilise au lieu de dupliquer.
                    existing.setLocation(point.location());
                } else {
                    DHAPI.createHologram(name, point.location());
                }
            }
            refreshAll();
        }));
    }

    public void start() {
        if (!available) return;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, 100L, 200L);
    }

    public void addHologram(Type type, Location location) {
        if (!available) return;
        String id = UUID.randomUUID().toString();
        holograms.put(id, type);
        DHAPI.createHologram(hologramName(id), location);
        store.save(id, type.name(), location);
        refresh(id, type);
    }

    public boolean removeNearest(Location near) {
        if (!available) return false;
        String nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (String id : holograms.keySet()) {
            Hologram hologram = DHAPI.getHologram(hologramName(id));
            if (hologram == null || !hologram.getLocation().getWorld().equals(near.getWorld())) continue;
            double distance = hologram.getLocation().distanceSquared(near);
            if (distance < bestDistance) { bestDistance = distance; nearest = id; }
        }
        if (nearest == null) return false;
        DHAPI.removeHologram(hologramName(nearest));
        holograms.remove(nearest);
        store.removeById(nearest);
        return true;
    }

    private void refreshAll() {
        for (Map.Entry<String, Type> entry : new HashMap<>(holograms).entrySet()) refresh(entry.getKey(), entry.getValue());
    }

    private void refresh(String id, Type type) {
        switch (type) {
            case LEVEL -> levelStore.top(10).whenComplete((entries, error) -> {
                if (error == null) applyLines(id, "★ Top Niveaux ★", entries.stream()
                        .map(e -> (Object[]) new Object[]{e.playerName(), (long) e.level()}).toList());
            });
            case KILLS -> killStore.top(10).whenComplete((entries, error) -> {
                if (error == null) applyLines(id, "☠ Top Kills ★", entries.stream()
                        .map(e -> (Object[]) new Object[]{e.playerName(), e.total()}).toList());
            });
            case ECUS -> economy.topBalances(CurrencyType.ECUS, 10).whenComplete((entries, error) -> {
                if (error == null) applyLines(id, "✦ Top Écus ★", entries.stream()
                        .map(e -> (Object[]) new Object[]{e.playerName(), e.amount()}).toList());
            });
            case GEMMES -> economy.topBalances(CurrencyType.GEMMES_SOMBRES, 10).whenComplete((entries, error) -> {
                if (error == null) applyLines(id, "◆ Top Gemmes Sombres ★", entries.stream()
                        .map(e -> (Object[]) new Object[]{e.playerName(), e.amount() / 10}).toList());
            });
            case CENDRES -> economy.topBalances(CurrencyType.CENDRES, 10).whenComplete((entries, error) -> {
                if (error == null) applyLines(id, "❀ Top Cendres ★", entries.stream()
                        .map(e -> (Object[]) new Object[]{e.playerName(), e.amount()}).toList());
            });
            case BOSS_TIMER -> applyBossTimerLines(id, lastBossTimerLines);
        }
    }

    private List<String> lastBossTimerLines = List.of("&d&l⚔ Prochain Golem ⚔", "&7", "&8En attente...");

    /** Appelé par BossEventManager chaque seconde pour garder le compte à rebours précis, indépendamment du cycle de refresh des classements. */
    public void updateBossTimer(String title, List<String> details) {
        if (!available) return;
        List<String> lines = new ArrayList<>();
        lines.add("&d&l" + title);
        lines.add("&7");
        lines.addAll(details);
        lastBossTimerLines = lines;
        for (Map.Entry<String, Type> entry : holograms.entrySet()) {
            if (entry.getValue() == Type.BOSS_TIMER) applyBossTimerLines(entry.getKey(), lines);
        }
    }

    private void applyBossTimerLines(String id, List<String> lines) {
        Hologram hologram = DHAPI.getHologram(hologramName(id));
        if (hologram == null) return;
        DHAPI.setHologramLines(hologram, lines);
    }

    private void applyLines(String id, String title, List<Object[]> entries) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Hologram hologram = DHAPI.getHologram(hologramName(id));
            if (hologram == null) return;
            List<String> lines = new ArrayList<>();
            lines.add("&d&l" + title);
            lines.add("&7");
            if (entries.isEmpty()) {
                lines.add("&8Aucune donnée pour le moment.");
            } else {
                for (int i = 0; i < entries.size(); i++) {
                    Object[] entry = entries.get(i);
                    lines.add("&e#" + (i + 1) + " &f" + entry[0] + " &7- &b" + Numbers.compact((Long) entry[1]));
                }
            }
            DHAPI.setHologramLines(hologram, lines);
        });
    }

    private String hologramName(String id) { return "cliker_leaderboard_" + id; }

    private Type parseType(String raw) {
        try { return Type.valueOf(raw); } catch (IllegalArgumentException invalid) { return null; }
    }
}
