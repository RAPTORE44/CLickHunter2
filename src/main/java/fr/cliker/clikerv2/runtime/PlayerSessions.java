package fr.cliker.clikerv2.runtime;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Une référence Player d'une ancienne connexion ne doit jamais recevoir un résultat SQL tardif. */
public final class PlayerSessions {
    private PlayerSessions() {}

    public static boolean isCurrent(Player player) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }
}
