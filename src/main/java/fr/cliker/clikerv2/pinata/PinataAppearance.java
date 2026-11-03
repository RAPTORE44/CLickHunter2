package fr.cliker.clikerv2.pinata;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Llama;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.function.LongSupplier;

/** Une seule rotation globale toutes les 500 ms, même avec plusieurs attaquants. */
final class PinataAppearance {
    private static final long INTERVAL = 500_000_000L;
    private static final NamedTextColor[] COLORS = {
        NamedTextColor.YELLOW,
        NamedTextColor.LIGHT_PURPLE,
        NamedTextColor.AQUA,
        NamedTextColor.GREEN,
        NamedTextColor.RED,
        NamedTextColor.GOLD,
        NamedTextColor.BLUE
    };
    private static final DyeColor[] CARPETS = {
        DyeColor.YELLOW,
        DyeColor.MAGENTA,
        DyeColor.CYAN,
        DyeColor.LIME,
        DyeColor.RED,
        DyeColor.ORANGE,
        DyeColor.BLUE
    };
    private final LongSupplier nanos;
    private final Map<Scoreboard, Team> teams = new IdentityHashMap<>();
    private Llama entity;
    private int step;
    private long changed;
    private boolean hasChanged;
    private long viewersAt;
    private boolean hasViewers;

    PinataAppearance() {
        this(System::nanoTime);
    }

    PinataAppearance(LongSupplier nanos) {
        this.nanos = nanos;
    }

    void attach(Llama llama) {
        clear();
        entity = llama;
        step = 0;
        hasChanged = false;
        llama.setAdult();
        llama.setGlowing(true);
        applySkin();
        viewers();
    }

    boolean hit() {
        if (entity == null || !entity.isValid()) return false;
        long now = nanos.getAsLong();
        if (hasChanged && now - changed < INTERVAL) return false;
        changed = now;
        hasChanged = true;
        step++;
        applySkin();
        for (var team : teams.values()) team.color(COLORS[step % COLORS.length]);
        viewers();
        return true;
    }

    private void applySkin() {
        entity.setColor(Llama.Color.values()[step % Llama.Color.values().length]);
        entity.getInventory()
                .setDecor(
                        new ItemStack(
                                Objects.requireNonNull(
                                        Material.matchMaterial(
                                                CARPETS[step % CARPETS.length].name()
                                                        + "_CARPET"))));
    }

    // Le scoreboard HUD est propre à chaque joueur : inscrire le contour dans son tableau.
    void viewers() {
        if (entity == null || !entity.isValid()) return;
        long now = nanos.getAsLong();
        if (hasViewers && now - viewersAt < 1_000_000_000L) return;
        hasViewers = true;
        viewersAt = now;
        Set<Scoreboard> visible = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var player : Bukkit.getOnlinePlayers())
            if (player.getWorld().equals(entity.getWorld())
                    && player.getLocation().distanceSquared(entity.getLocation()) <= 128 * 128)
                visible.add(player.getScoreboard());
        var it = teams.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (!visible.contains(e.getKey())) {
                e.getValue().unregister();
                it.remove();
            }
        }
        for (var board : visible)
            if (!teams.containsKey(board)) {
                String name = "chpn_" + entity.getUniqueId().toString().substring(0, 8);
                // UUID aléatoire propre à cet événement : ne réutiliser aucune équipe d’un autre
                // plugin.
                if (board.getTeam(name) != null) continue;
                Team team = board.registerNewTeam(name);
                team.color(COLORS[step % COLORS.length]);
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                team.addEntry(entity.getUniqueId().toString());
                teams.put(board, team);
            }
    }

    void clear() {
        for (var team : teams.values()) team.unregister();
        teams.clear();
        entity = null;
        hasViewers = false;
    }
}
