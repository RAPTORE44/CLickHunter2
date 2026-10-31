package fr.cliker.clikerv2.mobs;

import fr.cliker.clikerv2.hologram.HologramManager;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Fait réapparaître tous les Golems (mobs boss) toutes les 30 min, avec annonce chat et barre de
 * boss les 5 dernières minutes.
 */
public final class BossEventManager implements Listener {
    private static final long INTERVAL_SECONDS = 30 * 60;
    private static final long WARNING_SECONDS = 5 * 60;

    private final JavaPlugin plugin;
    private final CustomMobManager mobManager;
    private final BossBar bossBar;
    private HologramManager hologramManager;
    private long remainingSeconds;
    private long nextSpawnMillis;
    private final org.bukkit.configuration.file.YamlConfiguration settings;
    private final java.io.File settingsFile;
    private final long interval;
    private final long warning;
    private org.bukkit.scheduler.BukkitTask task;
    private final java.util.Set<String> zoneHolos = new java.util.HashSet<>();

    public BossEventManager(JavaPlugin plugin, CustomMobManager mobManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        settingsFile = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "boss.yml");
        if (!settingsFile.exists())
            fr.cliker.clikerv2.config.ConfigFiles.install(plugin, "boss.yml");
        settings = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(settingsFile);
        settings.addDefault("annonces.actives", true);
        settings.addDefault(
                "annonces.reapparition", "&c&l⚔ Tous les boss de zone ont réapparu ! ⚔");
        settings.addDefault(
                "annonces.mort", "&c⚔ Le boss &e{boss} &ca été vaincu par &a{joueur}&c !");
        settings.options().copyDefaults(true);
        interval = settings.getLong("intervalle-secondes", INTERVAL_SECONDS);
        warning = settings.getLong("avertissement-secondes", WARNING_SECONDS);
        if (interval < 1 || interval > 31_536_000 || warning < 0 || warning > interval)
            throw new IllegalArgumentException("Intervalle boss invalide");
        nextSpawnMillis =
                settings.getLong("prochain-spawn-ms", System.currentTimeMillis() + interval * 1000);
        saveTimer();
        remainingSeconds = Math.max(0, (nextSpawnMillis - System.currentTimeMillis() + 999) / 1000);
        this.bossBar = Bukkit.createBossBar("Prochains boss", BarColor.RED, BarStyle.SOLID);
    }

    /** Branché après coup car HologramManager est construit plus tard dans onEnable. */
    public void setHologramManager(HologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        if (mobManager.bossSpawnIds().isEmpty()) {
            hideBossBar();
            clearZoneHolograms();
            return;
        }

        remainingSeconds = Math.max(0, (nextSpawnMillis - System.currentTimeMillis() + 999) / 1000);
        if (remainingSeconds <= 0) {
            spawnWave();
            nextSpawnMillis = System.currentTimeMillis() + interval * 1000;
            saveTimer();
            remainingSeconds = interval;
            return;
        }
        if (remainingSeconds <= warning) {
            updateBossBar();
        } else if (!bossBar.getPlayers().isEmpty()) {
            hideBossBar();
        }
        updateHologram();
    }

    public void spawnNow() {
        spawnWave();
        nextSpawnMillis = System.currentTimeMillis() + interval * 1000;
        saveTimer();
        remainingSeconds = interval;
    }

    private void spawnWave() {
        mobManager.triggerBossWave();
        hideBossBar();
        if (settings.getBoolean("annonces.actives", true)) {
            Component message =
                    fr.cliker.clikerv2.daily.DailySettings.color(
                            settings.getString(
                                            "annonces.reapparition",
                                            "&c&l⚔ Tous les boss de zone ont réapparu ! ⚔")
                                    .replace(
                                            "{nombre}",
                                            Integer.toString(mobManager.bossSpawnIds().size())));
            for (Player player : plugin.getServer().getOnlinePlayers()) player.sendMessage(message);
        }
        if (hologramManager != null) {
            hologramManager.updateBossTimer("⚔ Boss ⚔", List.of("&c&lLES BOSS SONT ARRIVÉS !"));
        }
    }

    private void saveTimer() {
        settings.set("prochain-spawn-ms", nextSpawnMillis);
        try {
            settings.save(settingsFile);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Sauvegarde de l'horloge boss impossible", error);
        }
    }

    private void updateHologram() {
        updateZoneHolograms();
        if (hologramManager == null) return;
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        hologramManager.updateBossTimer(
                "⚔ Prochains boss ⚔",
                List.of("&e" + minutes + "m " + String.format("%02d", seconds) + "s"));
    }

    private void updateZoneHolograms() {
        var provider = fr.cliker.clikerv2.hologram.SharedHolograms.service();
        if (provider == null || !provider.available()) return;
        var wanted = new java.util.HashSet<String>();
        for (var entry : mobManager.bossPoints().entrySet()) {
            var at = entry.getValue();
            if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
            String id = "zone-boss-timer:" + entry.getKey();
            wanted.add(id);
            // Les boss de zone sont placés vers l’est : six blocs devant, trois au-dessus.
            at.add(6, 3, 0);
            var lines =
                    List.of(
                            "&c&l⚔ " + mobManager.bossLabel(entry.getKey()) + " ⚔",
                            mobManager.bossAlive(entry.getKey())
                                    ? "&aDisponible • Prêt au combat"
                                    : "&7Boss vaincu",
                            "&7Prochaine réapparition : &e"
                                    + (remainingSeconds / 60)
                                    + "m "
                                    + String.format("%02d", remainingSeconds % 60)
                                    + "s");
            var holo = provider.get(id);
            if (holo == null || !holo.isValid()) provider.create(id, at, lines, null, false);
            else {
                holo.setLocation(at);
                holo.setLines(lines);
            }
        }
        for (String id : new java.util.HashSet<>(zoneHolos))
            if (!wanted.contains(id)) provider.remove(id);
        zoneHolos.clear();
        zoneHolos.addAll(wanted);
    }

    private void clearZoneHolograms() {
        var provider = fr.cliker.clikerv2.hologram.SharedHolograms.service();
        if (provider != null) zoneHolos.forEach(provider::remove);
        zoneHolos.clear();
    }

    private void updateBossBar() {
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        bossBar.setTitle(
                "⚔ Prochains boss dans " + minutes + "m " + String.format("%02d", seconds) + "s");
        bossBar.setProgress(
                Math.max(0.0, Math.min(1.0, (double) remainingSeconds / Math.max(1, warning))));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!bossBar.getPlayers().contains(player)) bossBar.addPlayer(player);
        }
    }

    private void hideBossBar() {
        for (Player player : new ArrayList<>(bossBar.getPlayers())) bossBar.removePlayer(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!mobManager.bossSpawnIds().isEmpty()
                && remainingSeconds > 0
                && remainingSeconds <= warning) bossBar.addPlayer(event.getPlayer());
    }

    @EventHandler
    public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
        bossBar.removePlayer(event.getPlayer());
    }

    public void shutdown() {
        clearZoneHolograms();
        if (task != null) task.cancel();
        hideBossBar();
        org.bukkit.event.HandlerList.unregisterAll(this);
    }
}
