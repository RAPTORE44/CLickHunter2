package fr.cliker.clikerv2.spawn;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

/** Le cache ne confirme jamais une position dont la sauvegarde a échoué. */
public final class SpawnManager {
    private final JavaPlugin plugin;
    private final SpawnStore store;
    private Location spawn;
    private long version;

    public SpawnManager(JavaPlugin plugin, SpawnStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void load() {
        long requested = version;
        store.load()
                .whenComplete(
                        (location, error) -> {
                            if (!plugin.isEnabled()) return;
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        if (error != null) {
                                            plugin.getLogger()
                                                    .log(
                                                            java.util.logging.Level.SEVERE,
                                                            "Chargement du point d’accueil"
                                                                    + " impossible",
                                                            error);
                                            return;
                                        }
                                        if (version == requested) spawn = location;
                                    });
                        });
    }

    public boolean hasSpawn() {
        return spawn != null;
    }

    public Location spawn() {
        return spawn == null ? null : spawn.clone();
    }

    public boolean teleport(Player player) {
        return spawn != null && player.teleport(spawn);
    }

    public CompletableFuture<Boolean> setSpawn(Location location) {
        Location saved = location.clone();
        var result = new CompletableFuture<Boolean>();
        store.save(saved)
                .whenComplete(
                        (ignored, error) -> {
                            if (!plugin.isEnabled()) {
                                result.complete(false);
                                return;
                            }
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        if (error != null) {
                                            plugin.getLogger()
                                                    .log(
                                                            java.util.logging.Level.SEVERE,
                                                            "Sauvegarde du point d’accueil"
                                                                    + " impossible",
                                                            error);
                                            result.complete(false);
                                        } else {
                                            version++;
                                            spawn = saved;
                                            result.complete(true);
                                        }
                                    });
                        });
        return result;
    }
}
