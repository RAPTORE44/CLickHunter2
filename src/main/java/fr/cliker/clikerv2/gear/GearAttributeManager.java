package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applique en continu les bonus d'armure passifs (enchants/reforge/runes) : vie max, régénération,
 * vitesse, réduction de dégâts.
 */
public final class GearAttributeManager implements Listener {
    private static final double MAX_REDUCTION_PERCENT = 80.0;

    private final JavaPlugin plugin;
    private org.bukkit.scheduler.BukkitTask task;
    private final NamespacedKey healthKey;
    private final NamespacedKey speedKey;
    private final Map<UUID, Double> damageReduction = new HashMap<>();

    public GearAttributeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.healthKey = new NamespacedKey(plugin, "cliker_gear_health");
        this.speedKey = new NamespacedKey(plugin, "cliker_gear_speed");
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) applyTo(player);
    }

    public record ArmorBonuses(double health, double reductionPercent, double speed) {}

    public static ArmorBonuses armorBonuses(Player player) {
        double health = 0, reduction = 0, speed = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || !piece.hasItemMeta()) continue;
            String tierId =
                    piece.getItemMeta()
                            .getPersistentDataContainer()
                            .get(TierGear.ARMOR_KEY, PersistentDataType.STRING);
            if (tierId == null) continue;
            MobTier tier = MobTier.parse(tierId);
            if (tier == null) continue;

            reduction +=
                    EnchantType.DEFENCE.magnitudeAt(
                            tier, ItemMods.getEnchantLevel(piece, EnchantType.DEFENCE));

            ReforgeType reforge = ItemMods.getReforge(piece);
            if (reforge != null) {
                health += reforge.armorHealthBonus(tier);
                reduction += reforge.armorDamageReductionPercent();
                speed += reforge.armorSpeedBonus();
            }
        }

        return new ArmorBonuses(health, Math.min(MAX_REDUCTION_PERCENT, reduction), speed);
    }

    private void applyTo(Player player) {
        ArmorBonuses bonuses = armorBonuses(player);
        applyAttribute(player, Attribute.MAX_HEALTH, healthKey, bonuses.health());
        applyAttribute(player, Attribute.MOVEMENT_SPEED, speedKey, bonuses.speed());
        damageReduction.put(player.getUniqueId(), bonuses.reductionPercent());
    }

    private void applyAttribute(
            Player player, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier existing = instance.getModifier(key);
        if (existing != null
                && Double.compare(existing.getAmount(), amount) == 0
                && existing.getOperation() == AttributeModifier.Operation.ADD_NUMBER) return;
        if (existing != null) instance.removeModifier(key);
        if (amount > 0)
            instance.addModifier(
                    new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    /**
     * Réduit les dégâts reçus par le joueur selon ses bonus d'armure (Absorption/Ferme/Résistance).
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double percent = damageReduction.getOrDefault(player.getUniqueId(), 0.0);
        if (percent <= 0) return;
        event.setDamage(event.getDamage() * (1 - percent / 100.0));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        damageReduction.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        if (task != null) task.cancel();
        org.bukkit.event.HandlerList.unregisterAll(this);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            var health = player.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) health.removeModifier(healthKey);
            var speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(speedKey);
        }
        damageReduction.clear();
    }
}
