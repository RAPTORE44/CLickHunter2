package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.MobTier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TierGearCommand implements CommandExecutor, TabCompleter {

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
        if (args.length != 1) {
            sender.sendMessage(Component.text("/" + label + " <mob>", NamedTextColor.YELLOW));
            return true;
        }

        MobTier tier = MobTier.parse(args[0]);
        if (tier == null) {
            sender.sendMessage(
                    Component.text("Mob inconnu. Utilisez /mobspawn list.", NamedTextColor.RED));
            return true;
        }

        List<ItemStack> pieces = new ArrayList<>();
        pieces.add(TierGear.createSword(tier));
        for (TierGear.ArmorSlot slot : TierGear.ArmorSlot.values())
            pieces.add(TierGear.createArmor(tier, slot));
        for (ItemStack piece : pieces) {
            fr.cliker.clikerv2.rewards.ItemDelivery.give(player, piece);
        }

        sender.sendMessage(
                Component.text(
                                "Set " + tier.label() + " reçu (épée + armure). ",
                                NamedTextColor.GREEN)
                        .append(
                                Component.text(
                                        "Équipe les pièces assorties pour cumuler leurs dégâts.",
                                        NamedTextColor.LIGHT_PURPLE)));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin") || args.length != 1) return List.of();
        String current = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>(MobTier.byId().keySet());
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(current));
        return options;
    }
}
