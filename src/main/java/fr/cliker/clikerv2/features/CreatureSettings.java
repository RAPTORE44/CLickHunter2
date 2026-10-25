package fr.cliker.clikerv2.features;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CreatureSettings {
    private static YamlConfiguration config = new YamlConfiguration();

    private CreatureSettings() {}

    public static void initialize(JavaPlugin plugin) {
        var next = new Settings(plugin, "mobs/creatures.yml").yaml;
        for (var mob : MobTier.values()) {
            for (String field :
                    new String[] {
                        "pv",
                        "prix-vente",
                        "degats-piece",
                        "degats-amulette",
                        "ecus",
                        "gemmes-dixiemes",
                        "xp",
                        "taille",
                        "echelle"
                    })
                if (next.contains("creatures." + mob.id() + "." + field)) {
                    double value = next.getDouble("creatures." + mob.id() + "." + field);
                    if (!Double.isFinite(value)
                            || value < 0
                            || value > 1_000_000_000_000_000L
                            || (field.equals("pv") && value < 1))
                        throw new IllegalArgumentException(
                                "Valeur invalide : " + mob.id() + "." + field);
                }
        }
        config = next;
    }

    public static double value(MobTier mob, String field, double fallback) {
        return config.getDouble("creatures." + mob.id() + "." + field, fallback);
    }

    public static String text(MobTier mob, String field, String fallback) {
        return config.getString("creatures." + mob.id() + "." + field, fallback);
    }

    public static String hologram(boolean boss) {
        return config.getString(
                boss ? "hologrammes.boss" : "hologrammes.mob",
                boss ? "&4&l⚔ Golem {nom} ⚔ &c[{pv}/{pv-max}]" : "&6&l{nom} &c[{pv}/{pv-max}]");
    }

    public static void reset() {
        config = new YamlConfiguration();
    }
}
