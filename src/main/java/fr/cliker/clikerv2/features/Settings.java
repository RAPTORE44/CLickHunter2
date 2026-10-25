package fr.cliker.clikerv2.features;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.*;

public final class Settings {
    public final File file;
    public final YamlConfiguration yaml;

    public Settings(JavaPlugin p, String path) {
        file = new File(p.getDataFolder(), path);
        file.getParentFile().mkdirs();
        if (!file.exists()) {
            try (var in = p.getResource(path)) {
                if (in != null) Files.copy(in, file.toPath());
                else Files.createFile(file.toPath());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        yaml = fr.cliker.clikerv2.config.YamlFiles.load(file);
    }

    public void save() {
        try {
            Path temp = Files.createTempFile(file.toPath().getParent(), "settings-", ".tmp");
            Files.writeString(temp, yaml.saveToString());
            try {
                Files.move(
                        temp,
                        file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Sauvegarde impossible : " + file, e);
        }
    }
}
