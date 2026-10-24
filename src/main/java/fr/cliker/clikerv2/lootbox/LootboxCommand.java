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
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    public LootboxCommand(org.bukkit.plugin.java.JavaPlugin plugin) { this.plugin = plugin; }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) { sender.sendMessage(Component.text("Permission administrateur requise.", NamedTextColor.RED)); return true; }
        if (args.length < 2 || args.length > 3) { sender.sendMessage(Component.text("/" + label + " <joueur> <rareté> [quantité]", NamedTextColor.YELLOW)); return true; }

        var delivery = Bukkit.getServicesManager().load(fr.cliker.clikerv2.rewards.ClaimService.class);
        if (delivery == null) { sender.sendMessage(Component.text("Service de récompenses indisponible.", NamedTextColor.RED)); return true; }

        LootboxTier tier = LootboxTier.parse(args[1]);
        if (tier == null) { sender.sendMessage(Component.text("Rareté inconnue : " + String.join(", ", LootboxTier.byId().keySet()), NamedTextColor.RED)); return true; }

        int amount = 1;
        if (args.length == 3) { try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException invalid) { sender.sendMessage(Component.text("Quantité invalide.", NamedTextColor.RED)); return true; } }

        if (amount < 1 || amount > 4096) { sender.sendMessage(Component.text("Quantité autorisée : 1 à 4096.", NamedTextColor.RED)); return true; }
        ItemStack box = LootboxItem.create(tier); box.setAmount(amount);
        String name = args[0]; int quantity = amount;
        delivery.resolve(name).whenComplete((uuid, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || uuid == null) { sender.sendMessage(Component.text("Joueur inconnu : il doit avoir déjà rejoint le serveur.", NamedTextColor.RED)); return; }
            delivery.give(uuid, name, box);
            sender.sendMessage(Component.text(quantity + "x " + tier.boxName() + " attribué(s) à " + name + " (ou conservé(s) dans /claim).", NamedTextColor.GREEN));
        }));
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
