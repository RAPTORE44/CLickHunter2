package fr.cliker.clikerv2.spawn;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Fait respawn les joueurs au /spawn du serveur (si défini) plutôt qu'à leur lit ou au spawn du
 * monde.
 */
public final class RespawnListener implements Listener {
    private final SpawnManager spawnManager;

    public RespawnListener(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onRespawn(PlayerRespawnEvent event) {
        var spawn = spawnManager.spawn();
        if (spawn != null) event.setRespawnLocation(spawn);
    }
}
