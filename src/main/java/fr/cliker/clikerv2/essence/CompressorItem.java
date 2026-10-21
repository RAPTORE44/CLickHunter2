package fr.cliker.clikerv2.essence;

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

public final class CompressorItem {
    public static NamespacedKey MOB_KEY;

    private CompressorItem() {}

    public static void init(JavaPlugin plugin) {
        MOB_KEY = new NamespacedKey(plugin, "cliker_compressor_mob");
    }

    public static ItemStack create(MobTier tier) {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Compresseur " + tier.label(), NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Tant qu'il est dans ton inventaire,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("compresse tes essences " + tier.label() + ".", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(""),
                Component.text("64 essences d'un palier = 1 du palier suivant.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
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
