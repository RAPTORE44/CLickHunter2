package fr.cliker.clikerv2.station;

import fr.cliker.clikerv2.gear.EnchantMenu;
import fr.cliker.clikerv2.gear.ReforgeMenu;
import fr.cliker.clikerv2.gear.RuneMenu;
import fr.cliker.clikerv2.gear.TierGear;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Blocs posés dans le monde (table d'enchant, enclume de reforge, table de runes) : clic gauche ou
 * droit avec une épée/armure de tier en main ouvre directement le menu correspondant, l'item est
 * préchargé automatiquement.
 */
public final class StationManager implements Listener {
    private final JavaPlugin plugin;
    private final StationStore store;
    private final EnchantMenu enchantMenu;
    private final ReforgeMenu reforgeMenu;
    private final RuneMenu runeMenu;

    private final Map<String, StationType> stations = new HashMap<>();
    private final boolean hologramsAvailable;

    public StationManager(
            JavaPlugin plugin,
            StationStore store,
            EnchantMenu enchantMenu,
            ReforgeMenu reforgeMenu,
            RuneMenu runeMenu) {
        this.plugin = plugin;
        this.store = store;
        this.enchantMenu = enchantMenu;
        this.reforgeMenu = reforgeMenu;
        this.runeMenu = runeMenu;
        this.hologramsAvailable =
                fr.cliker.clikerv2.hologram.SharedHolograms.service() != null
                        && fr.cliker.clikerv2.hologram.SharedHolograms.service().available();
    }

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
                                                                "Impossible de charger les stations"
                                                                        + " : "
                                                                        + error.getMessage());
                                                return;
                                            }
                                            for (StationStore.StationPoint point : points) {
                                                point.location()
                                                        .getWorld()
                                                        .getChunkAt(point.location())
                                                        .load();
                                                point.location()
                                                        .getBlock()
                                                        .setType(point.type().material());
                                                stations.put(point.id(), point.type());
                                                spawnHologram(
                                                        point.id(), point.type(), point.location());
                                            }
                                        }));
    }

    public void addStation(StationType type, Location location) {
        Block block = location.getBlock();
        block.setType(type.material());
        String id = StationStore.idFor(block);
        stations.put(id, type);
        store.save(id, type, block);
        spawnHologram(id, type, block.getLocation());
    }

    public boolean removeNearest(Location near) {
        String nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (String id : stations.keySet()) {
            Location location = locationOf(id, near);
            if (location == null || !location.getWorld().equals(near.getWorld())) continue;
            double distance = location.distanceSquared(near);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = id;
            }
        }
        if (nearest == null) return false;
        stations.remove(nearest);
        String finalId = nearest;
        near.getWorld()
                .getBlockAt(parseX(finalId), parseY(finalId), parseZ(finalId))
                .setType(org.bukkit.Material.AIR);
        store.removeById(finalId);
        if (hologramsAvailable)
            fr.cliker.clikerv2.hologram.SharedHolograms.service().remove(hologramName(finalId));
        return true;
    }

    private void spawnHologram(String id, StationType type, Location blockLocation) {
        if (!hologramsAvailable) return;
        Location above = blockLocation.clone().add(0.5, 2.3, 0.5);
        String name = hologramName(id);
        var provider = fr.cliker.clikerv2.hologram.SharedHolograms.service();
        provider.removeLegacyLeaderboard(name);
        provider.create(name, above, hologramLines(type), null, false);
    }

    private List<String> hologramLines(StationType type) {
        return switch (type) {
            case ENCHANT ->
                    List.of("&d&l✦ Table d'Enchantement ✦", "&7Clique avec ton épée/armure");
            case REFORGE -> List.of("&6&l⚒ Enclume de Reforge ⚒", "&7Clique avec ton épée/armure");
            case RUNE -> List.of("&b&l◆ Table de Runes ◆", "&7Clique avec ton épée/armure");
        };
    }

    private String hologramName(String id) {
        return "cliker_station_" + id.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private Location locationOf(String id, Location near) {
        String[] parts = id.split(":");
        if (!parts[0].equals(near.getWorld().getName())) return null;
        return new Location(
                near.getWorld(),
                Integer.parseInt(parts[1]) + 0.5,
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]) + 0.5);
    }

    private int parseX(String id) {
        return Integer.parseInt(id.split(":")[1]);
    }

    private int parseY(String id) {
        return Integer.parseInt(id.split(":")[2]);
    }

    private int parseZ(String id) {
        return Integer.parseInt(id.split(":")[3]);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        StationType type = stations.get(StationStore.idFor(block));
        if (type == null) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("cliker.gear.use")) {
            player.sendMessage(
                    Component.text("Tu ne peux pas utiliser cette station.", NamedTextColor.RED));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!TierGear.isGear(hand)) {
            player.sendMessage(
                    Component.text(
                            "Tiens une épée ou une armure de tier en main pour utiliser la "
                                    + type.label()
                                    + ".",
                            NamedTextColor.RED));
            return;
        }

        ItemStack preload = hand.clone();
        player.getInventory().setItemInMainHand(null);
        switch (type) {
            case ENCHANT -> enchantMenu.open(player, preload);
            case REFORGE -> reforgeMenu.open(player, preload);
            case RUNE -> runeMenu.open(player, preload);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (stations.containsKey(StationStore.idFor(event.getBlock()))) event.setCancelled(true);
    }
}
