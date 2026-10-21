package fr.cliker.clikerv2.spawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SetSpawnCommand implements CommandExecutor {
    private final SpawnManager manager;

    public SetSpawnCommand(SpawnManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Commande réservée aux joueurs.", NamedTextColor.RED)); return true; }
        manager.setSpawn(player.getLocation());
        player.sendMessage(Component.text("✦ Spawn défini ici.", NamedTextColor.GREEN));
        return true;
    }
}
