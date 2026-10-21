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
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
    private final Map<String, TextDisplay> leaderboardDisplays = new HashMap<>();
    private final Map<String, Location> leaderboardLocations = new HashMap<>();

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
                if (type != Type.BOSS_TIMER) {
                    // Les noms d'armor stands de DecentHolograms suivent la caméra.
                    // Remplacer aussi les anciens classements par un texte fixe.
                    if (DHAPI.getHologram(name) != null) DHAPI.removeHologram(name);
                    createLeaderboardDisplay(point.id(), point.location());
                    continue;
                }
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
        if (type == Type.BOSS_TIMER) DHAPI.createHologram(hologramName(id), location);
        else createLeaderboardDisplay(id, location);
        store.save(id, type.name(), location);
        refresh(id, type);
    }

    public boolean removeNearest(Location near) {
        if (!available) return false;
        String nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (String id : holograms.keySet()) {
            Hologram hologram = DHAPI.getHologram(hologramName(id));
            TextDisplay display = leaderboardDisplays.get(id);
            Location location = leaderboardLocations.get(id);
            if (location == null && hologram != null) location = hologram.getLocation();
            if (location == null || !location.getWorld().equals(near.getWorld())) continue;
            double distance = location.distanceSquared(near);
            if (distance < bestDistance) { bestDistance = distance; nearest = id; }
        }
        if (nearest == null) return false;
        TextDisplay display = leaderboardDisplays.remove(nearest);
        leaderboardLocations.remove(nearest);
        if (display != null) display.remove();
        else DHAPI.removeHologram(hologramName(nearest));
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
            TextDisplay display = leaderboardDisplays.get(id);
            Location location = leaderboardLocations.get(id);
            if (location == null) return;
            if (display == null || !display.isValid()) {
                // Les entités non persistantes disparaissent au déchargement du chunk.
                // Les recréer à son retour sans forcer le chargement des chunks éloignés.
                if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
                createLeaderboardDisplay(id, location);
                display = leaderboardDisplays.get(id);
            }
            List<String> lines = leaderboardLines(title, entries);
            display.text(LegacyComponentSerializer.legacyAmpersand().deserialize(String.join("\n", lines)));
        });
    }

    private static List<String> leaderboardLines(String title, List<Object[]> entries) {
        List<String> lines = new ArrayList<>();
        lines.add("&d&l" + title);
        lines.add("&7");
        for (int i = 0; i < 10; i++) {
            if (i < entries.size()) {
                Object[] entry = entries.get(i);
                lines.add("&e#" + (i + 1) + " &f" + entry[0] + " &7- &b" + Numbers.compact((Long) entry[1]));
            } else {
                lines.add("&e#" + (i + 1) + "                &7- &b0");
            }
        }
        return lines;
    }

    private void createLeaderboardDisplay(String id, Location location) {
        TextDisplay previous = leaderboardDisplays.remove(id);
        if (previous != null) previous.remove();
        Location facingEast = location.clone();
        // Les TextDisplay sont orientés vers le sud à yaw=0 ; -90 pointe vers l'est.
        facingEast.setYaw(-90.0f);
        facingEast.setPitch(0.0f);
        leaderboardLocations.put(id, facingEast.clone());
        TextDisplay display = location.getWorld().spawn(facingEast, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setAlignment(TextDisplay.TextAlignment.LEFT);
            entity.setLineWidth(1000);
            entity.setShadowed(true);
        });
        leaderboardDisplays.put(id, display);
    }

    public void shutdown() {
        for (TextDisplay display : leaderboardDisplays.values()) display.remove();
        leaderboardDisplays.clear();
        leaderboardLocations.clear();
        holograms.clear();
    }

    private String hologramName(String id) { return "cliker_leaderboard_" + id; }

    private Type parseType(String raw) {
        try { return Type.valueOf(raw); } catch (IllegalArgumentException invalid) { return null; }
    }
}
