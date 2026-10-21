package fr.cliker.clikerv2.economy;

import fr.cliker.clikerv2.EconomyService;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

public final class CurrencyCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUB_ADMIN = List.of("balance", "give", "take", "set");

    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final CurrencyType currency;

    public CurrencyCommand(JavaPlugin plugin, EconomyService economy, CurrencyType currency) {
        this.plugin = plugin;
        this.economy = economy;
        this.currency = currency;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.money.use")) { sender.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED)); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("balance") || args[0].equalsIgnoreCase("bal")) return balance(sender, args);
        if (!sender.hasPermission("cliker.money.admin")) { sender.sendMessage(Component.text("Permission administrateur requise.", NamedTextColor.RED)); return true; }
        if (args.length != 3 || !(args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take") || args[0].equalsIgnoreCase("set"))) {
            sender.sendMessage(Component.text("/" + label + " <balance|give|take|set> [joueur] [montant]", NamedTextColor.YELLOW)); return true;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(args[1]);
        Long amount = parseAmount(args[2]);
        if (amount == null || amount < 0) { sender.sendMessage(Component.text("Montant invalide.", NamedTextColor.RED)); return true; }
        var operation = args[0].equalsIgnoreCase("set") ? economy.set(player.getUniqueId(), name(player), currency, amount) : economy.change(player.getUniqueId(), name(player), currency, args[0].equalsIgnoreCase("take") ? -amount : amount);
        operation.whenComplete((balance, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) { sender.sendMessage(Component.text(message(error), NamedTextColor.RED)); return; }
            sender.sendMessage(balanceLine(balance));
        }));
        return true;
    }

    private boolean balance(CommandSender sender, String[] args) {
        OfflinePlayer player = args.length > 1 ? Bukkit.getOfflinePlayer(args[1]) : sender instanceof OfflinePlayer offline ? offline : null;
        if (player == null) { sender.sendMessage(Component.text("Console : indiquez un joueur.", NamedTextColor.RED)); return true; }
        UUID uuid = player.getUniqueId();
        economy.getBalances(uuid).whenComplete((balances, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) { sender.sendMessage(Component.text(message(error), NamedTextColor.RED)); return; }
            long value = switch (currency) {
                case ECUS -> balances.ecus();
                case GEMMES_SOMBRES -> balances.gemmesSombres();
                case CENDRES -> balances.cendres();
            };
            sender.sendMessage(balanceLine(value));
        }));
        return true;
    }

    private Component balanceLine(long value) {
        NamedTextColor accent = switch (currency) {
            case ECUS -> NamedTextColor.GOLD;
            case GEMMES_SOMBRES -> NamedTextColor.DARK_PURPLE;
            case CENDRES -> NamedTextColor.GRAY;
        };
        return Component.text(currency.symbol() + " ", accent, TextDecoration.BOLD)
                .append(Component.text(currency.displayName() + " ", NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                .append(Component.text(currency.format(value), accent, TextDecoration.BOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("cliker.money.admin")) options.addAll(SUB_ADMIN);
            else if (sender.hasPermission("cliker.money.use")) options.add("balance");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("balance") || args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take") || args[0].equalsIgnoreCase("set"))) {
            if (args[0].equalsIgnoreCase("balance") ? sender.hasPermission("cliker.money.use") : sender.hasPermission("cliker.money.admin")) {
                for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) options.add(online.getName());
            }
        } else if (args.length == 3 && sender.hasPermission("cliker.money.admin") && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take") || args[0].equalsIgnoreCase("set"))) {
            options.add("100");
        }
        String current = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(java.util.Locale.ROOT).startsWith(current));
        return options;
    }

    private static Long parseAmount(String value) { try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return null; } }
    private static String name(OfflinePlayer player) { return player.getName() == null ? player.getUniqueId().toString() : player.getName(); }
    private static String message(Throwable error) { Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error; return cause.getMessage() == null ? "Erreur interne." : cause.getMessage(); }
}
