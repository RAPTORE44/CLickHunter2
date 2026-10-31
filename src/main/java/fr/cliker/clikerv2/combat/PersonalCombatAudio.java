package fr.cliker.clikerv2.combat;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Supprime les bruits d’attaque vanilla diffusés aux voisins ; le noteblock personnel reste actif.
 */
public final class PersonalCombatAudio implements Listener {
    private static final String BASE = "lol.pyr.znpcsplus.libraries.packetevents.api.";
    private static PersonalCombatAudio current;
    private final JavaPlugin plugin;
    private final Set<UUID> combatWorlds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> worlds = new ConcurrentHashMap<>();
    private final Set<Integer> attacks = new HashSet<>();

    private record Hit(UUID owner, UUID world, long until) {}

    private final Map<Integer, Hit> hits = new ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask hitCleanup;

    private void scheduleHitCleanup() {
        if (hitCleanup != null) return;
        hitCleanup =
                Bukkit.getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    hitCleanup = null;
                                    long now = System.nanoTime();
                                    hits.entrySet().removeIf(e -> e.getValue().until < now);
                                    if (!hits.isEmpty() && enabled) scheduleHitCleanup();
                                },
                                40);
    }

    private Constructor<?> hurtPacket;
    private Method playerHandle, sendPacket;
    private Field connection;

    public static void hit(Player player, org.bukkit.entity.LivingEntity entity) {
        mark(player);
        var audio = current;
        if (audio != null)
            audio.hits.put(
                    entity.getEntityId(),
                    new Hit(
                            player.getUniqueId(),
                            entity.getWorld().getUID(),
                            System.nanoTime() + 2_000_000_000L));
        if (audio != null) audio.scheduleHitCleanup();
    }

    public static void hurt(Player player, org.bukkit.entity.LivingEntity entity) {
        hit(player, entity);
        var audio = current;
        if (audio == null) return;
        try {
            Object handle = audio.playerHandle.invoke(player);
            audio.sendPacket.invoke(
                    audio.connection.get(handle),
                    audio.hurtPacket.newInstance(entity.getEntityId(), player.getYaw()));
        } catch (ReflectiveOperationException e) {
            audio.plugin
                    .getLogger()
                    .fine("Animation personnelle non envoyée : " + e.getClass().getSimpleName());
        }
    }

    private Method packetName,
            userMethod,
            bufferMethod,
            cancelMethod,
            uuidMethod,
            readerMethod,
            byteMethod;
    private Object manager, handle;
    private Class<?> common;
    private volatile boolean enabled;

    public PersonalCombatAudio(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static void mark(Player player) {
        var audio = current;
        if (audio != null) {
            audio.combatWorlds.add(player.getWorld().getUID());
            audio.worlds.put(player.getUniqueId(), player.getWorld().getUID());
        }
    }

    public void start() {
        if (!Bukkit.getPluginManager().isPluginEnabled("ZNPCsPlus")) return;
        try {
            var craft = Class.forName("org.bukkit.craftbukkit.CraftSound");
            var registry =
                    Class.forName("net.minecraft.core.registries.BuiltInRegistries")
                            .getField("SOUND_EVENT")
                            .get(null);
            var getId =
                    Class.forName("net.minecraft.core.Registry").getMethod("getId", Object.class);
            for (Sound sound :
                    List.of(
                            Sound.ENTITY_PLAYER_ATTACK_STRONG,
                            Sound.ENTITY_PLAYER_ATTACK_WEAK,
                            Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                            Sound.ENTITY_PLAYER_ATTACK_CRIT,
                            Sound.ENTITY_PLAYER_ATTACK_NODAMAGE,
                            Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK))
                attacks.add(
                        (Integer)
                                        getId.invoke(
                                                registry,
                                                craft.getMethod("bukkitToMinecraft", Sound.class)
                                                        .invoke(null, sound))
                                + 1);
            hurtPacket =
                    Class.forName(
                                    "net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket")
                            .getConstructor(int.class, float.class);
            playerHandle =
                    Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer")
                            .getMethod("getHandle");
            connection =
                    Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection");
            sendPacket =
                    Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl")
                            .getMethod(
                                    "send", Class.forName("net.minecraft.network.protocol.Packet"));
            Class<?> protocol = Class.forName(BASE + "event.ProtocolPacketEvent");
            packetName = protocol.getMethod("getPacketName");
            userMethod = protocol.getMethod("getUser");
            bufferMethod = protocol.getMethod("getByteBuf");
            cancelMethod = protocol.getMethod("setCancelled", boolean.class);
            uuidMethod = Class.forName(BASE + "protocol.player.User").getMethod("getUUID");
            var byteBuf = Class.forName("io.netty.buffer.ByteBuf");
            readerMethod = byteBuf.getMethod("readerIndex");
            byteMethod = byteBuf.getMethod("getUnsignedByte", int.class);
            Object api = Class.forName(BASE + "PacketEvents").getMethod("getAPI").invoke(null);
            manager =
                    Class.forName(BASE + "PacketEventsAPI")
                            .getMethod("getEventManager")
                            .invoke(api);
            Class<?> listener = Class.forName(BASE + "event.PacketListener"),
                    priority = Class.forName(BASE + "event.PacketListenerPriority");
            Object proxy =
                    Proxy.newProxyInstance(
                            listener.getClassLoader(),
                            new Class<?>[] {listener},
                            (self, method, args) -> {
                                if (method.getName().equals("onPacketSend")) {
                                    filter(args[0]);
                                    return null;
                                }
                                if (method.isDefault())
                                    return InvocationHandler.invokeDefault(
                                            self, method, args == null ? new Object[0] : args);
                                return switch (method.getName()) {
                                    case "hashCode" -> System.identityHashCode(self);
                                    case "equals" -> self == args[0];
                                    case "toString" -> "ClickHunterPersonalCombatAudio";
                                    default -> null;
                                };
                            });
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object normal = Enum.valueOf((Class) priority, "NORMAL");
            handle =
                    manager.getClass()
                            .getMethod("registerListener", listener, priority)
                            .invoke(manager, proxy, normal);
            common = Class.forName(BASE + "event.PacketListenerCommon");
            enabled = true;
            current = this;
            Bukkit.getPluginManager().registerEvents(this, plugin);
            for (Player player : Bukkit.getOnlinePlayers()) track(player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Filtre des sons de coups indisponible ; sons de mobs silencieux et"
                                    + " noteblock personnel conservés",
                            e);
        }
    }

    private void filter(Object event) {
        if (!enabled) return;
        try {
            String name = (String) packetName.invoke(event);
            boolean damage = name.equals("HURT_ANIMATION") || name.equals("DAMAGE_EVENT");
            if (!damage && !name.equals("SOUND_EFFECT")) return;
            Object user = userMethod.invoke(event);
            UUID id = (UUID) uuidMethod.invoke(user);
            UUID world = worlds.get(id);
            if (world == null || !combatWorlds.contains(world)) return;
            Object buffer = bufferMethod.invoke(event);
            int at = (Integer) readerMethod.invoke(buffer);
            int value = 0;
            for (int i = 0; i < 5; i++) {
                int part = ((Number) byteMethod.invoke(buffer, at + i)).intValue();
                value |= (part & 127) << (7 * i);
                if ((part & 128) == 0) {
                    if (damage) {
                        var hit = hits.get(value);
                        if (hit != null) {
                            if (hit.until < System.nanoTime()) hits.remove(value, hit);
                            else if (hit.world.equals(world) && !hit.owner.equals(id))
                                cancelMethod.invoke(event, true);
                        }
                    } else if (attacks.contains(value)) cancelMethod.invoke(event, true);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            enabled = false;
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Filtre audio arrêté pour préserver les paquets du serveur",
                            e);
        }
    }

    private void track(Player p) {
        worlds.put(p.getUniqueId(), p.getWorld().getUID());
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        track(e.getPlayer());
    }

    @EventHandler
    public void world(PlayerChangedWorldEvent e) {
        track(e.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        worlds.remove(e.getPlayer().getUniqueId());
    }

    public void shutdown() {
        enabled = false;
        if (hitCleanup != null) {
            hitCleanup.cancel();
            hitCleanup = null;
        }
        if (current == this) current = null;
        if (manager != null && handle != null)
            try {
                manager.getClass().getMethod("unregisterListener", common).invoke(manager, handle);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger()
                        .warning(
                                "Nettoyage du filtre audio impossible : "
                                        + e.getClass().getSimpleName());
            }
        worlds.clear();
        hits.clear();
        combatWorlds.clear();
        HandlerList.unregisterAll(this);
    }
}
