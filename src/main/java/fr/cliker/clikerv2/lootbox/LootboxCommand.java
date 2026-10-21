package fr.cliker.clikerv2.lootbox;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LootboxCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) { sender.sendMessage(Component.text("Permission administrateur requise.", NamedTextColor.RED)); return true; }
        if (args.length < 2 || args.length > 3) { sender.sendMessage(Component.text("/" + label + " <joueur> <rareté> [quantité]", NamedTextColor.YELLOW)); return true; }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage(Component.text("Joueur introuvable ou hors ligne.", NamedTextColor.RED)); return true; }

        LootboxTier tier = LootboxTier.parse(args[1]);
        if (tier == null) { sender.sendMessage(Component.text("Rareté inconnue : " + String.join(", ", LootboxTier.byId().keySet()), NamedTextColor.RED)); return true; }

        int amount = 1;
        if (args.length == 3) { try { amount = Math.max(1, Integer.parseInt(args[2])); } catch (NumberFormatException ignored) { } }

        ItemStack box = LootboxItem.create(tier);
        box.setAmount(amount);
        var leftover = target.getInventory().addItem(box);
        leftover.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));

        sender.sendMessage(Component.text(amount + "x " + tier.boxName() + " donné(s) à " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) for (Player online : Bukkit.getOnlinePlayers()) options.add(online.getName());
        else if (args.length == 2) options.addAll(LootboxTier.byId().keySet());
        else if (args.length == 3) options.add("1");
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(current));
        return options;
    }
}
