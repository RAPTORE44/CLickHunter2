package fr.cliker.clikerv2.ui;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.*;

/** Une lecture asynchrone ne doit pas ouvrir un ancien menu après un changement de contexte. */
public final class ViewRequests implements Listener {
    public record Request(Player session, UUID owner, UUID world, Inventory previous) {}

    private final Map<UUID, Request> pending = new HashMap<>();

    public ViewRequests(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public Request begin(Player player) {
        var request =
                new Request(
                        player,
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
                && request.session() == player
                && fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(player)
                && player.getWorld().getUID().equals(request.world())
                && player.getOpenInventory().getTopInventory() == request.previous();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void opened(org.bukkit.event.inventory.InventoryOpenEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void closed(org.bukkit.event.inventory.InventoryCloseEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
