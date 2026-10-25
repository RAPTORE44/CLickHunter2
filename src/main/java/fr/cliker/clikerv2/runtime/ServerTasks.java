package fr.cliker.clikerv2.runtime;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

/** Les résultats SQL tardifs ne planifient rien après l'arrêt de leur plugin. */
public final class ServerTasks {
    private ServerTasks() {}

    public static void run(Plugin plugin, Runnable task) {
        if (!plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (plugin.isEnabled()) task.run();
                            });
        } catch (IllegalPluginAccessException error) {
            if (plugin.isEnabled()) throw error;
        }
    }
}
