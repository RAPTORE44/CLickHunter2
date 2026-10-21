package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.prestige.PrestigeType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Reconstruit la lore complète d'une épée/armure tier après modification des enchants/reforge/runes. */
public final class GearLore {
    private GearLore() {}

    public static void refresh(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String swordTierId = pdc.get(TierGear.SWORD_KEY, PersistentDataType.STRING);
        String armorTierId = pdc.get(TierGear.ARMOR_KEY, PersistentDataType.STRING);
        if (swordTierId != null) {
            MobTier tier = MobTier.parse(swordTierId);
            if (tier != null) refreshSword(item, tier);
        } else if (armorTierId != null) {
            MobTier tier = MobTier.parse(armorTierId);
            TierGear.ArmorSlot slot = TierGear.slotOf(item.getType());
            if (tier != null && slot != null) refreshArmor(item, tier, slot);
        }
    }

    private static void refreshSword(ItemStack item, MobTier tier) {
        int tranchant = ItemMods.getEnchantLevel(item, EnchantType.TRANCHANT);
        long bonusDamage = Math.round(EnchantType.TRANCHANT.magnitudeAt(tier, tranchant));
        ReforgeType reforge = ItemMods.getReforge(item);
        if (reforge != null) bonusDamage += reforge.swordDamageBonus(tier);
        int runeTranchant = ItemMods.getRuneLevel(item, RuneType.TRANCHANT);
        bonusDamage += Math.round(RuneType.TRANCHANT.magnitudeAt(tier, runeTranchant));

        List<Component> lore = new ArrayList<>(TierGear.swordLore(tier, bonusDamage));
        insertModsSections(lore, 2, item, EnchantType.Category.SWORD, tier);

        ItemMeta meta = item.getItemMeta();
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private static void refreshArmor(ItemStack item, MobTier tier, TierGear.ArmorSlot slot) {
        List<Component> lore = new ArrayList<>(TierGear.armorLore(tier, slot, 0));
        insertModsSections(lore, 2, item, EnchantType.Category.ARMOR, tier);

        ItemMeta meta = item.getItemMeta();
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private static void insertModsSections(List<Component> lore, int insertAt, ItemStack item, EnchantType.Category category, MobTier tier) {
        List<Component> section = new ArrayList<>();

        for (EnchantType type : EnchantType.of(category)) {
            int level = ItemMods.getEnchantLevel(item, type);
            if (level <= 0) continue;
            section.add(Component.text("✦ " + type.label() + " ", type.color())
                    .append(Component.text(PrestigeType.toRoman(level), NamedTextColor.YELLOW))
                    .append(Component.text(" (+" + Numbers.compact(type.magnitudeAt(tier, level)) + (isPercent(type) ? "%" : "") + ")", NamedTextColor.GRAY)));
            section.add(Component.text("  " + type.description(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }

        ReforgeType reforge = ItemMods.getReforge(item);
        if (reforge != null) {
            section.add(Component.text("⚒ Reforge : ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(reforge.label(), reforge.color(), TextDecoration.BOLD)));
            section.add(Component.text("  " + reforge.description(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }

        for (RuneType type : RuneType.of(category)) {
            int level = ItemMods.getRuneLevel(item, type);
            if (level <= 0) continue;
            section.add(Component.text("◆ Rune " + type.label() + " ", type.color())
                    .append(Component.text(PrestigeType.toRoman(level), NamedTextColor.AQUA))
                    .append(Component.text(" (+" + Numbers.compact(type.magnitudeAt(tier, level)) + (isPercent(type) ? "%" : "") + ")", NamedTextColor.GRAY)));
            section.add(Component.text("  " + type.description(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }

        if (section.isEmpty()) return;
        section.add(Component.text(""));
        lore.addAll(Math.min(insertAt, lore.size()), section);
    }

    private static boolean isPercent(EnchantType type) {
        return type == EnchantType.DEFENCE || type == EnchantType.LOOT_FINDER || type == EnchantType.CRITICAL;
    }

    private static boolean isPercent(RuneType type) {
        return false;
    }
}
