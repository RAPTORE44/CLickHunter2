package fr.cliker.clikerv2.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StatsCommand implements CommandExecutor {
    private final StatsMenu menu;

    public StatsCommand(StatsMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    Component.text("Commande réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }
        Player subject = args.length == 0 ? player : org.bukkit.Bukkit.getPlayerExact(args[0]);
        if (subject == null) player.sendMessage("§cJoueur hors ligne ou inconnu.");
        else menu.open(player, subject);
        return true;
    }
}
