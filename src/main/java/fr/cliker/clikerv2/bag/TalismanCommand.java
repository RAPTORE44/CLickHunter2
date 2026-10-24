package fr.cliker.clikerv2.bag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /talisman treasure <joueur> : donne un Treasure Talisman (admin). */
public final class TalismanCommand implements CommandExecutor, TabCompleter {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    public TalismanCommand(org.bukkit.plugin.java.JavaPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) { sender.sendMessage(Component.text("Permission administrateur requise.", NamedTextColor.RED)); return true; }
        if (args.length != 2 || !args[0].equalsIgnoreCase("treasure")) {
            sender.sendMessage(Component.text("/" + label + " treasure <joueur>", NamedTextColor.YELLOW));
            return true;
        }
        var delivery = Bukkit.getServicesManager().load(fr.cliker.clikerv2.rewards.ClaimService.class);
        if (delivery == null) { sender.sendMessage(Component.text("Service de récompenses indisponible.", NamedTextColor.RED)); return true; }
        String name = args[1]; ItemStack item = TalismanItem.createTreasureTalisman();
        delivery.resolve(name).whenComplete((uuid, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || uuid == null) { sender.sendMessage(Component.text("Joueur inconnu : il doit avoir déjà rejoint le serveur.", NamedTextColor.RED)); return; }
            delivery.give(uuid, name, item);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) online.sendMessage(Component.text("✦ Treasure Talisman attribué ! Consulte /claim si nécessaire.", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Treasure Talisman attribué à " + name + ".", NamedTextColor.GREEN));
        }));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) options.add("treasure");
        else if (args.length == 2 && args[0].equalsIgnoreCase("treasure")) {
            for (Player online : Bukkit.getOnlinePlayers()) options.add(online.getName());
        }
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(current));
        return options;
    }
}
