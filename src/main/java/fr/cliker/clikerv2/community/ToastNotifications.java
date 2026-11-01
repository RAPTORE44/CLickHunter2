package fr.cliker.clikerv2.community;

import fr.cliker.clikerv2.features.Settings;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Succès éphémères privés : pas d'annonce serveur ni de progression vanilla. */
public final class ToastNotifications implements Listener {
    private static ToastNotifications current;

    public record Notice(Material icon, String title, String description) {}

    private final JavaPlugin plugin;
    private final Settings settings;
    private final Map<UUID, ArrayDeque<Notice>> queues = new HashMap<>();
    private final Map<UUID, Long> last = new HashMap<>();
    private final Map<NamespacedKey, UUID> active = new HashMap<>();
    private final BukkitTask task;
    private boolean supported = true;

    public ToastNotifications(JavaPlugin plugin) {
        this.plugin = plugin;
        settings = new Settings(plugin, "communaute/notifications.yml");
        current = this;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    public static void show(Player p, Material icon, String title, String description) {
        var service = current;
        if (service == null
                || p == null
                || !p.isOnline()
                || AfkModule.isolated(p)
                || !service.settings.yaml.getBoolean("actif", true)) return;
        var q = service.queues.computeIfAbsent(p.getUniqueId(), id -> new ArrayDeque<>());
        var n = new Notice(icon, title, description);
        if (q.size() < 5 && !q.contains(n)) q.addLast(n);
    }

    public static String json(Notice notice) {
        var gson = new com.google.gson.Gson();
        return "{\"display\":{\"icon\":{\"id\":"
                + gson.toJson(notice.icon().getKey().toString())
                + "},\"title\":{\"text\":"
                + gson.toJson(notice.title())
                + "},\"description\":{\"text\":"
                + gson.toJson(notice.description())
                + "},\"frame\":\"task\",\"show_toast\":true,\"announce_to_chat\":false,\"hidden\":true},\"criteria\":{\"notification\":{\"trigger\":\"minecraft:impossible\"}}}";
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(queues.keySet())) {
            var p = Bukkit.getPlayer(id);
            var q = queues.get(id);
            if (p == null || q == null || q.isEmpty()) {
                queues.remove(id);
                continue;
            }
            if (AfkModule.isolated(p)
                    || now - last.getOrDefault(id, 0L)
                            < Math.max(4, settings.yaml.getInt("intervalle-secondes", 8)) * 1000L)
                continue;
            Notice n = q.removeFirst();
            last.put(id, now);
            if (!supported) {
                p.showTitle(
                        net.kyori.adventure.title.Title.title(
                                net.kyori.adventure.text.Component.text(n.title()),
                                net.kyori.adventure.text.Component.text(n.description())));
                continue;
            }
            var key = new NamespacedKey(plugin, "notification/" + UUID.randomUUID());
            try {
                var advancement = Bukkit.getUnsafe().loadAdvancement(key, json(n));
                if (advancement == null) throw new IllegalStateException("Succès non créé");
                active.put(key, id);
                p.getAdvancementProgress(advancement).awardCriteria("notification");
                Bukkit.getScheduler().runTaskLater(plugin, () -> remove(key), 80);
            } catch (RuntimeException | LinkageError error) {
                remove(key);
                supported = false;
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.WARNING,
                                "Toasts indisponibles : secours par titre privé",
                                error);
                p.showTitle(
                        net.kyori.adventure.title.Title.title(
                                net.kyori.adventure.text.Component.text(n.title()),
                                net.kyori.adventure.text.Component.text(n.description())));
            }
        }
    }

    private void remove(NamespacedKey key) {
        UUID owner = active.remove(key);
        var a = Bukkit.getAdvancement(key);
        var p = owner == null ? null : Bukkit.getPlayer(owner);
        if (p != null && a != null)
            for (String criterion : a.getCriteria())
                p.getAdvancementProgress(a).revokeCriteria(criterion);
        Bukkit.getUnsafe().removeAdvancement(key);
    }

    @EventHandler
    public void level(PlayerLevelChangeEvent e) {
        if (e.getNewLevel() > e.getOldLevel()
                && e.getNewLevel() % 10 == 0
                && settings.yaml.getBoolean("evenements.niveaux", true))
            show(
                    e.getPlayer(),
                    Material.EXPERIENCE_BOTTLE,
                    "Niveau " + e.getNewLevel() + " atteint !",
                    "Continue ta progression sur ClickHunter.");
    }

    @EventHandler
    public void welcome(PlayerJoinEvent e) {
        if (!e.getPlayer().hasPlayedBefore()
                && settings.yaml.getBoolean("evenements.bienvenue", true))
            show(
                    e.getPlayer(),
                    Material.NETHER_STAR,
                    "Bienvenue sur ClickHunter !",
                    "Le tutoriel t’accompagne pour débuter.");
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        queues.remove(id);
        last.remove(id);
        for (var key : new ArrayList<>(active.keySet()))
            if (id.equals(active.get(key))) remove(key);
    }

    public void shutdown() {
        task.cancel();
        for (var key : new ArrayList<>(active.keySet())) remove(key);
        queues.clear();
        last.clear();
        HandlerList.unregisterAll(this);
        if (current == this) current = null;
    }
}
