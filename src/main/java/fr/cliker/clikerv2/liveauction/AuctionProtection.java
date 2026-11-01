package fr.cliker.clikerv2.liveauction;

import fr.cliker.clikerv2.config.YamlFiles;
import fr.cliker.clikerv2.features.Settings;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Protection passive du carré autour de la salle, sur toute la hauteur du monde. */
final class AuctionProtection implements Listener {
    private final Settings settings;
    private Location center;
    private double radius;

    AuctionProtection(Settings settings) {
        this.settings = settings;
        refresh();
    }

    void refresh() {
        center = YamlFiles.location(settings.yaml, "salle");
        radius = Math.max(1, Math.min(1000, settings.yaml.getDouble("protection-rayon", 100)));
    }

    boolean inside(Location at) {
        return center != null
                && center.getWorld().equals(at.getWorld())
                && Math.abs(at.getX() - center.getX()) <= radius
                && Math.abs(at.getZ() - center.getZ()) <= radius;
    }

    @EventHandler
    public void world(org.bukkit.event.world.WorldLoadEvent e) {
        refresh();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        boolean cancel = inside(e.getEntity().getLocation());
        if (e instanceof EntityDamageByEntityEvent hit) {
            Entity from = hit.getDamager();
            if (from instanceof Projectile projectile
                    && projectile.getShooter() instanceof Entity shooter) from = shooter;
            cancel |= inside(from.getLocation());
        }
        if (cancel) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) {
        if (inside(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (inside(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hunger(FoodLevelChangeEvent e) {
        if (inside(e.getEntity().getLocation()) && e.getFoodLevel() < e.getEntity().getFoodLevel())
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mobs(CreatureSpawnEvent e) {
        if (inside(e.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent e) {
        if (inside(e.getPlayer().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void items(ItemSpawnEvent e) {
        if (inside(e.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> inside(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosion(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> inside(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void change(EntityChangeBlockEvent e) {
        if (inside(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fire(BlockBurnEvent e) {
        if (inside(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void ignite(BlockIgniteEvent e) {
        if (inside(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void empty(PlayerBucketEmptyEvent e) {
        if (inside(e.getBlock().getLocation()) || inside(e.getBlockClicked().getLocation()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fill(PlayerBucketFillEvent e) {
        if (inside(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) {
        if (inside(e.getBlock().getLocation())
                || e.getBlocks().stream()
                        .anyMatch(
                                b ->
                                        inside(b.getLocation())
                                                || inside(
                                                        b.getRelative(e.getDirection())
                                                                .getLocation())))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent e) {
        if (inside(e.getBlock().getLocation())
                || e.getBlocks().stream()
                        .anyMatch(
                                b ->
                                        inside(b.getLocation())
                                                || inside(
                                                        b.getRelative(e.getDirection())
                                                                .getLocation())))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fluid(BlockFromToEvent e) {
        if (inside(e.getToBlock().getLocation())) e.setCancelled(true);
    }

    void start(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void shutdown() {
        HandlerList.unregisterAll(this);
    }
}
