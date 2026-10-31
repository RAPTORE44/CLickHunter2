package fr.cliker.clikerv2.features;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobEggs {
    private MobEggs() {}

    public static ItemStack create(JavaPlugin plugin, MobTier tier, String rarity) {
        Material material = Material.matchMaterial(tier.entityType().name() + "_SPAWN_EGG");
        if (material == null) material = Material.PIG_SPAWN_EGG;
        boolean epic = rarity.equals("epique");
        var item =
                Menus.item(
                        material,
                        (epic ? "&5" : "&9")
                                + "Œuf "
                                + (epic ? "épique" : "rare")
                                + " — "
                                + tier.label(),
                        "&7Invoque une créature de cette zone.",
                        "&7Clic droit sur un bloc dans une zone débloquée.");
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer()
                .set(new NamespacedKey(plugin, "market_egg"), PersistentDataType.STRING, tier.id());
        meta.getPersistentDataContainer()
                .set(new NamespacedKey(plugin, "egg_rarity"), PersistentDataType.STRING, rarity);
        item.setItemMeta(meta);
        return item;
    }
}
