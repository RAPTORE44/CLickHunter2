package fr.cliker.clikerv2.raid;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.features.Menus;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class RaidScrolls {
    private RaidScrolls() {}

    public static ItemStack create(JavaPlugin plugin, MobTier tier) {
        var item =
                Menus.item(
                        Material.PAPER,
                        "&dParchemin de raid — " + tier.label(),
                        "&7Difficulté : zone " + (tier.ordinal() + 1),
                        "&7Le chef le tient en main pour lancer le raid.",
                        "&7Un parchemin consommé par lancement.");
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer()
                .set(
                        new NamespacedKey(plugin, "raid_scroll"),
                        PersistentDataType.STRING,
                        tier.id());
        item.setItemMeta(meta);
        return item;
    }
}
