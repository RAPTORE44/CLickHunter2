package fr.cliker.clikerv2.mobs;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;

import org.bukkit.*;
import org.bukkit.entity.Chicken;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Les recettes et les succès personnalisés restent disponibles ; les exploits vanilla sont coupés.
 */
public final class VanillaRewardsListener implements Listener {
    public VanillaRewardsListener(JavaPlugin plugin) {
        for (var world : plugin.getServer().getWorlds()) quiet(world);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void quiet(World world) {
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
    }

    @EventHandler
    public void loaded(WorldLoadEvent event) {
        quiet(event.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void egg(EntityDropItemEvent event) {
        if (event.getEntity() instanceof Chicken
                && event.getItemDrop().getItemStack().getType() == Material.EGG)
            event.setCancelled(true);
    }

    public static boolean blocked(NamespacedKey key) {
        return key.getNamespace().equals("minecraft") && !key.getKey().startsWith("recipes/");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void criterion(PlayerAdvancementCriterionGrantEvent event) {
        if (blocked(event.getAdvancement().getKey())) event.setCancelled(true);
    }

    @EventHandler
    public void announcement(PlayerAdvancementDoneEvent event) {
        if (blocked(event.getAdvancement().getKey())) event.message(null);
    }
}
