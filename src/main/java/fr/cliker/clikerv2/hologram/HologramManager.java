package fr.cliker.clikerv2.hologram;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.level.PlayerLevelStore;
import fr.cliker.clikerv2.mobs.MobKillStore;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Classements (top 10) affichés via des hologrammes du fournisseur partagé : niveau, kills totaux,
 * et soldes actuels des 3 monnaies.
 */
public final class HologramManager {
    public enum Type {
        LEVEL,
        KILLS,
        ECUS,
        GEMMES,
        CENDRES,
        BOSS_TIMER
    }

    private final org.bukkit.configuration.file.YamlConfiguration appearance;
    private final JavaPlugin plugin;
    private final HologramStore store;
    private final PlayerLevelStore levelStore;
    private final MobKillStore killStore;
    private final EconomyService economy;
    private final boolean available;

    private final Map<String, Type> holograms = new HashMap<>();
    private final Map<String, List<String>> rendered = new HashMap<>();
    private final java.util.Set<Type> inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public HologramManager(
            JavaPlugin plugin,
            HologramStore store,
            PlayerLevelStore levelStore,
            MobKillStore killStore,
            EconomyService economy) {
        this.plugin = plugin;
        org.bukkit.configuration.file.YamlConfiguration loaded;
        try {
            loaded =
                    new fr.cliker.clikerv2.features.Settings(plugin, "affichage/classements.yml")
                            .yaml;
        } catch (RuntimeException error) {
            plugin.getLogger()
                    .warning(
                            "affichage/classements.yml invalide : styles par défaut utilisés. "
                                    + error.getMessage());
            loaded = new org.bukkit.configuration.file.YamlConfiguration();
        }
        appearance = loaded;
        this.store = store;
        this.levelStore = levelStore;
        this.killStore = killStore;
        this.economy = economy;
        this.available = SharedHolograms.service() != null && SharedHolograms.service().available();
    }

    private Location presentation(Location base) {
        Location result = base.clone().add(0, appearance.getDouble("decalage-vertical", -3), 0);
        result.setYaw(-90);
        result.setPitch(0);
        return result;
    }

    public boolean isAvailable() {
        return available;
    }

    public void load() {
        if (!available) return;
        store.loadAll()
                .whenComplete(
                        (points, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .severe(
                                                                "Impossible de charger les"
                                                                        + " hologrammes : "
                                                                        + error.getMessage());
                                                return;
                                            }
                                            for (HologramStore.HologramPoint point : points) {
                                                Type type = parseType(point.type());
                                                if (type == null) continue;
                                                holograms.put(point.id(), type);
                                                String name = hologramName(point.id());
                                                SharedHolograms.service()
                                                        .removeLegacyLeaderboard(name);
                                                SharedHolograms.service()
                                                        .create(
                                                                name,
                                                                presentation(point.location()),
                                                                List.of(),
                                                                null,
                                                                true);
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
        SharedHolograms.service()
                .create(hologramName(id), presentation(location), List.of(), null, true);
        store.save(id, type.name(), location);
        refresh(id, type);
    }

    public boolean removeNearest(Location near) {
        if (!available) return false;
        String nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (String id : holograms.keySet()) {
            var hologram = SharedHolograms.service().get(hologramName(id));
            if (hologram == null || !hologram.getLocation().getWorld().equals(near.getWorld()))
                continue;
            double distance = hologram.getLocation().distanceSquared(near);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = id;
            }
        }
        if (nearest == null) return false;
        SharedHolograms.service().remove(hologramName(nearest));
        holograms.remove(nearest);
        rendered.remove(nearest);
        store.removeById(nearest);
        return true;
    }

    private void refreshAll() {
        java.util.Set<Type> seen = java.util.EnumSet.noneOf(Type.class);
        for (Map.Entry<String, Type> entry : holograms.entrySet()) {
            if (seen.add(entry.getValue())) refresh(entry.getKey(), entry.getValue());
        }
    }

    private void refresh(String id, Type type) {
        if (!inFlight.add(type)) return;
        switch (type) {
            case LEVEL ->
                    levelStore
                            .top(10)
                            .whenComplete(
                                    (entries, error) -> {
                                        inFlight.remove(type);
                                        if (error == null)
                                            applyLines(
                                                    type,
                                                    "★ Top Niveaux ★",
                                                    entries.stream()
                                                            .map(
                                                                    e ->
                                                                            (Object[])
                                                                                    new Object[] {
                                                                                        e
                                                                                                .playerName(),
                                                                                        (long)
                                                                                                e
                                                                                                        .level()
                                                                                    })
                                                            .toList());
                                    });
            case KILLS ->
                    killStore
                            .top(10)
                            .whenComplete(
                                    (entries, error) -> {
                                        inFlight.remove(type);
                                        if (error == null)
                                            applyLines(
                                                    type,
                                                    "☠ Top Kills ★",
                                                    entries.stream()
                                                            .map(
                                                                    e ->
                                                                            (Object[])
                                                                                    new Object[] {
                                                                                        e
                                                                                                .playerName(),
                                                                                        e.total()
                                                                                    })
                                                            .toList());
                                    });
            case ECUS ->
                    economy.topBalances(CurrencyType.ECUS, 10)
                            .whenComplete(
                                    (entries, error) -> {
                                        inFlight.remove(type);
                                        if (error == null)
                                            applyLines(
                                                    type,
                                                    "✦ Top Écus ★",
                                                    entries.stream()
                                                            .map(
                                                                    e ->
                                                                            (Object[])
                                                                                    new Object[] {
                                                                                        e
                                                                                                .playerName(),
                                                                                        e.amount()
                                                                                    })
                                                            .toList());
                                    });
            case GEMMES ->
                    economy.topBalances(CurrencyType.GEMMES_SOMBRES, 10)
                            .whenComplete(
                                    (entries, error) -> {
                                        inFlight.remove(type);
                                        if (error == null)
                                            applyLines(
                                                    type,
                                                    "◆ Top Gemmes Sombres ★",
                                                    entries.stream()
                                                            .map(
                                                                    e ->
                                                                            (Object[])
                                                                                    new Object[] {
                                                                                        e
                                                                                                .playerName(),
                                                                                        e.amount()
                                                                                                / 10
                                                                                    })
                                                            .toList());
                                    });
            case CENDRES ->
                    economy.topBalances(CurrencyType.CENDRES, 10)
                            .whenComplete(
                                    (entries, error) -> {
                                        inFlight.remove(type);
                                        if (error == null)
                                            applyLines(
                                                    type,
                                                    "❀ Top Cendres ★",
                                                    entries.stream()
                                                            .map(
                                                                    e ->
                                                                            (Object[])
                                                                                    new Object[] {
                                                                                        e
                                                                                                .playerName(),
                                                                                        e.amount()
                                                                                    })
                                                            .toList());
                                    });
            case BOSS_TIMER -> {
                inFlight.remove(type);
                applyBossTimerLines(id, lastBossTimerLines);
            }
        }
    }

    private List<String> lastBossTimerLines =
            List.of("&d&l⚔ Prochains boss ⚔", "&7", "&8En attente...");

    /**
     * Appelé par BossEventManager chaque seconde pour garder le compte à rebours précis,
     * indépendamment du cycle de refresh des classements.
     */
    public void updateBossTimer(String title, List<String> details) {
        if (!available) return;
        List<String> lines = new ArrayList<>();
        lines.add(appearance.getString("boss-entete", "&d&l{titre}").replace("{titre}", title));
        lines.add(appearance.getString("separateur", "&7"));
        lines.addAll(details);
        lastBossTimerLines = lines;
        for (Map.Entry<String, Type> entry : holograms.entrySet()) {
            if (entry.getValue() == Type.BOSS_TIMER) applyBossTimerLines(entry.getKey(), lines);
        }
    }

    private void applyBossTimerLines(String id, List<String> lines) {
        var hologram = SharedHolograms.service().get(hologramName(id));
        if (hologram == null) return;
        if (!lines.equals(rendered.get(id))) {
            hologram.setLines(lines);
            rendered.put(id, List.copyOf(lines));
        }
    }

    private void applyLines(Type type, String title, List<Object[]> entries) {
        if (!plugin.isEnabled()) return;
        fr.cliker.clikerv2.runtime.ServerTasks.run(
                plugin,
                () -> {
                    List<String> lines =
                            leaderboardLines(
                                    appearance.getString("titres." + type.name(), title), entries);
                    lines.set(
                            0,
                            appearance
                                    .getString("entete", "&d&l{titre}")
                                    .replace(
                                            "{titre}",
                                            appearance.getString("titres." + type.name(), title)));
                    lines.set(1, appearance.getString("separateur", "&7"));
                    for (int rank = 0; rank < 10; rank++) {
                        String row =
                                rank < entries.size()
                                        ? appearance
                                                .getString(
                                                        "ligne",
                                                        "&e#{rang} &f{joueur} &7- &b{valeur}")
                                                .replace(
                                                        "{joueur}",
                                                        String.valueOf(entries.get(rank)[0]))
                                                .replace(
                                                        "{valeur}",
                                                        Numbers.compact(
                                                                (Long) entries.get(rank)[1]))
                                        : appearance.getString(
                                                "ligne-vide", "&e#{rang}                &7- &b0");
                        lines.set(rank + 2, row.replace("{rang}", Integer.toString(rank + 1)));
                    }
                    for (var entry : holograms.entrySet()) {
                        if (entry.getValue() != type || lines.equals(rendered.get(entry.getKey())))
                            continue;
                        var hologram = SharedHolograms.service().get(hologramName(entry.getKey()));
                        if (hologram == null) continue;
                        hologram.setLines(lines);
                        rendered.put(entry.getKey(), List.copyOf(lines));
                    }
                });
    }

    private static List<String> leaderboardLines(String title, List<Object[]> entries) {
        List<String> lines = new ArrayList<>();
        lines.add("&d&l" + title);
        lines.add("&7");
        for (int i = 0; i < 10; i++) {
            if (i < entries.size()) {
                Object[] entry = entries.get(i);
                lines.add(
                        "&e#"
                                + (i + 1)
                                + " &f"
                                + entry[0]
                                + " &7- &b"
                                + Numbers.compact((Long) entry[1]));
            } else {
                lines.add("&e#" + (i + 1) + "                &7- &b0");
            }
        }
        return lines;
    }

    private String hologramName(String id) {
        return "cliker_leaderboard_" + id;
    }

    private Type parseType(String raw) {
        try {
            return Type.valueOf(raw);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
