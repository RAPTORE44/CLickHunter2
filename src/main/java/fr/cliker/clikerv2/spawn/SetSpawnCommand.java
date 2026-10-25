package fr.cliker.clikerv2.spawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SetSpawnCommand implements CommandExecutor {
    private final SpawnManager manager;
    private final org.bukkit.plugin.java.JavaPlugin plugin;

    public SetSpawnCommand(org.bukkit.plugin.java.JavaPlugin plugin, SpawnManager manager) {
        this.manager = manager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    Component.text("Commande réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("cliker.spawn.admin")) {
            sender.sendMessage(
                    Component.text("Permission administrateur requise.", NamedTextColor.RED));
            return true;
        }
        manager.setSpawn(player.getLocation())
                .whenComplete(
                        (saved, error) -> {
                            if (!plugin.isEnabled()) return;
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        if (player.isOnline())
                                            player.sendMessage(
                                                    Component.text(
                                                            Boolean.TRUE.equals(saved)
                                                                    ? "Point d’accueil sauvegardé"
                                                                            + " ici."
                                                                    : "Sauvegarde impossible."
                                                                            + " L’ancien point"
                                                                            + " d’accueil est"
                                                                            + " conservé.",
                                                            Boolean.TRUE.equals(saved)
                                                                    ? NamedTextColor.GREEN
                                                                    : NamedTextColor.RED));
                                    });
                        });
        return true;
    }
}
