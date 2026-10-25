package fr.cliker.clikerv2.economy;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.rewards.ClaimService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.*;

/** Résolution locale des joueurs : aucune requête de profil réseau sur le thread serveur. */
public final class CurrencyCommand implements CommandExecutor, TabCompleter {
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
        if (!sender.hasPermission("cliker.money.use")) {
            message(sender, "Tu ne peux pas utiliser cette commande.", NamedTextColor.RED);
            return true;
        }
        String action = args.length == 0 ? "balance" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("bal")) action = "balance";
        boolean balance = action.equals("balance");
        if (!balance && !sender.hasPermission("cliker.money.admin")) {
            message(sender, "Permission administrateur requise.", NamedTextColor.RED);
            return true;
        }
        if ((balance && args.length > 2)
                || (!balance
                        && (args.length != 3
                                || !List.of("give", "take", "set").contains(action)))) {
            message(
                    sender,
                    "/"
                            + label
                            + " [balance [pseudo]] ou /"
                            + label
                            + " <give|take|set> <pseudo> <montant>",
                    NamedTextColor.YELLOW);
            return true;
        }
        long amount = 0;
        if (!balance) {
            try {
                amount = Long.parseLong(args[2]);
                if (amount < 0) throw new NumberFormatException();
            } catch (NumberFormatException error) {
                message(sender, "Indique un montant entier positif ou nul.", NamedTextColor.RED);
                return true;
            }
        }
        String name = args.length > 1 ? args[1] : sender instanceof Player p ? p.getName() : null;
        if (name == null) {
            message(sender, "Indique le pseudo du joueur.", NamedTextColor.YELLOW);
            return true;
        }
        if (name.length() > 64
                || name.chars()
                        .anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            message(sender, "Pseudo invalide.", NamedTextColor.RED);
            return true;
        }
        ClaimService profiles = Bukkit.getServicesManager().load(ClaimService.class);
        if (profiles == null) {
            message(
                    sender,
                    "Le service des joueurs est temporairement indisponible.",
                    NamedTextColor.RED);
            return true;
        }
        String operation = action;
        long value = amount;
        profiles.resolve(name)
                .thenCompose(
                        owner -> {
                            if (owner == null) return CompletableFuture.completedFuture(null);
                            if (balance) return economy.getBalance(owner, name, currency);
                            return operation.equals("set")
                                    ? economy.set(owner, name, currency, value)
                                    : economy.change(
                                            owner,
                                            name,
                                            currency,
                                            operation.equals("take") ? -value : value);
                        })
                .whenComplete(
                        (result, error) -> {
                            if (!plugin.isEnabled()) return;
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        if (error != null) {
                                            Throwable cause =
                                                    error instanceof CompletionException
                                                                    && error.getCause() != null
                                                            ? error.getCause()
                                                            : error;
                                            if (cause instanceof IllegalArgumentException)
                                                message(
                                                        sender,
                                                        "Montant refusé : solde insuffisant ou"
                                                                + " limite atteinte.",
                                                        NamedTextColor.RED);
                                            else {
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Opération économique impossible",
                                                                error);
                                                message(
                                                        sender,
                                                        "Opération impossible. Réessaie dans"
                                                                + " quelques instants.",
                                                        NamedTextColor.RED);
                                            }
                                            return;
                                        }
                                        if (result == null) {
                                            message(
                                                    sender,
                                                    "Ce joueur n’est pas connu du serveur. Vérifie"
                                                            + " son pseudo.",
                                                    NamedTextColor.RED);
                                            return;
                                        }
                                        message(
                                                sender,
                                                currency.symbol()
                                                        + " "
                                                        + currency.displayName()
                                                        + " : "
                                                        + currency.format(result),
                                                NamedTextColor.GREEN);
                                    });
                        });
        return true;
    }

    private void message(CommandSender sender, String message, NamedTextColor color) {
        sender.sendMessage(Component.text(message, color));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cliker.money.use")) return List.of();
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission("cliker.money.admin");
        if (args.length == 1)
            options.addAll(admin ? List.of("balance", "give", "take", "set") : List.of("balance"));
        if (args.length == 2 && (args[0].equalsIgnoreCase("balance") || admin))
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        if (args.length == 3 && admin) options.add("100");
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
