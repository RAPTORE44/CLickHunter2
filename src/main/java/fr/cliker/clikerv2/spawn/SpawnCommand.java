package fr.cliker.clikerv2.spawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnCommand implements CommandExecutor {
    private static final long WARMUP_TICKS = 60L;

    private final JavaPlugin plugin;
    private final SpawnManager manager;

    public SpawnCommand(JavaPlugin plugin, SpawnManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Commande réservée aux joueurs.", NamedTextColor.RED)); return true; }
        if (!manager.hasSpawn()) {
            player.sendMessage(Component.text("Le spawn n'a pas encore été défini (/setspawn).", NamedTextColor.RED));
            return true;
        }

        Location start = player.getLocation();
        player.sendMessage(Component.text("Téléportation au spawn dans 3s, ne bouge pas...", NamedTextColor.YELLOW));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Location current = player.getLocation();
            if (current.getWorld() != start.getWorld() || current.distanceSquared(start) > 0.09) {
                player.sendMessage(Component.text("Téléportation annulée, tu as bougé.", NamedTextColor.RED));
                return;
            }
            if (manager.teleport(player)) {
                player.sendMessage(Component.text("✦ Téléporté au spawn.", NamedTextColor.GREEN));
            }
        }, WARMUP_TICKS);
        return true;
    }
}
