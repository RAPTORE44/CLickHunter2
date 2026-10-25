package fr.cliker.clikerv2.bag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BagCommand implements CommandExecutor, org.bukkit.command.TabCompleter {
    private final BagMenu menu;

    public BagCommand(BagMenu menu) {
        this.menu = menu;
    }

    @Override
    public java.util.List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 && "gestion".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))
                ? java.util.List.of("gestion")
                : java.util.List.of();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    Component.text("Commande réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("gestion")) menu.manage(player);
        else menu.open(player);
        return true;
    }
}
