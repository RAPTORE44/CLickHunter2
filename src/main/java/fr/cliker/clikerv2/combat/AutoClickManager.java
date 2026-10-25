package fr.cliker.clikerv2.combat;

import fr.cliker.clikerv2.gear.ItemMods;
import fr.cliker.clikerv2.gear.ReforgeType;
import fr.cliker.clikerv2.gear.TierGear;
import fr.cliker.clikerv2.mobs.CustomMobManager;
import fr.cliker.clikerv2.upgrade.UpgradeManager;
import fr.cliker.clikerv2.upgrade.UpgradeType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class AutoClickManager implements CommandExecutor, Listener {
    private static final int BASE_INTERVAL_TICKS = 30;
    private static final int MIN_INTERVAL_TICKS = 10;

    private final JavaPlugin plugin;
    private final UpgradeManager upgrades;
    private final Set<UUID> active = new HashSet<>();
    private final java.util.Map<UUID, Integer> lastAttackTick = new java.util.HashMap<>();
    private int elapsedTicks = 0;
    private org.bukkit.scheduler.BukkitTask task;

    public AutoClickManager(JavaPlugin plugin, UpgradeManager upgrades) {
        this.plugin = plugin;
        this.upgrades = upgrades;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private void tick() {
        elapsedTicks += 10;
        for (UUID uuid : new HashSet<>(active)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                active.remove(uuid);
                lastAttackTick.remove(uuid);
                continue;
            }

            if (!player.hasPermission("cliker.autoclick.use")) {
                active.remove(uuid);
                lastAttackTick.remove(uuid);
                continue;
            }
            int interval = intervalFor(player);
            int last = lastAttackTick.getOrDefault(uuid, -interval);
            if (elapsedTicks - last < interval) continue;

            Location eye = player.getEyeLocation();
            RayTraceResult result =
                    player.getWorld()
                            .rayTraceEntities(
                                    eye,
                                    eye.getDirection(),
                                    5.0,
                                    0.4,
                                    entity ->
                                            entity instanceof LivingEntity
                                                    && entity.getPersistentDataContainer()
                                                            .has(
                                                                    CustomMobManager.MOB_ID_KEY,
                                                                    PersistentDataType.STRING));
            if (result != null
                    && result.getHitEntity() instanceof LivingEntity living
                    && living.isValid()) {
                if (!player.hasLineOfSight(living)) continue;
                player.swingMainHand();
                living.damage(1.0, player);
                lastAttackTick.put(uuid, elapsedTicks);
            }
        }
    }

    /**
     * Délai entre deux attaques auto : réduit par la reforge Vif de l'épée équipée + l'upgrade
     * Cadence.
     */
    private int intervalFor(Player player) {
        double ticks = BASE_INTERVAL_TICKS;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        String tierId =
                weapon.hasItemMeta()
                        ? weapon.getItemMeta()
                                .getPersistentDataContainer()
                                .get(TierGear.SWORD_KEY, PersistentDataType.STRING)
                        : null;
        if (tierId != null) {
            ReforgeType reforge = ItemMods.getReforge(weapon);
            if (reforge != null) ticks *= (1 - reforge.swordAutoclickSpeedPercent() / 100.0);
        }

        double cadenceSeconds = upgrades.getBonus(player.getUniqueId(), UpgradeType.CADENCE);
        ticks -= cadenceSeconds * 20.0;

        return Math.max(MIN_INTERVAL_TICKS, (int) Math.round(ticks));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    Component.text("Commande réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("cliker.autoclick.use")) {
            sender.sendMessage(
                    Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (active.remove(uuid)) {
            lastAttackTick.remove(uuid);
            player.sendMessage(Component.text("Autoclick désactivé.", NamedTextColor.RED));
        } else {
            active.add(uuid);
            player.sendMessage(
                    Component.text(
                            "Autoclick activé : vise une créature. La cadence dépend de tes"
                                    + " améliorations et de ton équipement.",
                            NamedTextColor.GREEN));
        }
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
        lastAttackTick.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        if (task != null) task.cancel();
        active.clear();
        lastAttackTick.clear();
        org.bukkit.event.HandlerList.unregisterAll(this);
    }
}
