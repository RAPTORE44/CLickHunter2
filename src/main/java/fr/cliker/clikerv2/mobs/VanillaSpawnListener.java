package fr.cliker.clikerv2.mobs;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Préserve les apparitions CUSTOM des mobs ClickHunter et des PNJ. */
public final class VanillaSpawnListener implements Listener {
    private final JavaPlugin plugin;

    public VanillaSpawnListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (plugin.getConfig().getBoolean("mobs.disable-vanilla-spawns", true)
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }
}
