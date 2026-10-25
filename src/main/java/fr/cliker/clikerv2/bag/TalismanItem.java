package fr.cliker.clikerv2.bag;

import fr.cliker.clikerv2.MobTier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Talismans posés dans le /sac : bonus passif tant qu'ils sont dedans, pas besoin de les porter.
 */
public final class TalismanItem {
    private static NamespacedKey MOB_KEY;
    private static NamespacedKey TREASURE_KEY;

    private TalismanItem() {}

    public static void init(JavaPlugin plugin) {
        MOB_KEY = new NamespacedKey(plugin, "cliker_talisman_mob");
        TREASURE_KEY = new NamespacedKey(plugin, "cliker_talisman_treasure");
    }

    /**
     * % de dégâts bonus contre ce mob, tant que le talisman est dans le /sac : scale jusqu'à 90%
     * pour le tier le plus haut.
     */
    public static double mobBonusPercent(MobTier tier) {
        int lastOrdinal = MobTier.values().length - 1;
        return 90.0 * (tier.ordinal() + 1) / (lastOrdinal + 1);
    }

    public static ItemStack createMobTalisman(MobTier tier) {
        ItemStack item = new ItemStack(Material.NAUTILUS_SHELL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(
                Component.text("Talisman " + tier.label(), NamedTextColor.AQUA, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
        meta.lore(
                List.of(
                        Component.text("Augmente les dégâts contre ", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                                .append(Component.text(tier.label(), NamedTextColor.GOLD))
                                .append(
                                        Component.text(
                                                " de +" + Math.round(mobBonusPercent(tier)) + "%.",
                                                NamedTextColor.GRAY)),
                        Component.text(""),
                        Component.text(
                                        "Actif tant qu'il est dans ton /sac.",
                                        NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, tier.id());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createTreasureTalisman() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(
                Component.text("Talisman du trésor", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
        meta.lore(
                List.of(
                        Component.text("Augmente les Écus des mobs de ", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                                .append(Component.text("+100", NamedTextColor.GOLD)),
                        Component.text("Augmente les Gemmes des mobs de ", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                                .append(Component.text("+1", NamedTextColor.DARK_PURPLE)),
                        Component.text("Augmente l'XP des mobs de ", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                                .append(Component.text("+25", NamedTextColor.AQUA)),
                        Component.text(""),
                        Component.text(
                                        "Actif tant qu'il est dans ton /sac.",
                                        NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(TREASURE_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static MobTier mobTierOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(MOB_KEY, PersistentDataType.STRING);
        return id == null ? null : MobTier.parse(id);
    }

    public static boolean isTreasure(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta()
                        .getPersistentDataContainer()
                        .has(TREASURE_KEY, PersistentDataType.BYTE);
    }
}
