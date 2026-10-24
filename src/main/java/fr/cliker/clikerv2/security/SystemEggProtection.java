package fr.cliker.clikerv2.security;

import fr.cliker.clikerv2.essence.EssenceItem;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/** Les essences en forme d'œuf sont une monnaie, jamais un œuf vanilla. */
public final class SystemEggProtection implements Listener {
    public static boolean protectedEgg(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SPAWN_EGG") && EssenceItem.tierOf(item) != null;
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void use(PlayerInteractEvent event) {
        if (protectedEgg(event.getItem())) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void entity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (protectedEgg(item)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void dispense(BlockDispenseEvent event) {
        if (protectedEgg(event.getItem())) event.setCancelled(true);
    }
}
