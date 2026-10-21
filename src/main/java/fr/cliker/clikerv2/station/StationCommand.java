package fr.cliker.clikerv2.station;

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

public final class StationCommand implements CommandExecutor, TabCompleter {
    private static final List<String> TYPES = List.of("enchant", "reforge", "rune");

    private final StationManager manager;

    public StationCommand(StationManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) { sender.sendMessage(Component.text("Permission administrateur requise.", NamedTextColor.RED)); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Commande réservée aux joueurs.", NamedTextColor.RED)); return true; }

        if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
            boolean removed = manager.removeNearest(player.getLocation());
            sender.sendMessage(removed
                    ? Component.text("Station la plus proche supprimée.", NamedTextColor.GREEN)
                    : Component.text("Aucune station à proximité.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            StationType type = parseType(args[1]);
            if (type == null) { sender.sendMessage(Component.text("Type inconnu. Utilisez : " + String.join(", ", TYPES), NamedTextColor.RED)); return true; }
            manager.addStation(type, player.getLocation().getBlock().getLocation());
            sender.sendMessage(Component.text("✦ " + type.label() + " posée ici.", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("/" + label + " <spawn <" + String.join("|", TYPES) + ">|remove>", NamedTextColor.YELLOW));
        return true;
    }

    private StationType parseType(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "enchant" -> StationType.ENCHANT;
            case "reforge" -> StationType.REFORGE;
            case "rune" -> StationType.RUNE;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) options.addAll(List.of("spawn", "remove"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) options.addAll(TYPES);
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(current));
        return options;
    }
}
