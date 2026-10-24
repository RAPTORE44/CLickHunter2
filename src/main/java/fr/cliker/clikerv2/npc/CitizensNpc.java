package fr.cliker.clikerv2.npc;

import fr.cliker.clikerv2.daily.DailySettings;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** Adaptateur Citizens optionnel : aucune classe Citizens n'est chargée s'il est absent. */
public class CitizensNpc implements Listener {
    private final JavaPlugin plugin;
    private final YamlConfiguration config;
    private final Runnable save;
    private final Consumer<Player> action;
    private final String ownerKey;
    private final String internalName;
    private Object registry;
    private TextDisplay label;
    private BukkitTask ticker;
    private BukkitTask restoreTask;
    private UUID entityId;
    private boolean ready;

    public CitizensNpc(JavaPlugin plugin, YamlConfiguration config, Runnable save,
                       Consumer<Player> action, String ownerKey, String internalName) {
        this.plugin = plugin; this.config = config; this.save = save;
        this.action = action; this.ownerKey = ownerKey; this.internalName = internalName;
    }
    public boolean available() { return ready; }

    @SuppressWarnings("unchecked")
    public void start() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Citizens")) {
            plugin.getLogger().info("Citizens absent : PNJ indisponible, commandes du module accessibles.");
            return;
        }
        try {
            registry = Class.forName("net.citizensnpcs.api.CitizensAPI").getMethod("getNPCRegistry").invoke(null);
            Class<? extends Event> clickClass = (Class<? extends Event>) Class.forName("net.citizensnpcs.api.event.NPCRightClickEvent");
            EventExecutor clicks = (listener, event) -> {
                try {
                    Object npc = call(event, "getNPC");
                    if (!owned(npc)) return;
                    if (event instanceof Cancellable cancellable) cancellable.setCancelled(true);
                    action.accept((Player) call(event, "getClicker"));
                } catch (Exception e) { plugin.getLogger().log(Level.SEVERE, "Clic PNJ impossible", e); }
            };
            var manager = plugin.getServer().getPluginManager();
            manager.registerEvent(clickClass, this, EventPriority.NORMAL, clicks, plugin, false);
            manager.registerEvents(this, plugin);
            ready = true;
            restoreTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try { restore(); } catch (Exception e) { plugin.getLogger().log(Level.SEVERE, "Restauration du PNJ impossible", e); }
            }, 40L);
            ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::maintain, 50L, 10L);
        } catch (Exception | LinkageError e) {
            ready = false;
            plugin.getLogger().log(Level.SEVERE, "Citizens incompatible : création de PNJ indisponible", e);
        }
    }
    private boolean owned(Object npc) throws Exception { return Boolean.TRUE.equals(call(call(npc, "data"), "get", ownerKey, false)); }
    private List<Object> allOwned() throws Exception {
        List<Object> result = new ArrayList<>();
        for (Object npc : (Iterable<?>) registry) if (owned(npc)) result.add(npc);
        return result;
    }
    private Location anchor() { return config.getLocation("pnj.position"); }
    private Object create(Location location) throws Exception {
        Object npc = call(registry, "createNPC", EntityType.PLAYER, internalName);
        try {
            call(call(npc, "data"), "setPersistent", ownerKey, true);
            configure(npc);
            if (!Boolean.TRUE.equals(call(npc, "spawn", location))) throw new IllegalStateException("Citizens n'a pas pu faire apparaître le PNJ");
            return npc;
        } catch (Exception e) { call(npc, "destroy"); throw e; }
    }
    private void configure(Object npc) throws Exception {
        call(npc, "setProtected", true);
        call(npc, "setFlyable", true);
        Object data = call(npc, "data");
        call(data, "setPersistent", "nameplate-visible", false);
        call(data, "setPersistent", "collidable", false);
        call(data, "setPersistent", "fluid-pushable", false);
        call(data, "setPersistent", "silent-sounds", true);
        Object skin = call(npc, "getOrAddTrait", Class.forName("net.citizensnpcs.trait.SkinTrait"));
        call(skin, "setSkinName", config.getString("pnj.skin", "Nati144378"), false);
        // Ne jamais activer LookClose : l'orientation initiale doit être conservée.
        Class<?> lookClose = Class.forName("net.citizensnpcs.trait.LookClose");
        call(npc, "removeTrait", lookClose);
        call(call(npc, "getNavigator"), "cancelNavigation");
    }
    private void restore() throws Exception {
        List<Object> owned = allOwned();
        if (!config.getBoolean("pnj.actif", false)) {
            for (Object npc : owned) call(npc, "destroy");
            call(registry, "saveToStore");
            return;
        }
        Location location = anchor();
        if (location == null || location.getWorld() == null) throw new IllegalStateException("Monde ou position du PNJ indisponible dans la configuration du PNJ");
        // La balise persistante permet de retrouver le PNJ même si son identifiant a changé.
        Object npc = owned.isEmpty() ? create(location) : owned.getFirst();
        for (int i = 1; i < owned.size(); i++) call(owned.get(i), "destroy");
        configure(npc);
        if (!Boolean.TRUE.equals(call(npc, "isSpawned"))) call(npc, "spawn", location);
        config.set("pnj.id", call(npc, "getId"));
        save.run();
        call(registry, "saveToStore");
        maintain();
    }
    public boolean spawn(Location location) throws Exception {
        if (config.getBoolean("pnj.actif", false) || !allOwned().isEmpty()) return false;
        Object npc = create(location.clone());
        config.set("pnj.actif", true);
        config.set("pnj.position", location.clone());
        config.set("pnj.id", call(npc, "getId"));
        save.run();
        call(registry, "saveToStore");
        maintain();
        return true;
    }
    public boolean remove() throws Exception {
        List<Object> owned = allOwned();
        boolean present = !owned.isEmpty() || config.getBoolean("pnj.actif", false);
        // Sauvegarder l'intention avant destruction pour ne pas ressusciter le PNJ au redémarrage.
        config.set("pnj.actif", false);
        config.set("pnj.position", null);
        config.set("pnj.id", null);
        save.run();
        for (Object npc : owned) call(npc, "destroy");
        call(registry, "saveToStore");
        if (label != null) label.remove();
        label = null;
        entityId = null;
        return present;
    }
    private void maintain() {
        if (!ready || !config.getBoolean("pnj.actif", false)) return;
        try {
            Location location = anchor();
            if (location == null || location.getWorld() == null) return;
            List<Object> owned = allOwned();
            if (owned.isEmpty()) return;
            Object npc = owned.getFirst();
            if (!Boolean.TRUE.equals(call(npc, "isSpawned"))) return;
            Entity entity = (Entity) call(npc, "getEntity");
            entityId = entity.getUniqueId();
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setVelocity(new Vector());
            call(call(npc, "getNavigator"), "cancelNavigation");
            Location current = entity.getLocation();
            if (!current.getWorld().equals(location.getWorld()) || current.distanceSquared(location) > 0.0001
                    || Math.abs(current.getYaw() - location.getYaw()) > 0.1 || Math.abs(current.getPitch() - location.getPitch()) > 0.1) entity.teleport(location);
            Location above = location.clone().add(0, 2.35, 0);
            if (label == null || !label.isValid()) {
                label = location.getWorld().spawn(above, TextDisplay.class, display -> {
                    display.setPersistent(false);
                    display.setBillboard(Display.Billboard.CENTER);
                    display.setShadowed(true);
                    display.setLineWidth(500);
                    display.text(DailySettings.color(config.getString("pnj.titre", "&aRécompenses journalières") + "\n"
                            + config.getString("pnj.sous-titre", "&7Clic droit pour ouvrir")));
                });
            }
        } catch (Exception e) {
            ready = false; // Éviter une erreur répétée toutes les dix ticks.
            plugin.getLogger().log(Level.SEVERE, "PNJ indisponible : commandes du module accessibles", e);
        }
    }
    @EventHandler public void protect(EntityDamageEvent event) {
        if (entityId != null && entityId.equals(event.getEntity().getUniqueId())) event.setCancelled(true);
    }
    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (ticker != null) ticker.cancel();
        if (restoreTask != null) restoreTask.cancel();
        if (label != null) label.remove();
        if (registry != null) {
            try { call(registry, "saveToStore"); }
            catch (Exception e) { plugin.getLogger().log(Level.SEVERE, "Sauvegarde Citizens impossible", e); }
        }
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        outer: for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] types = method.getParameterTypes();
            for (int i = 0; i < args.length; i++) {
                Class<?> type = types[i];
                if (type == boolean.class) type = Boolean.class;
                if (type == int.class) type = Integer.class;
                if (args[i] != null && !type.isInstance(args[i])) continue outer;
            }
            try { return method.invoke(target, args); }
            catch (InvocationTargetException e) {
                if (e.getCause() instanceof Exception cause) throw cause;
                throw e;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }
}
