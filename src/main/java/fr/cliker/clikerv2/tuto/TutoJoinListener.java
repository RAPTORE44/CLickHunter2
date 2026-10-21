package fr.cliker.clikerv2.tuto;

import fr.cliker.clikerv2.level.PlayerLevelStore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** À la toute première connexion d'un joueur (jamais rejoué avant) : message de bienvenue + ouverture du livre-guide. */
public final class TutoJoinListener implements Listener {
    private final JavaPlugin plugin;
    private final PlayerLevelStore levelStore;

    public TutoJoinListener(JavaPlugin plugin, PlayerLevelStore levelStore) {
        this.plugin = plugin;
        this.levelStore = levelStore;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPlayedBefore()) return;
        var player = event.getPlayer();

        levelStore.setLevel(player.getUniqueId(), player.getName(), player.getLevel())
                .thenCompose(v -> levelStore.countTotal())
                .whenComplete((total, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null || !player.isOnline()) return;
            Bukkit.broadcast(Component.text("✦ ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                    .append(Component.text(player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" rejoint Northblue Cliker pour la première fois ! (", NamedTextColor.GRAY))
                    .append(Component.text(total + "e joueur", NamedTextColor.GOLD))
                    .append(Component.text(")", NamedTextColor.GRAY)));

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) player.openBook(TutoBook.create());
            }, 40L);
        }));
    }
}
