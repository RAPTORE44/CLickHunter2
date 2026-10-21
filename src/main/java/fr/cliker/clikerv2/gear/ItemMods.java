package fr.cliker.clikerv2.gear;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stocke enchants / reforge / runes directement sur l'item (PDC), niveaux sérialisés en "TYPE:niveau;TYPE:niveau". */
public final class ItemMods {
    private static NamespacedKey ENCHANTS_KEY;
    private static NamespacedKey REFORGE_KEY;
    private static NamespacedKey RUNES_KEY;

    private ItemMods() {}

    public static void init(JavaPlugin plugin) {
        ENCHANTS_KEY = new NamespacedKey(plugin, "cliker_enchants");
        REFORGE_KEY = new NamespacedKey(plugin, "cliker_reforge");
        RUNES_KEY = new NamespacedKey(plugin, "cliker_runes");
    }

    public static int getEnchantLevel(ItemStack item, EnchantType type) {
        return readLevels(item, ENCHANTS_KEY).getOrDefault(type.name(), 0);
    }

    public static void setEnchantLevel(ItemStack item, EnchantType type, int level) {
        Map<String, Integer> levels = readLevels(item, ENCHANTS_KEY);
        levels.put(type.name(), level);
        writeLevels(item, ENCHANTS_KEY, levels);
    }

    /** Retire tous les enchants (max 1 enchant actif à la fois sur un item). */
    public static void clearEnchants(ItemStack item) {
        writeLevels(item, ENCHANTS_KEY, new LinkedHashMap<>());
    }

    public static ReforgeType getReforge(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(REFORGE_KEY, PersistentDataType.STRING);
        if (id == null) return null;
        try { return ReforgeType.valueOf(id); } catch (IllegalArgumentException invalid) { return null; }
    }

    public static void setReforge(ItemStack item, ReforgeType reforge) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(REFORGE_KEY, PersistentDataType.STRING, reforge.name());
        item.setItemMeta(meta);
    }

    public static int getRuneLevel(ItemStack item, RuneType type) {
        return readLevels(item, RUNES_KEY).getOrDefault(type.name(), 0);
    }

    public static void setRuneLevel(ItemStack item, RuneType type, int level) {
        Map<String, Integer> levels = readLevels(item, RUNES_KEY);
        levels.put(type.name(), level);
        writeLevels(item, RUNES_KEY, levels);
    }

    /** Retire toutes les runes (max 1 rune active à la fois sur un item). */
    public static void clearRunes(ItemStack item) {
        writeLevels(item, RUNES_KEY, new LinkedHashMap<>());
    }

    private static Map<String, Integer> readLevels(ItemStack item, NamespacedKey key) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (item == null || !item.hasItemMeta()) return map;
        String raw = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return map;
        for (String part : raw.split(";")) {
            String[] split = part.split(":");
            if (split.length != 2) continue;
            try { map.put(split[0], Integer.parseInt(split[1])); } catch (NumberFormatException ignored) {}
        }
        return map;
    }

    private static void writeLevels(ItemStack item, NamespacedKey key, Map<String, Integer> levels) {
        ItemMeta meta = item.getItemMeta();
        StringBuilder builder = new StringBuilder();
        for (var entry : levels.entrySet()) {
            if (entry.getValue() <= 0) continue;
            if (builder.length() > 0) builder.append(';');
            builder.append(entry.getKey()).append(':').append(entry.getValue());
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, builder.toString());
        item.setItemMeta(meta);
    }
}
