package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.Numbers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class TierGear {
    public static NamespacedKey SWORD_KEY;
    public static NamespacedKey ARMOR_KEY;

    private TierGear() {}

    public static void init(JavaPlugin plugin) {
        SWORD_KEY = new NamespacedKey(plugin, "cliker_tier_sword");
        ARMOR_KEY = new NamespacedKey(plugin, "cliker_tier_armor");
    }

    /** Épée ou armure de tier valide (utilisable pour enchant/reforge/runes). */
    public static boolean isGear(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(SWORD_KEY, PersistentDataType.STRING) || pdc.has(ARMOR_KEY, PersistentDataType.STRING);
    }

    public enum ArmorSlot {
        HELMET(Material.LEATHER_HELMET, "Casque"),
        CHESTPLATE(Material.LEATHER_CHESTPLATE, "Plastron"),
        LEGGINGS(Material.LEATHER_LEGGINGS, "Jambières"),
        BOOTS(Material.LEATHER_BOOTS, "Bottes");

        private final Material material;
        private final String label;

        ArmorSlot(Material material, String label) { this.material = material; this.label = label; }
        public Material material() { return material; }
        public String label() { return label; }
    }

    enum Rarity {
        COMMUN("COMMUN", NamedTextColor.WHITE, Color.fromRGB(0xAAAAAA)),
        PEU_COMMUN("PEU COMMUN", NamedTextColor.GREEN, Color.fromRGB(0x55FF55)),
        RARE("RARE", NamedTextColor.BLUE, Color.fromRGB(0x5555FF)),
        EPIQUE("ÉPIQUE", NamedTextColor.DARK_PURPLE, Color.fromRGB(0xAA00AA)),
        LEGENDAIRE("LÉGENDAIRE", NamedTextColor.GOLD, Color.fromRGB(0xFFAA00)),
        MYTHIQUE("MYTHIQUE", NamedTextColor.LIGHT_PURPLE, Color.fromRGB(0xFF55FF));

        private final String label;
        private final NamedTextColor color;
        private final Color leatherColor;

        Rarity(String label, NamedTextColor color, Color leatherColor) {
            this.label = label;
            this.color = color;
            this.leatherColor = leatherColor;
        }

        public String label() { return label; }
        public NamedTextColor color() { return color; }
    }

    /** Épée = 1 pièce, chaque pièce d'armure assortie = 1 pièce de plus. Chaque pièce inflige un montant fixe = 8% des PV du TIER DE L'ITEM (pas de la cible). Set complet (5 pièces) = 40% des PV du tier de l'item, appliqué tel quel sur n'importe quel mob. */
    private static final int PIECE_PERCENT = 8;

    /** Dégâts fixes d'une pièce : identique au chiffre affiché en lore, quelle que soit la cible visée. */
    public static long pieceDamage(MobTier tier) { return Math.round(tier.maxHealth() * PIECE_PERCENT / 100.0); }

    public static ItemStack createSword(MobTier tier) {
        Rarity rarity = rarityOf(tier);
        ItemStack item = new ItemStack(swordMaterial(tier));
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Épée " + tier.label(), rarity.color(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(swordLore(tier, 0));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(SWORD_KEY, PersistentDataType.STRING, tier.id());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createArmor(MobTier tier, ArmorSlot slot) {
        Rarity rarity = rarityOf(tier);
        ItemStack item = new ItemStack(slot.material());
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(rarity.leatherColor);
        meta.displayName(Component.text(tier.label() + " " + slot.label(), rarity.color(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(armorLore(tier, slot, 0));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(ARMOR_KEY, PersistentDataType.STRING, tier.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Lore de base de l'épée (hors sections enchants/reforge/runes, ajoutées par GearLore). bonusDamage = dégâts fixes en plus (reforge/enchant/rune). */
    static List<Component> swordLore(MobTier tier, long bonusDamage) {
        Rarity rarity = rarityOf(tier);
        return List.of(
                Component.text("■ ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("Dégâts: ", NamedTextColor.GRAY))
                        .append(Component.text(Numbers.compact(pieceDamage(tier) + bonusDamage), NamedTextColor.RED)),
                Component.text(""),
                Component.text("Utilisable sur tout mob custom", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text(""),
                Component.text(rarity.label() + " ÉPÉE", rarity.color(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
        );
    }

    /** Lore de base d'une pièce d'armure (hors sections enchants/reforge/runes, ajoutées par GearLore). */
    static List<Component> armorLore(MobTier tier, ArmorSlot slot, long bonusDamage) {
        Rarity rarity = rarityOf(tier);
        long damage = pieceDamage(tier) + bonusDamage;
        return List.of(
                Component.text("■ ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("Dégâts: ", NamedTextColor.GRAY))
                        .append(Component.text("+" + Numbers.compact(damage), NamedTextColor.RED)),
                Component.text(""),
                Component.text("Bonus Set Complet: Dégâts", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Épée + set complet = " + Numbers.compact(damage * 5) + " dégâts fixes", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text(""),
                Component.text(rarity.label() + " " + slot.label().toUpperCase(java.util.Locale.ROOT), rarity.color(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
        );
    }

    static ArmorSlot slotOf(Material material) {
        for (ArmorSlot slot : ArmorSlot.values()) if (slot.material() == material) return slot;
        return null;
    }

    private static Material swordMaterial(MobTier tier) {
        int index = tier.ordinal();
        if (index < 3) return Material.WOODEN_SWORD;
        if (index < 6) return Material.STONE_SWORD;
        if (index < 9) return Material.IRON_SWORD;
        if (index < 12) return Material.GOLDEN_SWORD;
        if (index < 15) return Material.DIAMOND_SWORD;
        return Material.NETHERITE_SWORD;
    }

    static Rarity rarityOf(MobTier tier) {
        int index = tier.ordinal();
        if (index < 3) return Rarity.COMMUN;
        if (index < 6) return Rarity.PEU_COMMUN;
        if (index < 9) return Rarity.RARE;
        if (index < 12) return Rarity.EPIQUE;
        if (index < 15) return Rarity.LEGENDAIRE;
        return Rarity.MYTHIQUE;
    }
}
