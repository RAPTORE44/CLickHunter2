package fr.cliker.clikerv2.upgrade;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class UpgradeCommand implements CommandExecutor, org.bukkit.command.TabCompleter {
    private final UpgradeMenu menu;

    public UpgradeCommand(UpgradeMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    Component.text("Commande réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 2
                && args[0].equalsIgnoreCase("set")
                && (args[1].equalsIgnoreCase("block") || args[1].equalsIgnoreCase("bloc"))) {
            player.performCommand("upgradebloc set");
            return true;
        }
        if (args.length != 0) {
            player.sendMessage("§e/upgrade ou /upgrade set block");
            return true;
        }
        menu.open(player);
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.services.admin")) return java.util.List.of();
        var values =
                args.length == 1
                        ? java.util.List.of("set")
                        : args.length == 2 && args[0].equalsIgnoreCase("set")
                                ? java.util.List.of("block", "bloc")
                                : java.util.List.<String>of();
        String prefix =
                args.length == 0 ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
