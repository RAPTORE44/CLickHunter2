package fr.cliker.clikerv2.spawn;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Point de spawn du serveur, persisté en BDD. */
public final class SpawnManager {
    private final JavaPlugin plugin;
    private final SpawnStore store;
    private Location spawn;

    public SpawnManager(JavaPlugin plugin, SpawnStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void load() {
        store.load().whenComplete((location, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) { plugin.getLogger().severe("Impossible de charger le spawn : " + error.getMessage()); return; }
            spawn = location;
        }));
    }

    public boolean hasSpawn() { return spawn != null; }

    public Location spawn() { return spawn == null ? null : spawn.clone(); }

    public boolean teleport(Player player) {
        if (spawn == null) return false;
        player.teleport(spawn);
        return true;
    }

    public void setSpawn(Location location) {
        spawn = location.clone();
        store.save(spawn);
    }
}
