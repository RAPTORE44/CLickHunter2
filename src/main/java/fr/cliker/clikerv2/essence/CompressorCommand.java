package fr.cliker.clikerv2.essence;

import fr.cliker.clikerv2.MobTier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class CompressorCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Commande réservée aux joueurs.", NamedTextColor.RED)); return true; }
        if (args.length != 1) { sender.sendMessage(Component.text("Usage : /compresseur <mob>", NamedTextColor.RED)); return true; }

        MobTier tier = MobTier.parse(args[0]);
        if (tier == null) { sender.sendMessage(Component.text("Mob inconnu : " + args[0], NamedTextColor.RED)); return true; }

        fr.cliker.clikerv2.rewards.ItemDelivery.give(player, CompressorItem.create(tier));
        player.sendMessage(Component.text("✦ Compresseur " + tier.label() + " donné !", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = new ArrayList<>(MobTier.byId().keySet());
        return options.stream().filter(id -> id.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
    }
}
