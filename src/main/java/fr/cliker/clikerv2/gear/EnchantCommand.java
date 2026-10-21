package fr.cliker.clikerv2.gear;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class EnchantCommand implements CommandExecutor {
    private final EnchantMenu menu;

    public EnchantCommand(EnchantMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        menu.open(player);
        return true;
    }
}
