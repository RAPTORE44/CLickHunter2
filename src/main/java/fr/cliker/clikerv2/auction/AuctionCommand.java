package fr.cliker.clikerv2.auction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AuctionCommand implements CommandExecutor, org.bukkit.command.TabCompleter {
    private final AuctionMenu menu;

    public AuctionCommand(AuctionMenu menu) {
        this.menu = menu;
    }

    @Override
    public java.util.List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return java.util.List.of();
        return java.util.List.of("vendre", "sell", "mesannonces").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    Component.text("Commande réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            menu.openBrowse(player);
            return true;
        }

        if ((args[0].equalsIgnoreCase("mylistings") || args[0].equalsIgnoreCase("mesannonces"))) {
            menu.openMyListings(player);
            return true;
        }

        if ((args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("vendre"))) {
            if (args.length != 2) {
                player.sendMessage(Component.text("Usage : /ah vendre <prix>", NamedTextColor.RED));
                return true;
            }
            long price;
            try {
                price = Long.parseLong(args[1]);
            } catch (NumberFormatException invalid) {
                player.sendMessage(Component.text("Prix invalide.", NamedTextColor.RED));
                return true;
            }
            if (price <= 0) {
                player.sendMessage(
                        Component.text("Le prix doit être positif.", NamedTextColor.RED));
                return true;
            }

            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                player.sendMessage(
                        Component.text("Tiens l’objet à vendre en main.", NamedTextColor.RED));
                return true;
            }

            if (fr.cliker.clikerv2.comfort.SessionConvenience.protectedMenu(hand)) {
                player.sendMessage("L’étoile du menu ne peut pas être vendue.");
                return true;
            }
            if (fr.cliker.clikerv2.comfort.PlayerComfort.protectedForSale(player, hand)) {
                player.sendMessage(
                        "Équipement protégé : /lock pour déverrouiller, puis /antidrop"
                                + " pour autoriser sa vente.");
                return true;
            }
            menu.openSellConfirm(player, hand, price);
            return true;
        }

        player.sendMessage(
                Component.text("/ah, /ah vendre <prix>, /ah mesannonces", NamedTextColor.YELLOW));
        return true;
    }
}
