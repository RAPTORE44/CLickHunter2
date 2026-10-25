package fr.cliker.clikerv2.ui;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.*;

/** Une lecture asynchrone ne doit pas ouvrir un ancien menu après un changement de contexte. */
public final class ViewRequests implements Listener {
    public record Request(UUID owner, UUID world, Inventory previous) {}

    private final Map<UUID, Request> pending = new HashMap<>();

    public ViewRequests(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public Request begin(Player player) {
        var request =
                new Request(
                        player.getUniqueId(),
                        player.getWorld().getUID(),
                        player.getOpenInventory().getTopInventory());
        pending.put(player.getUniqueId(), request);
        return request;
    }

    public boolean complete(Player player, Request request) {
        boolean same = pending.get(request.owner()) == request;
        if (same) pending.remove(request.owner());
        return same
                && player.isOnline()
                && player.getWorld().getUID().equals(request.world())
                && player.getOpenInventory().getTopInventory() == request.previous();
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
