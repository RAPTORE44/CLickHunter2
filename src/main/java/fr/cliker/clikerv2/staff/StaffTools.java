package fr.cliker.clikerv2.staff;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Commandes contrôlées séparément ; cibler autrui exige une permission supplémentaire. */
public final class StaffTools implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final NamespacedKey god;

    public StaffTools(JavaPlugin plugin) {
        this.plugin = plugin;
        god = new NamespacedKey(plugin, "staff_god");
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (String name : List.of("flyspeed", "god", "heal", "feed")) {
            var c = plugin.getCommand(name);
            if (c != null) {
                c.setExecutor(this);
                c.setTabCompleter(this);
            }
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("cliker.staff." + name)) {
            sender.sendMessage("§cPermission manquante : cliker.staff." + name);
            return true;
        }
        boolean speed = name.equals("flyspeed");
        int targetIndex = speed ? 1 : 0;
        if ((speed && args.length < 1) || args.length > targetIndex + 1) {
            sender.sendMessage("§e/" + name + (speed ? " <0-10>" : "") + " [joueur]");
            return true;
        }
        Player target =
                args.length > targetIndex
                        ? Bukkit.getPlayerExact(args[targetIndex])
                        : sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage("§cJoueur en ligne requis.");
            return true;
        }
        if (target != sender && !sender.hasPermission("cliker.staff.others")) {
            sender.sendMessage("§cPermission requise : cliker.staff.others");
            return true;
        }
        switch (name) {
            case "flyspeed" -> {
                double n;
                try {
                    n = Double.parseDouble(args[0].replace(',', '.'));
                } catch (NumberFormatException e) {
                    n = Double.NaN;
                }
                if (!Double.isFinite(n) || n < 0 || n > 10) {
                    sender.sendMessage("§cVitesse comprise entre 0 et 10.");
                    return true;
                }
                target.setFlySpeed((float) (n / 10));
                sender.sendMessage("§aVitesse de vol de " + target.getName() + " : " + n + "/10.");
            }
            case "god" -> {
                boolean enabled = !target.getPersistentDataContainer().has(god);
                if (enabled)
                    target.getPersistentDataContainer().set(god, PersistentDataType.BYTE, (byte) 1);
                else target.getPersistentDataContainer().remove(god);
                target.sendMessage(
                        enabled ? "§aInvulnérabilité activée." : "§eInvulnérabilité désactivée.");
                if (target != sender)
                    sender.sendMessage(
                            "§aInvulnérabilité de "
                                    + target.getName()
                                    + " : "
                                    + (enabled ? "activée" : "désactivée"));
            }
            case "heal" -> {
                if (target.isDead()) {
                    sender.sendMessage("§cCe joueur doit d’abord réapparaître.");
                    return true;
                }
                target.setHealth(
                        Objects.requireNonNull(target.getAttribute(Attribute.MAX_HEALTH))
                                .getValue());
                target.setFireTicks(0);
                target.sendMessage("§aTes points de vie sont restaurés.");
            }
            case "feed" -> {
                target.setFoodLevel(20);
                target.setSaturation(20);
                target.setExhaustion(0);
                target.sendMessage("§aTa faim est restaurée.");
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && p.getPersistentDataContainer().has(god))
            e.setCancelled(true);
    }

    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] args) {
        if (!s.hasPermission("cliker.staff." + c.getName())) return List.of();
        if (c.getName().equals("flyspeed") && args.length == 1)
            return List.of("1", "2", "5", "10").stream()
                    .filter(x -> x.startsWith(args[0]))
                    .toList();
        return s.hasPermission("cliker.staff.others")
                ? Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(
                                x ->
                                        x.toLowerCase(Locale.ROOT)
                                                .startsWith(
                                                        args[args.length - 1].toLowerCase(
                                                                Locale.ROOT)))
                        .toList()
                : List.of();
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
    }
}
