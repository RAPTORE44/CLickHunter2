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
        file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "daily.yml");
        if (!file.exists()) fr.cliker.clikerv2.config.ConfigFiles.install(plugin, "daily.yml");
        config = fr.cliker.clikerv2.config.YamlFiles.load(file);
        migrateCalendar();
        balanceEarlyRewards();
        zone = ZoneId.of(config.getString("fuseau-horaire", "Europe/Paris"));
    }

    private void balanceEarlyRewards() {
        if (config.getInt("equilibrage-debut-version", 0) >= 1) return;
        try {
            var backup =
                    file.toPath()
                            .getParent()
                            .resolve(
                                    "backups/daily-equilibrage-"
                                            + System.currentTimeMillis()
                                            + ".yml");
            java.nio.file.Files.createDirectories(backup.getParent());
            java.nio.file.Files.copy(file.toPath(), backup);
            long[] amounts = {100, 150, 200, 250, 300, 400, 500, 600, 750, 1000};
            for (int day = 1; day <= 10; day++) {
                var commands = config.getStringList("recompenses." + day + ".commandes");
                String rarity = day <= 7 ? "epique" : "legendaire";
                int count = day <= 7 ? day : day - 7;
                if (!commands.equals(List.of("coffre {joueur} " + rarity + " " + count))) continue;
                config.set("recompenses." + day + ".description", amounts[day - 1] + " Écus");
                config.set(
                        "recompenses." + day + ".commandes",
                        List.of("ecus give {joueur} " + amounts[day - 1]));
            }
            config.set("equilibrage-debut-version", 1);
            save();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Rééquilibrage du daily impossible", error);
        }
    }

    private void migrateCalendar() {
        if (config.getInt("schema-version", 1) >= 3) return;
        try {
            java.nio.file.Path backup =
                    file.toPath()
                            .getParent()
                            .resolve("backups/daily-" + System.currentTimeMillis() + ".yml");
            java.nio.file.Files.createDirectories(backup.getParent());
            java.nio.file.Files.copy(file.toPath(), backup);
            try (var input = plugin.getResource("daily.yml")) {
                var defaults =
                        YamlConfiguration.loadConfiguration(
                                new java.io.InputStreamReader(
                                        java.util.Objects.requireNonNull(input),
                                        java.nio.charset.StandardCharsets.UTF_8));
                // Ajouter seulement les journées absentes ; conserver les récompenses
                // personnalisées
                // et les dates de récupération. L'ancien pack de test n'est plus remplacé.
                for (int day = 1; day <= DailyProgress.DAYS; day++) {
                    String path = "recompenses." + day;
                    if (!config.contains(path))
                        config.set(path, defaults.getConfigurationSection(path));
                }
            }
            String path = "interface.icones.informations.description";
            if (config.contains(path))
                config.set(
                        path,
                        config.getStringList(path).stream()
                                .map(
                                        line ->
                                                line.replace("/28", "/31")
                                                        .replace("jour 28", "jour 31"))
                                .toList());
            config.set("schema-version", 3);
            save();
            plugin.getLogger()
                    .info(
                            "Daily : calendrier de 31 jours ; configuration précédente sauvegardée"
                                    + " dans "
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
        meta.displayName(
                color(replace(config.getString(path + ".nom", " "), values))
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(
                config.getStringList(path + ".description").stream()
                        .map(
                                line ->
                                        color(replace(line, values))
                                                .decoration(
                                                        net.kyori.adventure.text.format
                                                                .TextDecoration.ITALIC,
                                                        false))
                        .toList());
        int model = config.getInt(path + ".custom-model-data", 0);
        if (model > 0) meta.setCustomModelData(model);
        if (config.getBoolean(path + ".brillant", false)) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }
}
