package fr.cliker.clikerv2.daily;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class DailySettings {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration config;
    private final ZoneId zone;

    public DailySettings(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "daily.yml");
        if (!file.exists()) plugin.saveResource("daily.yml", false);
        config = fr.cliker.clikerv2.config.YamlFiles.load(file);
        migrateCalendar();
        zone = ZoneId.of(config.getString("fuseau-horaire", "Europe/Paris"));
    }

    private void migrateCalendar() {
        if (config.getInt("schema-version", 1) >= 2) return;
        try {
            java.nio.file.Path backup =
                    plugin.getDataFolder()
                            .toPath()
                            .resolve("backups/daily-" + System.currentTimeMillis() + ".yml");
            java.nio.file.Files.createDirectories(backup.getParent());
            java.nio.file.Files.copy(file.toPath(), backup);
            // Remplacer uniquement le pack de test livré auparavant ; préserver les récompenses
            // personnalisées.
            boolean testPack = true;
            for (int day = 1; day <= 31; day++) {
                if (!config.getString("recompenses." + day + ".description", "")
                        .equals("Récompense test — Jour " + day)) testPack = false;
            }
            if (testPack) {
                try (var input = plugin.getResource("daily.yml")) {
                    var defaults =
                            YamlConfiguration.loadConfiguration(
                                    new java.io.InputStreamReader(
                                            java.util.Objects.requireNonNull(input),
                                            java.nio.charset.StandardCharsets.UTF_8));
                    config.set("recompenses", defaults.getConfigurationSection("recompenses"));
                    config.set("interface", defaults.getConfigurationSection("interface"));
                }
            }
            config.set("schema-version", 2);
            save();
            plugin.getLogger()
                    .info(
                            "Daily : cycle de 28 jours, configuration sauvegardée dans "
                                    + backup.getFileName());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Migration du calendrier impossible", e);
        }
    }

    public YamlConfiguration config() {
        return config;
    }

    public ZoneId zone() {
        return zone;
    }

    public void save() {
        try {
            config.save(file);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Impossible de sauvegarder daily.yml", e);
        }
    }

    public static String replace(String input, Map<String, String> values) {
        String result = input;
        for (var value : values.entrySet())
            result = result.replace("{" + value.getKey() + "}", value.getValue());
        return result;
    }

    public static Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public String message(String key) {
        return config.getString("messages." + key, key).replace("Citizens", "ZNPCsPlus");
    }

    public Component message(String key, Map<String, String> values) {
        return color(replace(message(key), values));
    }

    public String reward(int day) {
        return config.getString(
                "recompenses." + day + ".description", "Récompense test — Jour " + day);
    }

    public List<String> commands(int day) {
        return config.getStringList("recompenses." + day + ".commandes");
    }

    public ItemStack icon(String name, Map<String, String> values) {
        String path = "interface.icones." + name;
        String custom = config.getString(path + ".itemsadder", "");
        ItemStack item = null;
        if (!custom.isBlank()
                && plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
            try {
                Class<?> api = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object stack = api.getMethod("getInstance", String.class).invoke(null, custom);
                if (stack != null)
                    item = ((ItemStack) api.getMethod("getItemStack").invoke(stack)).clone();
            } catch (ReflectiveOperationException | LinkageError e) {
                plugin.getLogger()
                        .log(Level.WARNING, "Icône ItemsAdder indisponible : " + custom, e);
            }
        }
        if (item == null) {
            Material material =
                    Material.matchMaterial(config.getString(path + ".materiau", "PAPER"));
            if (material == null || material.isAir() || !material.isItem())
                material = Material.PAPER;
            item = new ItemStack(material);
        }
        ItemMeta meta = item.getItemMeta();
        meta.displayName(color(replace(config.getString(path + ".nom", " "), values)));
        meta.lore(
                config.getStringList(path + ".description").stream()
                        .map(line -> color(replace(line, values)))
                        .toList());
        int model = config.getInt(path + ".custom-model-data", 0);
        if (model > 0) meta.setCustomModelData(model);
        if (config.getBoolean(path + ".brillant", false)) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }
}
