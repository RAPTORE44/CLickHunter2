package fr.cliker.clikerv2.merchant;

import fr.cliker.clikerv2.MobTier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
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
    private final JavaPlugin plugin;
    private final MerchantStore store;
    private final MerchantMenu menu;
    private final NamespacedKey mobIdKey;

    private final Map<String, String> npcMobId = new HashMap<>();
    private final Map<String, Location> npcLocation = new HashMap<>();
    private final Map<String, UUID> aliveByNpcId = new HashMap<>();

    public MerchantManager(JavaPlugin plugin, MerchantStore store, MerchantMenu menu) {
        this.plugin = plugin;
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
                                                npcLocation.put(point.id(), point.location());
                                                spawn(point.id());
                                            }
                                        }));
    }

    public void addNpc(String mobId, Location location) {
        String id = UUID.randomUUID().toString();
        npcMobId.put(id, mobId);
        npcLocation.put(id, location.clone());
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
        UUID uuid = aliveByNpcId.remove(npcId);
        if (uuid == null) return;
        Entity entity = plugin.getServer().getEntity(uuid);
        if (entity != null) entity.remove();
    }

    private void spawn(String npcId) {
        Location location = npcLocation.get(npcId);
        String mobId = npcMobId.get(npcId);
        MobTier tier = mobId == null ? null : MobTier.parse(mobId);
        if (location == null || tier == null) return;

        Villager villager =
                (Villager)
                        location.getWorld()
                                .spawnEntity(location, org.bukkit.entity.EntityType.VILLAGER);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCollidable(false);
        villager.setPersistent(false);
        villager.setRemoveWhenFarAway(false);
        villager.setVillagerType(Villager.Type.SWAMP);
        villager.setProfession(Villager.Profession.NONE);
        villager.customName(
                Component.text(
                        "Marchand " + tier.label(),
                        NamedTextColor.LIGHT_PURPLE,
                        TextDecoration.BOLD));
        villager.setCustomNameVisible(true);
        villager.getPersistentDataContainer().set(mobIdKey, PersistentDataType.STRING, mobId);
        aliveByNpcId.put(npcId, villager.getUniqueId());
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
