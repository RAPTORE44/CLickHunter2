package fr.cliker.clikerv2.mobs;

import fr.cliker.clikerv2.hologram.HologramManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

/** Fait réapparaître tous les Golems (mobs boss) toutes les 30 min, avec annonce chat et barre de boss les 5 dernières minutes. */
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

    public BossEventManager(JavaPlugin plugin, CustomMobManager mobManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        settingsFile = new java.io.File(plugin.getDataFolder(), "boss.yml");
        if (!settingsFile.exists()) plugin.saveResource("boss.yml", false);
        settings = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(settingsFile);
        interval = settings.getLong("intervalle-secondes", INTERVAL_SECONDS);
        warning = settings.getLong("avertissement-secondes", WARNING_SECONDS);
        if (interval < 1 || interval > 31_536_000 || warning < 0 || warning > interval) throw new IllegalArgumentException("Intervalle boss invalide");
        nextSpawnMillis = settings.getLong("prochain-spawn-ms", System.currentTimeMillis() + interval * 1000);
        saveTimer();
        remainingSeconds = Math.max(0, (nextSpawnMillis - System.currentTimeMillis() + 999) / 1000);
        this.bossBar = Bukkit.createBossBar("Prochain Golem", BarColor.RED, BarStyle.SOLID);
    }

    /** Branché après coup car HologramManager est construit plus tard dans onEnable. */
    public void setHologramManager(HologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        if (mobManager.bossSpawnIds().isEmpty()) return;

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

    private void spawnWave() {
        mobManager.triggerBossWave();
        hideBossBar();
        Component message = Component.text("⚔ Un Golem est apparu sur la carte ! ⚔", NamedTextColor.DARK_RED, TextDecoration.BOLD);
        for (Player player : plugin.getServer().getOnlinePlayers()) player.sendMessage(message);
        if (hologramManager != null) {
            hologramManager.updateBossTimer("⚔ Golem ⚔", List.of("&c&lLES GOLEMS SONT ARRIVÉS !"));
        }
    }

    private void saveTimer() {
        settings.set("prochain-spawn-ms", nextSpawnMillis);
        try { settings.save(settingsFile); }
        catch (java.io.IOException error) { throw new IllegalStateException("Sauvegarde de l'horloge boss impossible", error); }
    }

    private void updateHologram() {
        if (hologramManager == null) return;
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        hologramManager.updateBossTimer("⚔ Prochain Golem ⚔",
                List.of("&e" + minutes + "m " + String.format("%02d", seconds) + "s"));
    }

    private void updateBossBar() {
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        bossBar.setTitle("⚔ Prochain Golem dans " + minutes + "m " + String.format("%02d", seconds) + "s");
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) remainingSeconds / Math.max(1, warning))));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!bossBar.getPlayers().contains(player)) bossBar.addPlayer(player);
        }
    }

    private void hideBossBar() {
        for (Player player : new ArrayList<>(bossBar.getPlayers())) bossBar.removePlayer(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (remainingSeconds > 0 && remainingSeconds <= warning) bossBar.addPlayer(event.getPlayer());
    }
}
