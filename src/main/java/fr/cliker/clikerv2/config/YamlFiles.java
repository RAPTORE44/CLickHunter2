package fr.cliker.clikerv2.config;

import org.bukkit.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Conserver les positions même si leur monde n'est pas encore chargé. */
public final class YamlFiles {
    private YamlFiles() {}

    public static YamlConfiguration load(File file) {
        try {
            String yaml =
                    Files.readString(file.toPath(), StandardCharsets.UTF_8)
                            .replaceAll("(?m)^[ \\t]*==: org\\.bukkit\\.Location[ \\t]*$", "");
            var config = new YamlConfiguration();
            config.loadFromString(yaml);
            return config;
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalArgumentException(
                    "Configuration illisible : " + file.getName(), error);
        }
    }

    public static Location location(YamlConfiguration config, String path) {
        Object value = config.get(path);
        if (value instanceof Location location)
            return finite(location) ? location.clone() : invalid(path);
        var section = config.getConfigurationSection(path);
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) return null;
        if (!(section.get("x") instanceof Number)
                || !(section.get("y") instanceof Number)
                || !(section.get("z") instanceof Number)
                || (section.contains("yaw") && !(section.get("yaw") instanceof Number))
                || (section.contains("pitch") && !(section.get("pitch") instanceof Number)))
            return invalid(path);
        Location location =
                new Location(
                        world,
                        section.getDouble("x"),
                        section.getDouble("y"),
                        section.getDouble("z"),
                        (float) section.getDouble("yaw"),
                        (float) section.getDouble("pitch"));
        return finite(location) ? location : invalid(path);
    }

    private static boolean finite(Location location) {
        return location.getWorld() != null
                && Double.isFinite(location.getX())
                && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ())
                && Float.isFinite(location.getYaw())
                && Float.isFinite(location.getPitch());
    }

    private static Location invalid(String path) {
        Bukkit.getLogger()
                .warning(
                        "[ClikerV2] Position invalide ignorée et conservée dans le fichier : "
                                + path);
        return null;
    }

    public static void location(YamlConfiguration config, String path, Location location) {
        if (location != null && !finite(location))
            throw new IllegalArgumentException("Position invalide : " + path);
        config.set(path, null);
        if (location == null) return;
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", location.getYaw());
        config.set(path + ".pitch", location.getPitch());
    }
}
