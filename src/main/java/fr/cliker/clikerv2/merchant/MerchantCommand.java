package fr.cliker.clikerv2.merchant;

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
import java.util.Locale;

public final class MerchantCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUB = List.of("spawn", "remove", "list", "skin");

    private final MerchantManager manager;

    public MerchantCommand(MerchantManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) {
            sender.sendMessage(
                    Component.text("Permission administrateur requise.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    Component.text("Commande réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("skin")
                && MobTier.parse(args[1]) != null) {
            try {
                manager.skin(MobTier.parse(args[1]).id(), args[2]);
                player.sendMessage("Apparence des marchands sauvegardée.");
            } catch (RuntimeException error) {
                player.sendMessage("Apparence non sauvegardée : " + error.getMessage());
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            List<String> mobIds = manager.listMobIds();
            if (mobIds.isEmpty()) {
                sender.sendMessage(Component.text("Aucun marchand posé.", NamedTextColor.GRAY));
                return true;
            }
            sender.sendMessage(
                    Component.text(
                            "Marchands : " + String.join(", ", mobIds), NamedTextColor.YELLOW));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
            boolean removed = manager.removeNearest(player.getLocation());
            sender.sendMessage(
                    removed
                            ? Component.text(
                                    "Marchand le plus proche supprimé.", NamedTextColor.GREEN)
                            : Component.text("Aucun marchand à proximité.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            if (!manager.available()) {
                sender.sendMessage("ZNPCsPlus 2.0.0 est nécessaire pour créer un PNJ.");
                return true;
            }
            MobTier tier = MobTier.parse(args[1]);
            if (tier == null) {
                sender.sendMessage(Component.text("Mob inconnu : " + args[1], NamedTextColor.RED));
                return true;
            }
            manager.addNpc(tier.id(), player.getLocation());
            sender.sendMessage(
                    Component.text(
                            "✦ Marchand " + tier.label() + " posé ici.", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(
                Component.text(
                        "/" + label + " <spawn <mob>|remove|list|skin <mob> <pseudo|villager>>",
                        NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) options.addAll(SUB);
        else if (args.length == 2
                && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("skin")))
            options.addAll(MobTier.byId().keySet());
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(current));
        return options;
    }
}
