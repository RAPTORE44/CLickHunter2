package fr.cliker.clikerv2.combat;

import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.daily.DailySettings;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
    private final JavaPlugin plugin;
    private final boolean enabled;
    private final int duration, maximum, perPlayer;
    private final double rise, verticalOffset, spread;
    private final String text;
    private final Map<UUID, Popup> popups = new LinkedHashMap<>();
    private BukkitTask task;
    private boolean stopped;

    public DamagePopups(JavaPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "damage-popups.yml");
        if (!file.exists()) plugin.saveResource("damage-popups.yml", false);
        var config = YamlConfiguration.loadConfiguration(file);
        enabled = config.getBoolean("active", true);
        duration = config.getInt("duree-ticks", 20);
        maximum = config.getInt("maximum-actifs", 200);
        perPlayer = config.getInt("maximum-par-joueur", 20);
        rise = config.getDouble("montee-blocs", 1);
        verticalOffset = config.getDouble("decalage-vertical", 0.5);
        spread = config.getDouble("dispersion", 0.35);
        text = config.getString("texte", "&c&l-{degats}");
        if (duration < 1 || duration > 200 || maximum < 1 || maximum > 1000 || perPlayer < 1 || perPlayer > maximum
                || !Double.isFinite(rise) || rise < 0 || rise > 5 || !Double.isFinite(spread) || spread < 0 || spread > 2
                || !Double.isFinite(verticalOffset) || Math.abs(verticalOffset) > 5) throw new IllegalArgumentException("Configuration des textes de dégâts invalide");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    public void show(Player player, LivingEntity victim, long damage) {
        if (!enabled || stopped || damage <= 0) return;
        UUID owner = player.getUniqueId();
        while (popups.values().stream().filter(p -> p.owner.equals(owner)).count() >= perPlayer) {
            UUID oldest = popups.entrySet().stream().filter(e -> e.getValue().owner.equals(owner)).findFirst().orElseThrow().getKey();
            remove(oldest);
        }
        while (popups.size() >= maximum) remove(popups.keySet().iterator().next());
        Location origin = victim.getLocation().add(randomOffset(), victim.getHeight() + verticalOffset, randomOffset());
        ArmorStand stand = victim.getWorld().spawn(origin, ArmorStand.class, entity -> {
            entity.setVisible(false); entity.setGravity(false); entity.setMarker(true);
            entity.setInvulnerable(true); entity.setCollidable(false);
            entity.setCustomNameVisible(true); entity.setPersistent(false);
            entity.customName(DailySettings.color(text.replace("{degats}", Numbers.compact(damage))));
        });
        popups.put(stand.getUniqueId(), new Popup(owner, stand, origin));
        if (task == null) task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }
    private double randomOffset() { return spread == 0 ? 0 : ThreadLocalRandom.current().nextDouble(-spread, spread); }
    private void tick() {
        var iterator = popups.values().iterator();
        while (iterator.hasNext()) {
            Popup popup = iterator.next();
            if (!popup.stand.isValid() || ++popup.age > duration) {
                popup.stand.remove(); iterator.remove(); continue;
            }
            popup.stand.teleport(popup.origin.clone().add(0, rise * popup.age / duration, 0));
        }
        stopIfIdle();
    }
    private void remove(UUID id) {
        Popup popup = popups.remove(id);
        if (popup != null) popup.stand.remove();
    }
    private void stopIfIdle() { if (popups.isEmpty() && task != null) { task.cancel(); task = null; } }
    @EventHandler public void quit(PlayerQuitEvent event) {
        for (UUID id : new ArrayList<>(popups.keySet())) if (popups.get(id).owner.equals(event.getPlayer().getUniqueId())) remove(id);
        stopIfIdle();
    }
    @EventHandler(ignoreCancelled = true) public void unload(WorldUnloadEvent event) {
        for (UUID id : new ArrayList<>(popups.keySet())) if (popups.get(id).origin.getWorld().equals(event.getWorld())) remove(id);
        stopIfIdle();
    }
    public void shutdown() {
        stopped = true;
        for (UUID id : new ArrayList<>(popups.keySet())) remove(id);
        stopIfIdle(); HandlerList.unregisterAll(this);
    }
    private static final class Popup {
        private final UUID owner;
        private final ArmorStand stand;
        private final Location origin;
        private int age;
        private Popup(UUID owner, ArmorStand stand, Location origin) { this.owner = owner; this.stand = stand; this.origin = origin.clone(); }
    }
}
