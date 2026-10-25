package fr.cliker.clikerv2.cooldown;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Impose un délai de 3s entre deux usages de la même commande par un joueur, avant de déléguer à
 * l'exécuteur réel.
 */
public final class CooldownCommand implements CommandExecutor {
    private static final long COOLDOWN_MILLIS = 3000L;

    private final CommandExecutor delegate;
    private long nextCleanup;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public CooldownCommand(CommandExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            long now = System.currentTimeMillis();
            if (now >= nextCleanup) {
                lastUse.values().removeIf(time -> now - time >= COOLDOWN_MILLIS);
                nextCleanup = now + 60000;
            }
            Long last = lastUse.get(player.getUniqueId());
            if (last != null && now - last < COOLDOWN_MILLIS) {
                long remainingSeconds = (COOLDOWN_MILLIS - (now - last) + 999) / 1000;
                player.sendMessage(
                        Component.text(
                                "Attends encore "
                                        + remainingSeconds
                                        + "s avant de refaire /"
                                        + label
                                        + ".",
                                NamedTextColor.RED));
                return true;
            }
            lastUse.put(player.getUniqueId(), now);
        }
        return delegate.onCommand(sender, command, label, args);
    }
}
