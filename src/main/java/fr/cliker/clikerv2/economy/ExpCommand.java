package fr.cliker.clikerv2.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExpCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.xp.admin")) { sender.sendMessage(Component.text("Permission administrateur requise.", NamedTextColor.RED)); return true; }
        if (args.length != 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("/xp give <joueur> <montant>", NamedTextColor.YELLOW)); return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(Component.text("Joueur introuvable ou hors ligne.", NamedTextColor.RED)); return true; }
        Integer amount = parseAmount(args[2]);
        if (amount == null || amount <= 0) { sender.sendMessage(Component.text("Montant invalide.", NamedTextColor.RED)); return true; }

        target.giveExp(amount);
        sender.sendMessage(Component.text("✎ ", NamedTextColor.GREEN)
                .append(Component.text(amount + " XP", NamedTextColor.GREEN))
                .append(Component.text(" donnés à " + target.getName(), NamedTextColor.WHITE)));
        target.sendMessage(Component.text("✎ Vous avez reçu " + amount + " XP.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cliker.xp.admin")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) options.add("give");
        else if (args.length == 2) for (Player online : Bukkit.getOnlinePlayers()) options.add(online.getName());
        else if (args.length == 3) options.add("100");
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(current));
        return options;
    }

    private static Integer parseAmount(String value) { try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return null; } }
}
