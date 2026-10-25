package fr.cliker.clikerv2.npc;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.util.function.Consumer;
import java.util.logging.Level;

/** PNJ par paquets ZNPCsPlus 2.0.0 ; sauvegarde de référence conservée dans ClikerV2. */
public class ZnpcsNpc implements Listener {
    private final JavaPlugin plugin;
    private final YamlConfiguration config;
    private final Runnable save;
    private final Consumer<Player> action;
    private final String id;
    private final String legacyOwner;
    private Object api, registry;
    private boolean ready, stopped;

    public ZnpcsNpc(
            JavaPlugin plugin,
            YamlConfiguration config,
            Runnable save,
            Consumer<Player> action,
            String ownerKey,
            String internalName) {
        this.plugin = plugin;
        this.config = config;
        this.save = save;
        this.action = action;
        this.legacyOwner = ownerKey;
        id =
                "clickhunter-"
                        + ownerKey.toLowerCase(java.util.Locale.ROOT)
                                .replaceAll("[^a-z0-9_-]", "-");
    }

    public boolean available() {
        return ready && !stopped;
    }

    public void start() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ZNPCsPlus")) {
            plugin.getLogger()
                    .info(
                            "ZNPCsPlus absent : PNJ indisponibles, commandes des modules"
                                    + " accessibles.");
            return;
        }
        try {
            api =
                    Class.forName("lol.pyr.znpcsplus.api.NpcApiProvider")
                            .getMethod("get")
                            .invoke(null);
            registry = call(api, "getNpcRegistry");
            var eventClass =
                    Class.forName("lol.pyr.znpcsplus.api.event.NpcInteractEvent")
                            .asSubclass(Event.class);
            plugin.getServer()
                    .getPluginManager()
                    .registerEvent(
                            eventClass,
                            this,
                            EventPriority.NORMAL,
                            (listener, event) -> {
                                try {
                                    if (!id.equals(call(call(event, "getEntry"), "getId"))) return;
                                    if (!"RIGHT_CLICK"
                                            .equals(call(event, "getClickType").toString())) return;
                                    if (event instanceof Cancellable c) c.setCancelled(true);
                                    Player player = (Player) call(event, "getPlayer");
                                    fr.cliker.clikerv2.runtime.ServerTasks.run(
                                            plugin,
                                            () -> {
                                                if (available()
                                                        && config.getBoolean("pnj.actif", false)
                                                        && fr.cliker.clikerv2.runtime.PlayerSessions
                                                                .isCurrent(player))
                                                    action.accept(player);
                                            });
                                } catch (Exception error) {
                                    plugin.getLogger()
                                            .log(Level.SEVERE, "Clic ZNPCsPlus impossible", error);
                                }
                            },
                            plugin,
                            true);
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            ready = true;
            fr.cliker.clikerv2.runtime.ServerTasks.run(
                    plugin,
                    () -> {
                        try {
                            restore();
                        } catch (Exception error) {
                            plugin.getLogger()
                                    .log(Level.SEVERE, "Restauration ZNPCsPlus impossible", error);
                        }
                    });
        } catch (Exception | LinkageError error) {
            ready = false;
            plugin.getLogger().log(Level.SEVERE, "API ZNPCsPlus incompatible", error);
        }
    }

    private Location anchor() {
        return fr.cliker.clikerv2.config.YamlFiles.location(config, "pnj.position");
    }

    private void restore() throws Exception {
        if (!available()) return;
        removeLegacyCitizens();
        if (!config.getBoolean("pnj.actif", false)) {
            delete();
            return;
        }
        Location at = anchor();
        if (at == null) {
            plugin.getLogger()
                    .warning("PNJ " + id + " en attente de son monde ; position conservée.");
            return;
        }
        create(at);
    }

    private void removeLegacyCitizens() throws Exception {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Citizens")) return;
        Object old =
                Class.forName("net.citizensnpcs.api.CitizensAPI")
                        .getMethod("getNPCRegistry")
                        .invoke(null);
        var owned = new java.util.ArrayList<Object>();
        for (Object npc : (Iterable<?>) old)
            if (Boolean.TRUE.equals(call(call(npc, "data"), "get", legacyOwner, false)))
                owned.add(npc);
        for (Object npc : owned) call(npc, "destroy");
        if (!owned.isEmpty()) call(old, "saveToStore");
    }

    private void property(Object npc, String name, Object value) throws Exception {
        Object property = call(call(api, "getPropertyRegistry"), "getByName", name);
        if (property == null)
            throw new IllegalStateException("Propriété ZNPCsPlus absente : " + name);
        call(npc, "setProperty", property, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void create(Location at) throws Exception {
        at = at.clone();
        at.setYaw(90);
        at.setPitch(0);
        Object entry = call(registry, "getById", id);
        boolean created = entry == null;
        if (entry == null) {
            Object type =
                    call(
                            call(api, "getNpcTypeRegistry"),
                            "getByName",
                            config.getString("pnj.type", "player"));
            Object location =
                    Class.forName("lol.pyr.znpcsplus.util.NpcLocation")
                            .getConstructor(Location.class)
                            .newInstance(at);
            entry = call(registry, "create", id, at.getWorld(), type, location);
        }
        try {
            call(entry, "setSave", false); // La position est déjà conservée par ClikerV2.
            call(entry, "setAllowCommandModification", false);
            Object npc = call(entry, "getNpc");
            call(npc, "setWorld", at.getWorld());
            call(
                    npc,
                    "setLocation",
                    Class.forName("lol.pyr.znpcsplus.util.NpcLocation")
                            .getConstructor(Location.class)
                            .newInstance(at));
            call(npc, "clearActions");
            Object fixed =
                    Enum.valueOf(
                            (Class<? extends Enum>)
                                    Class.forName("lol.pyr.znpcsplus.util.LookType"),
                            "FIXED");
            property(npc, "look", fixed);
            if (config.getString("pnj.type", "player").equals("player")) {
                Object skin =
                        call(
                                call(api, "getSkinDescriptorFactory"),
                                "createStaticDescriptor",
                                config.getString("pnj.skin", "Nati144378"));
                property(npc, "skin", skin);
            }
            Object hologram = call(npc, "getHologram");
            call(hologram, "clearLines");
            call(hologram, "addLine", config.getString("pnj.titre", "&aRécompenses journalières"));
            call(
                    hologram,
                    "addLine",
                    config.getString("pnj.sous-titre", "&7Clic droit pour ouvrir"));
            call(npc, "setEnabled", true);
            call(entry, "setProcessed", true);
        } catch (Exception error) {
            if (created) call(registry, "delete", id);
            throw error;
        }
    }

    public boolean spawn(Location at) throws Exception {
        if (!available()) throw new IllegalStateException("ZNPCsPlus indisponible");
        if (config.getBoolean("pnj.actif", false) || call(registry, "getById", id) != null)
            return false;
        create(at.clone());
        config.set("pnj.actif", true);
        fr.cliker.clikerv2.config.YamlFiles.location(config, "pnj.position", at);
        config.set("pnj.znpcs-id", id);
        try {
            save.run();
        } catch (RuntimeException error) {
            config.set("pnj.actif", false);
            delete();
            throw error;
        }
        return true;
    }

    private void delete() throws Exception {
        if (registry != null && call(registry, "getById", id) != null) call(registry, "delete", id);
    }

    public boolean remove() throws Exception {
        boolean present =
                config.getBoolean("pnj.actif", false)
                        || (registry != null && call(registry, "getById", id) != null);
        config.set("pnj.actif", false);
        config.set("pnj.position", null);
        config.set("pnj.znpcs-id", null);
        config.set("pnj.id", null);
        save.run();
        delete();
        return present;
    }

    @EventHandler
    public void world(org.bukkit.event.world.WorldLoadEvent event) {
        if (available() && config.getBoolean("pnj.actif", false))
            fr.cliker.clikerv2.runtime.ServerTasks.run(
                    plugin,
                    () -> {
                        try {
                            restore();
                        } catch (Exception error) {
                            plugin.getLogger()
                                    .log(Level.SEVERE, "Restauration du PNJ impossible", error);
                        }
                    });
    }

    public void shutdown() {
        stopped = true;
        HandlerList.unregisterAll(this);
        try {
            delete();
        } catch (Exception error) {
            plugin.getLogger().log(Level.SEVERE, "Retrait du PNJ temporaire impossible", error);
        }
    }

    public static Object call(Object target, String name, Object... args) throws Exception {
        outer:
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length)
                continue;
            Class<?>[] types = method.getParameterTypes();
            for (int i = 0; i < args.length; i++) {
                Class<?> t = types[i];
                if (t == boolean.class) t = Boolean.class;
                if (t == int.class) t = Integer.class;
                if (args[i] != null && !t.isInstance(args[i])) continue outer;
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException error) {
                if (error.getCause() instanceof Exception cause) throw cause;
                throw error;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }
}
