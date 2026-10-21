package fr.cliker.clikerv2.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class SwordCommand implements CommandExecutor {
    public static NamespacedKey SWORD_KEY;

    public SwordCommand(JavaPlugin plugin) {
        SWORD_KEY = new NamespacedKey(plugin, "cliker_sword");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) { sender.sendMessage(Component.text("Permission administrateur requise.", NamedTextColor.RED)); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Commande réservée aux joueurs.", NamedTextColor.RED)); return true; }

        player.getInventory().addItem(create());
        player.sendMessage(Component.text("⚔ Épée +25 dégâts reçue.", NamedTextColor.AQUA));
        return true;
    }

    public static ItemStack create() {
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.displayName(Component.text("Épée du Dresseur", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("+25 Dégâts", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(SWORD_KEY, PersistentDataType.BYTE, (byte) 1);
        sword.setItemMeta(meta);
        return sword;
    }
}
