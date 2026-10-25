package fr.cliker.clikerv2.mobs;

import fr.cliker.clikerv2.Numbers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MobKillsCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final MobKillStore store;

    public MobKillsCommand(JavaPlugin plugin, MobKillStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) {
            sender.sendMessage(
                    Component.text("Permission administrateur requise.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(
                    Component.text(
                            "/" + label + " give <joueur> <montant>", NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(
                    Component.text("Joueur introuvable ou hors ligne.", NamedTextColor.RED));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(Component.text("Montant invalide.", NamedTextColor.RED));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(Component.text("Montant invalide.", NamedTextColor.RED));
            return true;
        }

        store.add(target.getUniqueId(), amount)
                .whenComplete(
                        (result, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (error != null) {
                                                sender.sendMessage(
                                                        Component.text(
                                                                "Erreur : " + error.getMessage(),
                                                                NamedTextColor.RED));
                                                return;
                                            }
                                            sender.sendMessage(
                                                    Component.text(
                                                            "+"
                                                                    + Numbers.compact(amount)
                                                                    + " kills de mobs ajoutés à "
                                                                    + target.getName()
                                                                    + ".",
                                                            NamedTextColor.GREEN));
                                        }));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) options.add("give");
        else if (args.length == 2)
            for (Player online : Bukkit.getOnlinePlayers()) options.add(online.getName());
        else if (args.length == 3) options.add("1000");
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(current));
        return options;
    }
}
