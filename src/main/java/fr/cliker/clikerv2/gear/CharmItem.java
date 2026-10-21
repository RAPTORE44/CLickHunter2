package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.Numbers;

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

/** Amulette passive : tant qu'elle est dans l'inventaire, ajoute un bonus de dégâts fixe propre au mob. */
public final class CharmItem {
    public static NamespacedKey MOB_KEY;

    private CharmItem() {}

    public static void init(JavaPlugin plugin) {
        MOB_KEY = new NamespacedKey(plugin, "cliker_charm_mob");
    }

    public static ItemStack create(MobTier tier) {
        ItemStack item = new ItemStack(Material.PRISMARINE_CRYSTALS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Amulette " + tier.label(), NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Tant qu'elle est dans ton inventaire :", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("+" + Numbers.compact(tier.charmDamageBonus()) + " dégâts", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, tier.id());
        item.setItemMeta(meta);
        return item;
    }

    public static MobTier tierOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(MOB_KEY, PersistentDataType.STRING);
        return id == null ? null : MobTier.parse(id);
    }
}
