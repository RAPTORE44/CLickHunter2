package fr.cliker.clikerv2.combat;

import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.daily.DailySettings;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Animation visuelle seule : aucune monnaie, aucun calcul de combat ici. */
public final class DamagePopups implements Listener {
    @FunctionalInterface
    interface DisplayFactory {
        TextDisplay spawn(
                Player owner, Location location, java.util.function.Consumer<TextDisplay> setup);
    }

    private final DisplayFactory displayFactory;
    private boolean shared;
    private final JavaPlugin plugin;
    private final boolean enabled;
    private final int duration, maximum, perPlayer;
    private final double rise, verticalOffset, spread;
    private final String text;
    private final Map<UUID, Popup> popups = new LinkedHashMap<>();
    private BukkitTask task;
    private boolean stopped;

    public DamagePopups(JavaPlugin plugin) {
        this(
                plugin,
                (owner, location, setup) -> {
                    TextDisplay display =
                            location.getWorld()
                                    .spawn(
                                            location,
                                            TextDisplay.class,
                                            entity -> {
                                                entity.setVisibleByDefault(false);
                                                setup.accept(entity);
                                            });
                    owner.showEntity(plugin, display);
                    return display;
                });
        shared = true;
    }

    DamagePopups(JavaPlugin plugin, DisplayFactory displayFactory) {
        this.displayFactory = displayFactory;
        this.plugin = plugin;
        File file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "damage-popups.yml");
        if (!file.exists())
            fr.cliker.clikerv2.config.ConfigFiles.install(plugin, "damage-popups.yml");
        var config = YamlConfiguration.loadConfiguration(file);
        enabled = config.getBoolean("active", true);
        duration = config.getInt("duree-ticks", 20);
        maximum = config.getInt("maximum-actifs", 200);
        perPlayer = config.getInt("maximum-par-joueur", 20);
        rise = config.getDouble("montee-blocs", 1);
        verticalOffset = config.getDouble("decalage-vertical", 0.5);
        spread = config.getDouble("dispersion", 0.35);
        text = config.getString("texte", "&c&l-{degats}");
        if (duration < 1
                || duration > 200
                || maximum < 1
                || maximum > 1000
                || perPlayer < 1
                || perPlayer > maximum
                || !Double.isFinite(rise)
                || rise < 0
                || rise > 5
                || !Double.isFinite(spread)
                || spread < 0
                || spread > 2
                || !Double.isFinite(verticalOffset)
                || Math.abs(verticalOffset) > 5)
            throw new IllegalArgumentException("Configuration des textes de dégâts invalide");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void show(Player player, LivingEntity victim, long damage) {
        if (!enabled
                || stopped
                || damage <= 0
                || !fr.cliker.clikerv2.comfort.PlayerComfort.enabled(player, "damage_texts", true))
            return;
        UUID owner = player.getUniqueId();
        while (popups.values().stream().filter(p -> p.owner.equals(owner)).count() >= perPlayer) {
            UUID oldest =
                    popups.entrySet().stream()
                            .filter(e -> e.getValue().owner.equals(owner))
                            .findFirst()
                            .orElseThrow()
                            .getKey();
            remove(oldest);
        }
        while (popups.size() >= maximum) remove(popups.keySet().iterator().next());
        boolean soft =
                fr.cliker.clikerv2.comfort.PlayerComfort.enabled(player, "soft_visuals", false);
        Location origin =
                victim.getLocation()
                        .add(
                                soft ? 0 : randomOffset(),
                                victim.getHeight() + verticalOffset,
                                soft ? 0 : randomOffset());
        var provider = fr.cliker.clikerv2.hologram.SharedHolograms.service();
        if (shared && provider != null && !provider.available()) return;
        if (shared && provider != null && provider.available()) {
            UUID id = UUID.randomUUID();
            var handle =
                    provider.create(
                            "damage:" + id,
                            origin,
                            java.util.List.of(text.replace("{degats}", Numbers.compact(damage))),
                            player,
                            false);
            popups.put(id, new Popup(owner, null, handle, origin));
        } else {
            TextDisplay stand =
                    displayFactory.spawn(
                            player,
                            origin,
                            entity -> {
                                entity.setGravity(false);
                                entity.setInvulnerable(true);
                                entity.setPersistent(false);
                                entity.setBillboard(Display.Billboard.CENTER);
                                entity.setShadowed(true);
                                entity.setDefaultBackground(false);
                                entity.setTeleportDuration(1);
                                entity.text(
                                        DailySettings.color(
                                                text.replace("{degats}", Numbers.compact(damage))));
                            });
            popups.put(stand.getUniqueId(), new Popup(owner, stand, null, origin));
        }
        if (task == null)
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private double randomOffset() {
        return spread == 0 ? 0 : ThreadLocalRandom.current().nextDouble(-spread, spread);
    }

    private void tick() {
        var iterator = popups.values().iterator();
        while (iterator.hasNext()) {
            Popup popup = iterator.next();
            if (!popup.valid() || ++popup.age > duration) {
                popup.remove();
                iterator.remove();
                continue;
            }
            popup.move(popup.origin.clone().add(0, rise * popup.age / duration, 0));
        }
        stopIfIdle();
    }

    private void remove(UUID id) {
        Popup popup = popups.remove(id);
        if (popup != null) popup.remove();
    }

    private void stopIfIdle() {
        if (popups.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        for (UUID id : new ArrayList<>(popups.keySet()))
            if (popups.get(id).owner.equals(event.getPlayer().getUniqueId())) remove(id);
        stopIfIdle();
    }

    @EventHandler(ignoreCancelled = true)
    public void unload(WorldUnloadEvent event) {
        for (UUID id : new ArrayList<>(popups.keySet()))
            if (popups.get(id).origin.getWorld().equals(event.getWorld())) remove(id);
        stopIfIdle();
    }

    public void shutdown() {
        stopped = true;
        for (UUID id : new ArrayList<>(popups.keySet())) remove(id);
        stopIfIdle();
        HandlerList.unregisterAll(this);
    }

    private static final class Popup {
        private final UUID owner;
        private final TextDisplay stand;
        private final fr.cliker.clikerv2.hologram.SharedHolograms.Handle hologram;
        private final Location origin;
        private int age;

        private boolean valid() {
            return hologram != null ? hologram.isValid() : stand.isValid();
        }

        private void remove() {
            if (hologram != null) hologram.remove();
            else stand.remove();
        }

        private void move(Location at) {
            if (hologram != null) hologram.setLocation(at);
            else stand.teleport(at);
        }

        private Popup(
                UUID owner,
                TextDisplay stand,
                fr.cliker.clikerv2.hologram.SharedHolograms.Handle hologram,
                Location origin) {
            this.hologram = hologram;
            this.owner = owner;
            this.stand = stand;
            this.origin = origin.clone();
        }
    }
}
