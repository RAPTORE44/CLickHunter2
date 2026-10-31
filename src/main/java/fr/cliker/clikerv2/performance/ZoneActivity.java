package fr.cliker.clikerv2.performance;

import fr.cliker.clikerv2.features.Settings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Index spatial des joueurs : ne charge ni chunk ni entité pour vérifier une zone. */
public final class ZoneActivity implements org.bukkit.event.Listener {
    private static ZoneActivity current;

    private record Position(double x, double y, double z) {}

    private final JavaPlugin plugin;
    private final int radius, interval;
    private final boolean enabled;
    private final boolean releaseSpawnChunks;
    private final Map<UUID, Map<Long, List<Position>>> worlds = new HashMap<>();
    private BukkitTask task;

    public ZoneActivity(JavaPlugin plugin) {
        this.plugin = plugin;
        var config = new Settings(plugin, "performance/zones.yml").yaml;
        radius = Math.max(16, Math.min(192, config.getInt("distance-activation", 64)));
        interval = Math.max(10, Math.min(100, config.getInt("actualisation-ticks", 20)));
        enabled = config.getBoolean("mise-en-veille", true);
        releaseSpawnChunks = config.getBoolean("liberer-chunks-spawn", true);
    }

    public void start() {
        current = this;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getWorlds().forEach(this::configure);
        refresh();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, interval, interval);
    }

    private void configure(org.bukkit.World world) {
        if (enabled && releaseSpawnChunks)
            world.setGameRule(org.bukkit.GameRule.SPAWN_CHUNK_RADIUS, 0);
    }

    @org.bukkit.event.EventHandler
    public void load(org.bukkit.event.world.WorldLoadEvent event) {
        configure(event.getWorld());
    }

    private int cell(double n) {
        return (int) Math.floor(n / radius);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public void refresh() {
        worlds.clear();
        for (var p : Bukkit.getOnlinePlayers()) {
            if (p.isDead()) continue;
            var at = p.getLocation();
            worlds.computeIfAbsent(at.getWorld().getUID(), k -> new HashMap<>())
                    .computeIfAbsent(key(cell(at.getX()), cell(at.getZ())), k -> new ArrayList<>())
                    .add(new Position(at.getX(), at.getY(), at.getZ()));
        }
    }

    private boolean near(Location at) {
        if (!enabled) return true;
        var cells = worlds.get(at.getWorld().getUID());
        if (cells == null) return false;
        int x = cell(at.getX()), z = cell(at.getZ());
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (var p : cells.getOrDefault(key(x + dx, z + dz), List.of())) {
                    double a = p.x - at.getX(), b = p.y - at.getY(), c = p.z - at.getZ();
                    if (a * a + b * b + c * c <= radius * radius) return true;
                }
        return false;
    }

    public static boolean active(Location location) {
        return current == null || current.near(location);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        worlds.clear();
        org.bukkit.event.HandlerList.unregisterAll(this);
        if (current == this) current = null;
    }
}
