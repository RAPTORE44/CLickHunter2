package fr.cliker.clikerv2.combat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Donne l'épée du dresseur (+25 dégâts) à la connexion si le joueur n'en a pas déjà une dans son
 * inventaire.
 */
public final class StarterSwordListener implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)) return;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null
                    && item.hasItemMeta()
                    && item.getItemMeta()
                            .getPersistentDataContainer()
                            .has(SwordCommand.SWORD_KEY, PersistentDataType.BYTE)) return;
        }
        fr.cliker.clikerv2.rewards.ItemDelivery.give(player, SwordCommand.create());
    }
}
