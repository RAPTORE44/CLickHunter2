package fr.cliker.clikerv2.lootbox;

import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class LootboxItem {
    public static NamespacedKey KEY;

    private LootboxItem() {}

    public static void init(JavaPlugin plugin) {
        KEY = new NamespacedKey(plugin, "cliker_lootbox");
    }

    public static ItemStack create(LootboxTier tier) {
        ItemStack item = new ItemStack(Material.CHEST);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(tier.boxName(), tier.color(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Contient :", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                line(Numbers.compact(tier.ecus()) + " Écus", NamedTextColor.GOLD),
                line(CurrencyType.GEMMES_SOMBRES.format(tier.gemmesSombresTenths()) + " Gemmes Sombres", NamedTextColor.DARK_PURPLE),
                line(Numbers.compact(tier.xp()) + " XP", NamedTextColor.AQUA),
                line(Numbers.compact(tier.cendres()) + " Cendres", NamedTextColor.GRAY),
                Component.text(""),
                Component.text("Clic-droit pour ouvrir !", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("Shift-clic pour tout ouvrir !", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text(""),
                Component.text("Chance d'obtention (par kill) : ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(tier.dropChancePercent() + "%", NamedTextColor.RED)),
                Component.text(""),
                Component.text(tier.rarityLabel(), tier.color(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, tier.id());
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text("■ ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(text, color)).append(Component.text(" (100%)", NamedTextColor.DARK_GRAY));
    }

    public static LootboxTier tierOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return id == null ? null : LootboxTier.parse(id);
    }
}
