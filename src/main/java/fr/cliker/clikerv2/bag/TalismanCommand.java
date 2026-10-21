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
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) { sender.sendMessage(Component.text("Permission administrateur requise.", NamedTextColor.RED)); return true; }
        if (args.length != 2 || !args[0].equalsIgnoreCase("treasure")) {
            sender.sendMessage(Component.text("/" + label + " treasure <joueur>", NamedTextColor.YELLOW));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(Component.text("Joueur introuvable ou hors ligne.", NamedTextColor.RED)); return true; }

        ItemStack item = TalismanItem.createTreasureTalisman();
        var leftover = target.getInventory().addItem(item);
        leftover.values().forEach(over -> target.getWorld().dropItemNaturally(target.getLocation(), over));
        target.sendMessage(Component.text("✦ Tu as reçu un Treasure Talisman ! Mets-le dans ton /sac.", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Treasure Talisman donné à " + target.getName() + ".", NamedTextColor.GREEN));
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
