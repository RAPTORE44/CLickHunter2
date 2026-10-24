package fr.cliker.clikerv2.rewards;

import fr.cliker.clikerv2.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Adaptations limitées aux API de persistance/écriture non implémentées par MockBukkit. */
public final class ClaimTestFactory {
    public static ClaimService create(JavaPlugin plugin, DatabaseManager database) {
        return new ClaimService(plugin, database, player -> {}, (player, items) -> {
            for (int i = 0; i < items.length; i++) player.getInventory().setItem(i, items[i]);
        });
    }
}
