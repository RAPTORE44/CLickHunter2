package fr.cliker.clikerv2.merchant;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PNJ marchand par mob : villageois figé, ouvre le GUI d'achat de l'équipement du mob
 * correspondant.
 */
public final class MerchantManager implements Listener {
    private final fr.cliker.clikerv2.features.Settings appearance;
    private final JavaPlugin plugin;
    private final MerchantStore store;
    private final MerchantMenu menu;
    private final NamespacedKey mobIdKey;

    private final Map<String, String> npcMobId = new HashMap<>();
    private final Map<String, Location> npcLocation = new HashMap<>();
    private final Map<String, UUID> aliveByNpcId = new HashMap<>();

    public MerchantManager(JavaPlugin plugin, MerchantStore store, MerchantMenu menu) {
        this.plugin = plugin;
        appearance = new fr.cliker.clikerv2.features.Settings(plugin, "pnj/marchands.yml");
        this.store = store;
        this.menu = menu;
        this.mobIdKey = new NamespacedKey(plugin, "cliker_merchant_mob");
    }

    /**
     * Force le chargement des chunks avant de scanner/supprimer les anciens PNJ, sinon reload à
     * chaud = doublons.
     */
    public void load() {
        store.loadAll()
                .whenComplete(
                        (points, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .severe(
                                                                "Impossible de charger les"
                                                                        + " marchands : "
                                                                        + error.getMessage());
                                                return;
                                            }
                                            for (MerchantStore.MerchantPoint point : points)
                                                point.location()
                                                        .getWorld()
                                                        .getChunkAt(point.location())
                                                        .load();
                                            for (var world : plugin.getServer().getWorlds()) {
                                                for (Entity entity : world.getEntities()) {
                                                    if (entity.getPersistentDataContainer()
                                                            .has(
                                                                    mobIdKey,
                                                                    PersistentDataType.STRING))
                                                        entity.remove();
                                                }
                                            }
                                            for (MerchantStore.MerchantPoint point : points) {
                                                npcMobId.put(point.id(), point.mobId());
                                                var override =
                                                        fr.cliker.clikerv2.config.YamlFiles
                                                                .location(
                                                                        appearance.yaml,
                                                                        "positions." + point.id());
                                                npcLocation.put(
                                                        point.id(),
                                                        override == null
                                                                ? point.location()
                                                                : override);
                                                if (!appearance.yaml.contains(
                                                        "positions." + point.id()))
                                                    fr.cliker.clikerv2.config.YamlFiles.location(
                                                            appearance.yaml,
                                                            "positions." + point.id(),
                                                            point.location());
                                                appearance.yaml.set(
                                                        "positions." + point.id() + ".creature",
                                                        point.mobId());
                                                spawn(point.id());
                                            }
                                            appearance.save();
                                        }));
    }

    public void addNpc(String mobId, Location location) {
        String id = UUID.randomUUID().toString();
        npcMobId.put(id, mobId);
        npcLocation.put(id, location.clone());
        fr.cliker.clikerv2.config.YamlFiles.location(appearance.yaml, "positions." + id, location);
        appearance.yaml.set("positions." + id + ".creature", mobId);
        appearance.save();
        spawn(id);
        store.save(id, mobId, location);
    }

    public boolean removeNearest(Location near) {
        String nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, Location> entry : npcLocation.entrySet()) {
            Location location = entry.getValue();
            if (!location.getWorld().equals(near.getWorld())) continue;
            double distance = location.distanceSquared(near);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = entry.getKey();
            }
        }
        if (nearest == null) return false;
        despawn(nearest);
        npcMobId.remove(nearest);
        npcLocation.remove(nearest);
        store.removeById(nearest);
        return true;
    }

    public List<String> listMobIds() {
        return new ArrayList<>(npcMobId.values());
    }

    private void despawn(String npcId) {
        var packet = packetNpcs.remove(npcId);
        if (packet != null) packet.shutdown();
        UUID uuid = aliveByNpcId.remove(npcId);
        if (uuid == null) return;
        Entity entity = plugin.getServer().getEntity(uuid);
        if (entity != null) entity.remove();
    }

    private final Map<String, fr.cliker.clikerv2.npc.ZnpcsNpc> packetNpcs = new HashMap<>();

    public void skin(String mobId, String skin) {
        if (!skin.equalsIgnoreCase("villager") && !skin.matches("[A-Za-z0-9_]{1,16}"))
            throw new IllegalArgumentException("Pseudo invalide");
        appearance.yaml.set(
                "marchands." + mobId + ".skin", skin.equalsIgnoreCase("villager") ? "" : skin);
        appearance.yaml.set(
                "marchands." + mobId + ".type",
                skin.equalsIgnoreCase("villager") ? "villager" : "player");
        appearance.save();
        for (var id : new java.util.ArrayList<>(npcMobId.keySet()))
            if (mobId.equals(npcMobId.get(id))) {
                despawn(id);
                spawn(id);
            }
    }

    public boolean available() {
        return plugin.getServer().getPluginManager().isPluginEnabled("ZNPCsPlus");
    }

    private void spawn(String npcId) {
        Location location = npcLocation.get(npcId);
        String mobId = npcMobId.get(npcId);
        MobTier tier = mobId == null ? null : MobTier.parse(mobId);
        if (location == null || tier == null || !available()) return;
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("pnj.actif", true);
        fr.cliker.clikerv2.config.YamlFiles.location(config, "pnj.position", location);
        config.set(
                "pnj.type", appearance.yaml.getString("marchands." + mobId + ".type", "villager"));
        config.set("pnj.skin", appearance.yaml.getString("marchands." + mobId + ".skin", ""));
        config.set(
                "pnj.titre",
                appearance.yaml.getString(
                        "marchands." + mobId + ".titre", "&aMarchand " + tier.label()));
        config.set(
                "pnj.sous-titre",
                appearance.yaml.getString(
                        "marchands." + mobId + ".sous-titre", "&7Clic droit pour ouvrir"));
        var npc =
                new fr.cliker.clikerv2.npc.ZnpcsNpc(
                        plugin,
                        config,
                        () -> {},
                        player -> menu.open(player, tier),
                        "merchant-" + npcId,
                        "Marchand");
        packetNpcs.put(npcId, npc);
        npc.start();
    }

    public void shutdown() {
        packetNpcs.values().forEach(fr.cliker.clikerv2.npc.ZnpcsNpc::shutdown);
        packetNpcs.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof LivingEntity entity)) return;
        String mobId = entity.getPersistentDataContainer().get(mobIdKey, PersistentDataType.STRING);
        if (mobId == null) return;
        MobTier tier = MobTier.parse(mobId);
        if (tier == null) return;
        event.setCancelled(true);
        menu.open(event.getPlayer(), tier);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.getPersistentDataContainer().has(mobIdKey, PersistentDataType.STRING))
            event.setCancelled(true);
    }
}
