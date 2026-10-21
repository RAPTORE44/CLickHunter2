package fr.cliker.clikerv2.auction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AuctionCommand implements CommandExecutor {
    private final AuctionMenu menu;

    public AuctionCommand(AuctionMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Commande réservée aux joueurs.", NamedTextColor.RED)); return true; }

        if (args.length == 0) {
            menu.openBrowse(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("mylistings")) {
            menu.openMyListings(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("sell")) {
            if (args.length != 2) { player.sendMessage(Component.text("Usage : /ah sell <prix>", NamedTextColor.RED)); return true; }
            long price;
            try { price = Long.parseLong(args[1]); } catch (NumberFormatException invalid) {
                player.sendMessage(Component.text("Prix invalide.", NamedTextColor.RED)); return true;
            }
            if (price <= 0) { player.sendMessage(Component.text("Le prix doit être positif.", NamedTextColor.RED)); return true; }

            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) { player.sendMessage(Component.text("Tiens l'item à vendre en main.", NamedTextColor.RED)); return true; }

            menu.openSellConfirm(player, hand, price);
            return true;
        }

        player.sendMessage(Component.text("/ah, /ah sell <prix>, /ah mylistings", NamedTextColor.YELLOW));
        return true;
    }
}
