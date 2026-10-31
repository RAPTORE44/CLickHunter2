package fr.cliker.clikerv2.ui;

import fr.cliker.clikerv2.runtime.PlayerSessions;
import fr.cliker.clikerv2.runtime.ServerTasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.LongSupplier;

/** Coalescer les paquets d'une interaction monde ; ne limite pas la navigation dans un menu. */
public final class MenuOpenGuard implements Listener {
    private final Plugin plugin;
    private final LongSupplier clock;
    private final Map<UUID, Object> pending = new HashMap<>();
    private final Map<UUID, Long> acceptedAt = new HashMap<>();
    private boolean stopped;

    MenuOpenGuard(Plugin plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer()
                .getServicesManager()
                .register(
                        MenuOpenGuard.class,
                        this,
                        plugin,
                        org.bukkit.plugin.ServicePriority.Normal);
    }

    public static void request(Plugin plugin, Player player, Runnable action) {
        if (!plugin.isEnabled()) return;
        if (!Bukkit.isPrimaryThread()) {
            ServerTasks.run(plugin, () -> request(plugin, player, action));
            return;
        }
        MenuOpenGuard guard = null;
        for (var registration : Bukkit.getServicesManager().getRegistrations(MenuOpenGuard.class)) {
            if (registration.getProvider().plugin == plugin) {
                guard = registration.getProvider();
                break;
            }
        }
        if (guard == null) guard = new MenuOpenGuard(plugin, System::nanoTime);
        guard.submit(player, action);
    }

    void submit(Player player, Runnable action) {
        if (stopped || !PlayerSessions.isCurrent(player)) return;
        UUID id = player.getUniqueId();
        Inventory before = player.getOpenInventory().getTopInventory();
        // Un clic monde tardif ne doit jamais rouvrir le menu déjà affiché.
        if (before != null
                && before.getType() != InventoryType.CRAFTING
                && before.getType() != InventoryType.CREATIVE) return;
        long now = clock.getAsLong();
        Long last = acceptedAt.get(id);
        if (pending.containsKey(id) || (last != null && now - last < 250_000_000L)) return;
        Object token = new Object();
        pending.put(id, token);
        acceptedAt.put(id, now);
        UUID world = player.getWorld().getUID();
        ServerTasks.run(
                plugin,
                () -> {
                    if (stopped || pending.get(id) != token) return;
                    pending.remove(id);
                    if (PlayerSessions.isCurrent(player)
                            && player.getWorld().getUID().equals(world)
                            && player.getOpenInventory().getTopInventory() == before) action.run();
                });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void opened(InventoryOpenEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void closed(InventoryCloseEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
        acceptedAt.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        stopped = true;
        pending.clear();
        acceptedAt.clear();
        HandlerList.unregisterAll(this);
        Bukkit.getServicesManager().unregister(MenuOpenGuard.class, this);
    }

    public static void shutdown(Plugin plugin) {
        for (var registration :
                List.copyOf(Bukkit.getServicesManager().getRegistrations(MenuOpenGuard.class)))
            if (registration.getProvider().plugin == plugin) registration.getProvider().shutdown();
    }
}
