package fr.cliker.clikerv2.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Emplacements SFTP canoniques ; migration conservatrice avec copie de secours. */
public final class ConfigFiles {
    private ConfigFiles() {}

    private static final Map<String, String> PATHS =
            Map.ofEntries(
                    Map.entry("daily.yml", "recompenses/daily.yml"),
                    Map.entry("claim.yml", "recompenses/claim.yml"),
                    Map.entry("lootboxes.yml", "recompenses/lootboxes.yml"),
                    Map.entry("parrainage.yml", "progression/parrainage.yml"),
                    Map.entry("chasse.yml", "progression/chasse.yml"),
                    Map.entry("raid.yml", "activites/raid.yml"),
                    Map.entry("avantpostes.yml", "activites/avantpostes.yml"),
                    Map.entry("boss.yml", "activites/boss-zones.yml"),
                    Map.entry("buyzone.yml", "navigation/buyzone.yml"),
                    Map.entry("damage-popups.yml", "affichage/damage-popups.yml"),
                    Map.entry("scoreboard.yml", "affichage/scoreboard.yml"),
                    Map.entry("enchants.yml", "equipement/enchants.yml"),
                    Map.entry("discord.yml", "pnj/discord.yml"));

    public static File file(JavaPlugin p, String name) {
        String path = PATHS.getOrDefault(name, name);
        File result = new File(p.getDataFolder(), path);
        if (!path.equals(name)) {
            File legacy = new File(p.getDataFolder(), name);
            if (!result.exists() && legacy.exists())
                try {
                    Files.createDirectories(result.toPath().getParent());
                    Path backup =
                            p.getDataFolder()
                                    .toPath()
                                    .resolve("migration-configs")
                                    .resolve(System.currentTimeMillis() + "-" + name);
                    Files.createDirectories(backup.getParent());
                    Files.copy(legacy.toPath(), backup);
                    Files.move(legacy.toPath(), result.toPath());
                    p.getLogger()
                            .info(
                                    "Configuration déplacée : "
                                            + name
                                            + " → "
                                            + path
                                            + " (copie de secours conservée)");
                } catch (IOException e) {
                    throw new IllegalStateException("Migration impossible de " + name, e);
                }
        }
        try {
            Files.createDirectories(result.toPath().getParent());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    public static void install(JavaPlugin p, String name) {
        File file = file(p, name);
        if (file.exists()) return;
        try (var in = p.getResource(name)) {
            if (in == null) throw new IOException("Ressource introuvable : " + name);
            Files.createDirectories(file.toPath().getParent());
            Files.copy(in, file.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Installation impossible de " + name, e);
        }
    }

    public static Map<String, String> paths() {
        return PATHS;
    }
}
