package fr.cliker.clikerv2.rewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Point d'entrée partagé par les récompenses et restitutions du plugin. */
public final class ItemDelivery {
    private ItemDelivery() {}
    public static void give(Player player, ItemStack item) {
        ClaimService service = Bukkit.getServicesManager().load(ClaimService.class);
        if (service == null) throw new IllegalStateException("Service /claim indisponible");
        service.give(player.getUniqueId(), player.getName(), item);
    }
}
