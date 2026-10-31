package fr.cliker.clikerv2.combat;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.*;

/** Une piste Minecraft par joueur, sans relancer la musique à chaque coup. */
public final class BossMusic {
    private BossMusic() {}

    private static final Map<UUID, Long> UNTIL = new HashMap<>();
    private static final Map<UUID, Long> LAST_SEEN = new HashMap<>();

    public static void play(Player player) {
        LAST_SEEN.put(player.getUniqueId(), System.nanoTime());
        long now = System.nanoTime();
        if (UNTIL.getOrDefault(player.getUniqueId(), 0L) > now) return;
        player.playSound(
                player.getLocation(), Sound.MUSIC_DISC_OTHERSIDE, SoundCategory.MUSIC, .45f, 1f);
        UNTIL.put(player.getUniqueId(), now + 195_000_000_000L);
    }

    public static void sweep() {
        long now = System.nanoTime();
        for (UUID id : new ArrayList<>(LAST_SEEN.keySet())) {
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player == null || now - LAST_SEEN.get(id) > 6_000_000_000L) {
                if (player != null) stop(player);
                else {
                    LAST_SEEN.remove(id);
                    UNTIL.remove(id);
                }
            }
        }
    }

    public static void stop(Player player) {
        LAST_SEEN.remove(player.getUniqueId());
        if (UNTIL.remove(player.getUniqueId()) != null)
            player.stopSound(Sound.MUSIC_DISC_OTHERSIDE, SoundCategory.MUSIC);
    }
}
