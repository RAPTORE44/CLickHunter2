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
    private static final java.util.Map<String, ZnpcsNpc> INSTANCES = new java.util.HashMap<>();
    private Object api, registry;
    private boolean ready, stopped;
    private org.bukkit.scheduler.BukkitTask scaleTask;

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

    public static java.util.List<String> diagnostics() {
        var lines = new java.util.ArrayList<String>();
        for (var npc : INSTANCES.values()) {
            String status;
            if (!npc.config.getBoolean("pnj.actif", false)) status = "non placé";
            else if (!npc.available()) status = "fournisseur indisponible";
            else
                try {
                    status =
                            call(npc.registry, "getById", npc.id) == null
                                    ? "ABSENT malgré sa configuration"
                                    : "présent";
                } catch (Exception error) {
                    status = "état impossible à vérifier";
                }
            lines.add("PNJ " + npc.id + " : " + status);
        }
        lines.sort(String.CASE_INSENSITIVE_ORDER);
        return lines;
    }

    public boolean available() {
        return ready && !stopped;
    }

    /** Actualise le skin et les textes sans retirer la position sauvegardée. */
    public void refresh() throws Exception {
        restore();
    }

    public void start() {
        INSTANCES.put(id, this);
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
                                    fr.cliker.clikerv2.ui.MenuOpenGuard.request(
                                            plugin,
                                            player,
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
            if (config.getDouble("pnj.echelle", 1) != 1) {
                scaleTask =
                        plugin.getServer()
                                .getScheduler()
                                .runTaskTimer(
                                        plugin,
                                        () -> {
                                            try {
                                                sendScale();
                                            } catch (Exception | LinkageError error) {
                                                plugin.getLogger()
                                                        .log(
                                                                Level.SEVERE,
                                                                "Échelle ZNPCsPlus indisponible"
                                                                        + " pour "
                                                                        + id,
                                                                error);
                                                if (scaleTask != null) scaleTask.cancel();
                                            }
                                        },
                                        10,
                                        20);
            }
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

    static Object publicApiCall(Class<?> apiType, Object target, String method) throws Exception {
        // L'implémentation PacketEvents peut être anonyme : invoquer la méthode de son API
        // publique.
        return apiType.getMethod(method).invoke(target);
    }

    static Object skinDescriptor(Object factory, String name) throws Exception {
        // Le descripteur statique de ZNPCsPlus 2.0.0 fait un join() réseau : ne jamais le créer sur
        // le thread serveur.
        return call(factory, "createRefreshingDescriptor", name);
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
                            "PER_PLAYER");
            property(npc, "look", fixed);
            property(
                    npc,
                    "look_distance",
                    Math.max(8.0, Math.min(128.0, config.getDouble("pnj.distance-regard", 48))));
            property(
                    npc,
                    "view_distance",
                    Math.max(16, Math.min(128, config.getInt("pnj.distance-affichage", 96))));
            if (config.getString("pnj.type", "player").equals("player")) {
                Object skin =
                        skinDescriptor(
                                call(api, "getSkinDescriptorFactory"),
                                config.getString("pnj.skin", "Nati144378"));
                property(npc, "skin", skin);
            }
            Object hologram = call(npc, "getHologram");
            call(hologram, "clearLines");
            var provider = fr.cliker.clikerv2.hologram.SharedHolograms.service();
            if (provider != null)
                provider.create(
                        "npc:" + id,
                        at.clone().add(0, 1.8 * config.getDouble("pnj.echelle", 1) + .7, 0),
                        java.util.List.of(
                                config.getString("pnj.titre", "&aRécompenses journalières"),
                                config.getString("pnj.sous-titre", "&7Clic droit pour ouvrir")),
                        null,
                        false);
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
        var provider = fr.cliker.clikerv2.hologram.SharedHolograms.service();
        if (provider != null) provider.remove("npc:" + id);
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

    /**
     * Attribut scale envoyé uniquement aux spectateurs du PNJ par PacketEvents de ZNPCsPlus 2.0.0.
     */
    private void sendScale() throws Exception {
        if (!available() || !config.getBoolean("pnj.actif", false)) return;
        Object entry = call(registry, "getById", id);
        if (entry == null) return;
        Object npc = call(entry, "getNpc");
        Object viewers = call(npc, "getViewers");
        if (!(viewers instanceof java.util.Set<?> players) || players.isEmpty()) return;
        String base = "lol.pyr.znpcsplus.libraries.packetevents.api.";
        Object api = Class.forName(base + "PacketEvents").getMethod("getAPI").invoke(null);
        Object manager =
                publicApiCall(Class.forName(base + "PacketEventsAPI"), api, "getPlayerManager");
        Class<?> attributeClass = Class.forName(base + "protocol.attribute.Attribute");
        Object attribute =
                Class.forName(base + "protocol.attribute.Attributes").getField("SCALE").get(null);
        Class<?> packetClass =
                Class.forName(base + "wrapper.play.server.WrapperPlayServerUpdateAttributes");
        var propertyConstructor =
                Class.forName(packetClass.getName() + "$Property")
                        .getConstructor(attributeClass, double.class, java.util.List.class);
        var send =
                Class.forName(base + "manager.player.PlayerManager")
                        .getMethod(
                                "sendPacket",
                                Object.class,
                                Class.forName(base + "wrapper.PacketWrapper"));
        double scale = config.getDouble("pnj.echelle", 1);
        if (!Double.isFinite(scale) || scale < .1 || scale > 4)
            throw new IllegalArgumentException("Échelle PNJ invalide");
        for (Object viewer : players) {
            Object property =
                    propertyConstructor.newInstance(attribute, scale, java.util.List.of());
            Object packet =
                    packetClass
                            .getConstructor(int.class, java.util.List.class)
                            .newInstance(
                                    call(npc, "getPacketEntityId"), java.util.List.of(property));
            send.invoke(manager, viewer, packet);
        }
    }

    public void shutdown() {
        INSTANCES.remove(id, this);
        stopped = true;
        if (scaleTask != null) scaleTask.cancel();
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
