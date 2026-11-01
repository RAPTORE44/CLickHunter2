package fr.cliker.clikerv2.combat;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.mobs.CustomMobManager;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class TrainingDummy implements Listener, CommandExecutor {
    private final JavaPlugin plugin;
    private final CustomMobManager combat;
    private final Settings settings;
    private ArmorStand dummy;

    public TrainingDummy(JavaPlugin p, CustomMobManager combat) {
        plugin = p;
        this.combat = combat;
        settings = new Settings(p, "combat/mannequin.yml");
        p.getCommand("mannequin").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, p);
        restore();
    }

    private void restore() {
        var at = fr.cliker.clikerv2.config.YamlFiles.location(settings.yaml, "position");
        if (at != null && at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4))
            spawn(at);
    }

    private void spawn(Location at) {
        if (dummy != null) dummy.remove();
        dummy =
                at.getWorld()
                        .spawn(
                                at,
                                ArmorStand.class,
                                e -> {
                                    e.setGravity(false);
                                    e.setArms(true);
                                    e.setBasePlate(false);
                                    e.setPersistent(false);
                                    e.setInvulnerable(true);
                                    e.customName(
                                            net.kyori.adventure.text.Component.text(
                                                    "⚔ Mannequin d’entraînement"));
                                    e.setCustomNameVisible(true);
                                });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void hit(EntityDamageByEntityEvent e) {
        if (dummy == null || !dummy.equals(e.getEntity())) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p && CombatReach.canHit(p, dummy)) {
            Long amount = combat.calculateDamage(p, MobTier.values()[0]);
            if (amount == null) {
                p.sendActionBar(net.kyori.adventure.text.Component.text("Tiens une épée en main."));
                return;
            }
            dummy.customName(
                    net.kyori.adventure.text.Component.text(
                            "⚔ " + fr.cliker.clikerv2.Numbers.compact(amount) + " dégâts"));
            combat.showDamage(p, dummy, amount);
        }
    }

    @EventHandler
    public void chunk(org.bukkit.event.world.ChunkLoadEvent e) {
        if (dummy == null || !dummy.isValid()) restore();
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;
        if (a.length > 0 && a[0].equalsIgnoreCase("remove")) {
            if (dummy != null) dummy.remove();
            dummy = null;
            settings.yaml.set("position", null);
            settings.save();
        } else {
            fr.cliker.clikerv2.config.YamlFiles.location(
                    settings.yaml, "position", p.getLocation());
            settings.save();
            spawn(p.getLocation());
        }
        return true;
    }

    public void shutdown() {
        if (dummy != null) dummy.remove();
        HandlerList.unregisterAll(this);
    }
}
