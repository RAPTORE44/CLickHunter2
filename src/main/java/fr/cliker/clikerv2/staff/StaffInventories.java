package fr.cliker.clikerv2.staff;

import fr.cliker.clikerv2.outpost.WorldInventories;
import fr.cliker.clikerv2.rewards.ClaimService;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Vue native de l’inventaire réel : aucun miroir différé pouvant écraser les changements du joueur.
 */
public final class StaffInventories implements Listener, CommandExecutor, TabCompleter {
    private record View(Player viewer, Player target) {}

    private final JavaPlugin plugin;
    private final Map<UUID, View> views = new HashMap<>();

    public StaffInventories(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var c = plugin.getCommand("inv");
        if (c != null) {
            c.setExecutor(this);
            c.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean allowed(View v) {
        var delivery = Bukkit.getServicesManager().load(ClaimService.class);
        return v.viewer.isOnline()
                && v.target.isOnline()
                && v.viewer.hasPermission("cliker.staff.inv")
                && !WorldInventories.isolated(v.viewer)
                && !WorldInventories.isolated(v.target)
                && (delivery == null
                        || (!delivery.isDelivering(v.viewer.getUniqueId())
                                && !delivery.isDelivering(v.target.getUniqueId())));
    }

    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (!p.hasPermission("cliker.staff.inv")) {
            p.sendMessage("§cPermission requise : cliker.staff.inv");
            return true;
        }
        if (args.length != 1) {
            p.sendMessage("§e/inv <joueur en ligne>");
            return true;
        }
        var target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            p.sendMessage("§cCe joueur doit être en ligne.");
            return true;
        }
        var view = new View(p, target);
        if (!allowed(view)) {
            p.sendMessage(
                    "§cInventaire indisponible : attends la fin des livraisons et quitte"
                            + " l’avant-poste.");
            return true;
        }
        p.openInventory(target.getInventory());
        views.put(p.getUniqueId(), view);
        p.sendMessage(
                "§eInventaire de " + target.getName() + " : les transferts et dépôts sont réels.");
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        var v = views.get(e.getWhoClicked().getUniqueId());
        if (v != null && !allowed(v)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) {
        var v = views.get(e.getWhoClicked().getUniqueId());
        if (v != null && !allowed(v)) e.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        views.remove(e.getPlayer().getUniqueId());
    }

    private void closeFor(Player p) {
        for (var v : new ArrayList<>(views.values()))
            if (v.viewer == p || v.target == p) {
                views.remove(v.viewer.getUniqueId());
                v.viewer.closeInventory();
            }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        closeFor(e.getPlayer());
    }

    @EventHandler
    public void world(PlayerChangedWorldEvent e) {
        closeFor(e.getPlayer());
    }

    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        return args.length == 1 && s.hasPermission("cliker.staff.inv")
                ? Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(
                                n ->
                                        n.toLowerCase(Locale.ROOT)
                                                .startsWith(args[0].toLowerCase(Locale.ROOT)))
                        .toList()
                : List.of();
    }

    public void shutdown() {
        for (var v : new ArrayList<>(views.values())) v.viewer.closeInventory();
        views.clear();
        HandlerList.unregisterAll(this);
    }
}
