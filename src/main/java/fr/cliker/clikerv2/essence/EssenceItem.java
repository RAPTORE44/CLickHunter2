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

public final class EssenceItem {
    public static NamespacedKey TIER_KEY;
    public static NamespacedKey RARITY_KEY;

    private EssenceItem() {}

    public static void init(JavaPlugin plugin) {
        TIER_KEY = new NamespacedKey(plugin, "cliker_essence_tier");
        RARITY_KEY = new NamespacedKey(plugin, "cliker_essence_rarity");
    }

    /** Essence brute obtenue en tuant un mob : toujours de rareté Commune. */
    public static ItemStack create(MobTier tier, int amount) {
        return create(tier, EssenceRarity.COMMUNE, amount);
    }

    public static ItemStack create(MobTier tier, EssenceRarity rarity, int amount) {
        ItemStack item = new ItemStack(spawnEggFor(tier), amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(rarity.label() + " " + tier.essenceName(), NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(rarity.label(), rarity.color()).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(TIER_KEY, PersistentDataType.STRING, tier.id());
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity.name());
        item.setItemMeta(meta);
        return item;
    }

    public static MobTier tierOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(TIER_KEY, PersistentDataType.STRING);
        return id == null ? null : MobTier.parse(id);
    }

    public static EssenceRarity rarityOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return EssenceRarity.COMMUNE;
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(RARITY_KEY, PersistentDataType.STRING);
        if (id == null) return EssenceRarity.COMMUNE;
        try { return EssenceRarity.valueOf(id); } catch (IllegalArgumentException invalid) { return EssenceRarity.COMMUNE; }
    }

    public static int count(org.bukkit.entity.Player player, MobTier tier, EssenceRarity rarity) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (tierOf(stack) == tier && rarityOf(stack) == rarity) total += stack.getAmount();
        }
        return total;
    }

    /** Retire `amount` essences du tier/rareté donnés. Retourne false (sans rien retirer) si le joueur n'en a pas assez. */
    public static boolean remove(org.bukkit.entity.Player player, MobTier tier, EssenceRarity rarity, int amount) {
        if (count(player, tier, rarity) < amount) return false;
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (tierOf(stack) != tier || rarityOf(stack) != rarity) continue;
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
        }
        return true;
    }

    private static Material spawnEggFor(MobTier tier) {
        try {
            return Material.valueOf(tier.entityType().name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException notFound) {
            return Material.EGG;
        }
    }
}
