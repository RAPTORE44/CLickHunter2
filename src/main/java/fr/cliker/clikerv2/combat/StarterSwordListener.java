package fr.cliker.clikerv2.combat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Donne l'épée du dresseur (+5 dégâts) à la connexion si le joueur n'en a pas déjà une dans son
 * inventaire.
 */
public final class StarterSwordListener implements Listener {
    private final org.bukkit.plugin.java.JavaPlugin plugin;

    public StarterSwordListener(org.bukkit.plugin.java.JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private void refresh(org.bukkit.entity.Player player, boolean retry) {
        if (!fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(player)
                || fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)) return;
        var delivery =
                org.bukkit.Bukkit.getServicesManager()
                        .load(fr.cliker.clikerv2.rewards.ClaimService.class);
        if (delivery != null && delivery.isDelivering(player.getUniqueId())) {
            if (retry)
                org.bukkit.Bukkit.getScheduler()
                        .runTaskLater(plugin, () -> refresh(player, true), 20);
            return;
        }
        var inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            var item = inv.getItem(slot);
            if (SwordCommand.refresh(item)) inv.setItem(slot, item);
        }
    }

    @EventHandler
    public void held(org.bukkit.event.player.PlayerItemHeldEvent event) {
        refresh(event.getPlayer(), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(player, true), 1);
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
