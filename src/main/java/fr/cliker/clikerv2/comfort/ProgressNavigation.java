package fr.cliker.clikerv2.comfort;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.buyzone.BuyZoneModule;
import fr.cliker.clikerv2.mobs.CustomMobManager;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Navigation uniquement vers des zones effectivement autorisées et configurées. */
public final class ProgressNavigation implements CommandExecutor, Listener {
    private record Display(BossBar bar, BukkitTask expiry) {}

    private final Map<UUID, Display> bars = new HashMap<>();
    private final JavaPlugin plugin;
    private final BuyZoneModule zones;
    private final CustomMobManager mobs;

    public ProgressNavigation(JavaPlugin plugin, BuyZoneModule zones, CustomMobManager mobs) {
        this.plugin = plugin;
        this.zones = zones;
        this.mobs = mobs;
    }

    public void start() {
        for (String name : List.of("zone", "boss", "progression")) {
            var cmd = plugin.getCommand(name);
            if (cmd != null) cmd.setExecutor(this);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (!player.hasPermission("cliker.navigation.use")) {
            message(player, "Tu n’as pas la permission.");
            return true;
        }
        if (zones == null || mobs == null) {
            message(player, "Navigation temporairement indisponible.");
            return true;
        }
        String cmd = command.getName();
        if (cmd.equals("progression") && args.length == 0) {
            progress(player);
            return true;
        }
        if (cmd.equals("boss") && args.length == 0) {
            for (int n = MobTier.values().length - 1; n >= 0; n--) {
                var tier = MobTier.values()[n];
                if (zones.canTravel(player, tier.id())
                        && mobs.travelPoint(tier, false).isPresent()) {
                    travel(player, tier, true);
                    return true;
                }
            }
            message(player, "Aucune zone accessible n’a de point d’apparition configuré.");
            return true;
        }
        if (cmd.equals("zone") && args.length == 1) {
            try {
                int number = Integer.parseInt(args[0]);
                if (number >= 1 && number <= MobTier.values().length) {
                    var tier = MobTier.values()[number - 1];
                    if (!zones.canTravel(player, tier.id())) {
                        message(player, "Cette zone n’est pas débloquée.");
                        return true;
                    }
                    travel(player, tier, false);
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        message(
                player,
                cmd.equals("zone")
                        ? "Usage : /zone <numéro> (1 à " + MobTier.values().length + ")."
                        : "Usage : /" + cmd);
        return true;
    }

    private void travel(Player player, MobTier tier, boolean boss) {
        var spawn = mobs.travelPoint(tier, boss);
        if (spawn.isEmpty()) {
            message(
                    player,
                    "Aucun point " + (boss ? "de boss " : "") + "n’est configuré pour cette zone.");
            return;
        }
        Location target = spawn.get().add(0, 0, -5);
        target.setYaw(0);
        target.setPitch(0);
        // Ne pas déposer un joueur dans le vide, un liquide ou un bloc ; l'admin corrige le
        // terrain.
        for (int offset : new int[] {0, 1, -1, 2, -2, 3, -3}) {
            Location at = target.clone().add(0, offset, 0);
            var floor = at.getBlock().getRelative(0, -1, 0);
            if (!at.getBlock().isPassable()
                    || !at.getBlock().getRelative(0, 1, 0).isPassable()
                    || at.getBlock().isLiquid()
                    || at.getBlock().getRelative(0, 1, 0).isLiquid()
                    || !floor.getType().isSolid()
                    || Set.of(
                                    Material.MAGMA_BLOCK,
                                    Material.CACTUS,
                                    Material.CAMPFIRE,
                                    Material.SOUL_CAMPFIRE)
                            .contains(floor.getType())
                    || Set.of(Material.FIRE, Material.SOUL_FIRE).contains(at.getBlock().getType()))
                continue;
            if (player.teleport(at))
                player.sendMessage(
                        Component.text(
                                "Zone " + (tier.ordinal() + 1) + " — " + tier.label(),
                                NamedTextColor.GREEN));
            else message(player, "Téléportation refusée par une protection du serveur.");
            return;
        }
        message(player, "Le point d’arrivée n’est pas sûr. Préviens un administrateur.");
    }

    private void progress(Player player) {
        int last = 0, highest = 0;
        for (var tier : MobTier.values()) {
            if (mobs.travelPoint(tier, false).isEmpty()) continue;
            last = Math.max(last, tier.ordinal() + 1);
            if (zones.canTravel(player, tier.id())) highest = Math.max(highest, tier.ordinal() + 1);
        }
        clear(player);
        float value = last == 0 ? 0 : Math.min(1f, (float) highest / last);
        var bar =
                BossBar.bossBar(
                        Component.text(
                                String.format(
                                        Locale.FRANCE,
                                        "Progression des zones : %.1f %% — Zone %d/%d",
                                        value * 100,
                                        highest,
                                        last)),
                        value,
                        BossBar.Color.GREEN,
                        BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        BukkitTask expiry =
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> clear(player), 200L);
        bars.put(player.getUniqueId(), new Display(bar, expiry));
    }

    private static void message(Player player, String text) {
        player.sendMessage(Component.text(text, NamedTextColor.YELLOW));
    }

    private void clear(Player player) {
        var display = bars.remove(player.getUniqueId());
        if (display != null) {
            display.expiry.cancel();
            player.hideBossBar(display.bar);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    public void shutdown() {
        for (var player : Bukkit.getOnlinePlayers()) clear(player);
        bars.values().forEach(display -> display.expiry.cancel());
        bars.clear();
        HandlerList.unregisterAll(this);
    }
}
