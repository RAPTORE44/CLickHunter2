package fr.cliker.clikerv2.world;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Jour permanent sur les mondes existants et futurs, sans tâche périodique. */
public final class PermanentDay implements Listener {
    public static final long NOON = 6000L;

    public PermanentDay(JavaPlugin plugin) {
        plugin.getServer().getWorlds().forEach(PermanentDay::apply);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void apply(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        if (world.getTime() != NOON) world.setTime(NOON);
    }

    @EventHandler
    public void load(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void skip(TimeSkipEvent event) {
        // Préserver les compteurs de jours, mais empêcher les lits/commandes de rétablir la nuit.
        long target =
                Math.floorMod(
                        event.getWorld().getTime() + Math.floorMod(event.getSkipAmount(), 24000L),
                        24000L);
        if (target != NOON) event.setCancelled(true);
    }
}
