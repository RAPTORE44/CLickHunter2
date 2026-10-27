package fr.cliker.clikerv2.hologram;

import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.features.Settings;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.util.*;

/** Fournisseur commun : API Holographic Displays 3, avec secours natif configurable. */
public final class SharedHolograms {
    private static SharedHolograms service;
    private final JavaPlugin plugin;
    private final Map<String, Handle> handles = new HashMap<>();
    private Object api;
    private org.bukkit.scheduler.BukkitTask repairTask;
    private final boolean nativeFallback;

    public SharedHolograms(JavaPlugin plugin) {
        this.plugin = plugin;
        nativeFallback =
                new Settings(plugin, "affichage/hologrammes.yml")
                        .yaml.getBoolean("secours-natif", true);
        if (Bukkit.getPluginManager().isPluginEnabled("HolographicDisplays"))
            try {
                api =
                        Class.forName("me.filoghost.holographicdisplays.api.HolographicDisplaysAPI")
                                .getMethod("get", org.bukkit.plugin.Plugin.class)
                                .invoke(null, plugin);
            } catch (ReflectiveOperationException | LinkageError e) {
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.WARNING,
                                "API Holographic Displays 3 indisponible",
                                e);
            }
        if (api == null)
            plugin.getLogger()
                    .warning(
                            "Holographic Displays compatible requis pour son rendu ; secours natif "
                                    + (nativeFallback ? "actif" : "désactivé")
                                    + ".");
        service = this;
        repairTask =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (api != null || !nativeFallback) return;
                                    for (var handle : new ArrayList<>(handles.values()))
                                        if (!handle.removed
                                                && handle.display != null
                                                && !handle.display.isValid()
                                                && handle.location
                                                        .getWorld()
                                                        .isChunkLoaded(
                                                                handle.location.getBlockX() >> 4,
                                                                handle.location.getBlockZ() >> 4)) {
                                            var lines = handle.rendered;
                                            handle.spawnNative();
                                            handle.rendered = List.of();
                                            handle.setLines(lines);
                                        }
                                },
                                40,
                                40);
    }

    public static SharedHolograms service() {
        return service;
    }

    public boolean usesHolographicDisplays() {
        return api != null;
    }

    public boolean available() {
        return api != null || nativeFallback;
    }

    public Handle get(String id) {
        return handles.get(id);
    }

    public Handle create(String id, Location at, List<String> lines, Player owner, boolean fixed) {
        remove(id);
        Handle handle = new Handle(id, at, owner, fixed);
        try {
            if (api != null) {
                handle.external = call(api, "createHologram", at);
                if (owner != null) {
                    Object visibility = call(handle.external, "getVisibilitySettings");
                    Class<?> type =
                            Class.forName(
                                    "me.filoghost.holographicdisplays.api.hologram.VisibilitySettings$Visibility");
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Object hidden = Enum.valueOf((Class) type, "HIDDEN");
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Object visible = Enum.valueOf((Class) type, "VISIBLE");
                    call(visibility, "setGlobalVisibility", hidden);
                    call(visibility, "setIndividualVisibility", owner, visible);
                }
            } else if (nativeFallback) {
                handle.spawnNative();
            }
            handles.put(id, handle);
            handle.setLines(lines);
            return handle;
        } catch (RuntimeException | ReflectiveOperationException e) {
            handle.remove();
            if (api != null && nativeFallback) {
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.WARNING,
                                "Holographic Displays ne peut pas créer les hologrammes ;"
                                        + " activation du secours natif",
                                e);
                api = null;
                return create(id, at, lines, owner, fixed);
            }
            throw new IllegalStateException("Création hologramme impossible : " + id, e);
        }
    }

    public void removeLegacyLeaderboard(String id) {
        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) return;
        try {
            Class.forName("eu.decentsoftware.holograms.api.DHAPI")
                    .getMethod("removeHologram", String.class)
                    .invoke(null, id);
        } catch (ReflectiveOperationException | LinkageError error) {
            plugin.getLogger().warning("Ancien hologramme à retirer manuellement : " + id);
        }
    }

    public void remove(String id) {
        var old = handles.remove(id);
        if (old != null) old.remove();
    }

    public void shutdown() {
        if (repairTask != null) repairTask.cancel();
        for (var handle : new ArrayList<>(handles.values())) handle.remove();
        handles.clear();
        if (service == this) service = null;
    }

    private static Object call(Object target, String name, Object... args) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length)
                continue;
            boolean matches = true;
            for (int i = 0; i < args.length; i++)
                if (args[i] != null && !method.getParameterTypes()[i].isInstance(args[i]))
                    matches = false;
            if (!matches) continue;
            try {
                method.trySetAccessible();
                return method.invoke(target, args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Holographic Displays : " + name, e);
            }
        }
        throw new IllegalStateException("Méthode Holographic Displays absente : " + name);
    }

    public final class Handle {
        private final String id;
        private Location location;
        private final Player owner;
        private final boolean fixed;
        private Object external;
        private TextDisplay display;
        private boolean removed;
        private List<String> rendered = List.of();

        private Handle(String id, Location at, Player owner, boolean fixed) {
            this.owner = owner;
            this.fixed = fixed;
            this.id = id;
            location = at.clone();
        }

        private void spawnNative() {
            display =
                    location.getWorld()
                            .spawn(
                                    location,
                                    TextDisplay.class,
                                    entity -> {
                                        entity.setPersistent(false);
                                        entity.setGravity(false);
                                        entity.setInvulnerable(true);
                                        entity.setBillboard(
                                                fixed
                                                        ? Display.Billboard.FIXED
                                                        : Display.Billboard.CENTER);
                                        entity.setDefaultBackground(false);
                                        entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                                        entity.setShadowed(true);
                                        entity.setTeleportDuration(1);
                                        if (owner != null) entity.setVisibleByDefault(false);
                                    });
            if (owner != null && owner.isOnline()) owner.showEntity(plugin, display);
        }

        public boolean isValid() {
            return !removed
                    && (external != null
                            ? !Boolean.TRUE.equals(call(external, "isDeleted"))
                            : display != null && display.isValid());
        }

        public Location getLocation() {
            return location.clone();
        }

        public void setLocation(Location at) {
            if (location.equals(at)) return;
            location = at.clone();
            if (external != null) call(external, "setPosition", at);
            else if (display != null) display.teleport(at);
        }

        public void setLines(List<String> lines) {
            if (removed || lines.equals(rendered)) return;
            if (external != null) {
                Object list = call(external, "getLines");
                call(list, "clear");
                for (String line : lines)
                    call(
                            list,
                            "appendText",
                            org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            } else if (display != null) display.text(DailySettings.color(String.join("\n", lines)));
            rendered = List.copyOf(lines);
        }

        public void remove() {
            if (removed) return;
            removed = true;
            if (external != null && !Boolean.TRUE.equals(call(external, "isDeleted")))
                call(external, "delete");
            if (display != null) display.remove();
            handles.remove(id, this);
        }
    }
}
