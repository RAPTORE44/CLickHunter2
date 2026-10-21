package fr.cliker.clikerv2.upgrade;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

public final class UpgradeManager implements Listener {
    private final JavaPlugin plugin;
    private final UpgradeStore store;
    private final EconomyService economy;
    private final Map<UUID, Map<UpgradeType, Integer>> cache = new HashMap<>();

    public UpgradeManager(JavaPlugin plugin, UpgradeStore store, EconomyService economy) {
        this.plugin = plugin;
        this.store = store;
        this.economy = economy;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer());
    }

    private void loadPlayer(Player player) {
        store.getLevels(player.getUniqueId()).whenComplete((levels, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) { plugin.getLogger().severe("Impossible de charger les upgrades : " + error.getMessage()); return; }
            cache.put(player.getUniqueId(), levels);
            if (player.isOnline()) applyAttributes(player, levels);
        }));
    }

    /** Recharge le cache pour tous les joueurs déjà connectés — nécessaire après un reload à chaud (PlugMan, /reload) qui ne redéclenche pas PlayerJoinEvent. */
    public void loadOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) loadPlayer(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    public int getLevel(UUID uuid, UpgradeType type) {
        return cache.getOrDefault(uuid, Map.of()).getOrDefault(type, 0);
    }

    public double getBonus(UUID uuid, UpgradeType type) {
        return type.bonusAt(getLevel(uuid, type));
    }

    public Map<UpgradeType, Integer> levelsOf(UUID uuid) {
        return new EnumMap<>(cache.getOrDefault(uuid, Map.of()));
    }

    public void purchase(Player player, UpgradeType type) { purchase(player, type, null); }

    public void purchase(Player player, UpgradeType type, Runnable onComplete) {
        int currentLevel = getLevel(player.getUniqueId(), type);
        if (currentLevel >= UpgradeType.MAX_LEVEL) {
            player.sendMessage(Component.text(type.label() + " est déjà au niveau maximum (" + UpgradeType.MAX_LEVEL + ").", NamedTextColor.RED));
            if (onComplete != null) onComplete.run();
            return;
        }
        int nextLevel = currentLevel + 1;
        long costTenths = UpgradeType.costForLevel(nextLevel) * 10L;

        economy.change(player.getUniqueId(), player.getName(), CurrencyType.GEMMES_SOMBRES, -costTenths).whenComplete((balance, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                player.sendMessage(Component.text(cause.getMessage() == null ? "Erreur : Gemmes Sombres insuffisantes." : cause.getMessage(), NamedTextColor.RED));
                if (onComplete != null) onComplete.run();
                return;
            }
            store.setLevel(player.getUniqueId(), type, nextLevel);
            cache.computeIfAbsent(player.getUniqueId(), key -> new EnumMap<>(UpgradeType.class)).put(type, nextLevel);
            applyAttributes(player, cache.get(player.getUniqueId()));
            player.sendMessage(Component.text(type.symbol() + " " + type.label() + " ", type.color())
                    .append(Component.text("niveau " + nextLevel + "/" + UpgradeType.MAX_LEVEL + " ! ", NamedTextColor.GREEN))
                    .append(Component.text("(" + formatBonus(type, nextLevel) + " " + type.unitLabel() + ")", NamedTextColor.GRAY)));
            if (onComplete != null) onComplete.run();
        }));
    }

    public String formatBonus(UpgradeType type, int level) {
        return "+" + Numbers.compact(type.bonusAt(level));
    }

    private void applyAttributes(Player player, Map<UpgradeType, Integer> levels) {
        applyAttribute(player, Attribute.MAX_HEALTH, "cliker_upgrade_vitalite", UpgradeType.VITALITE.bonusAt(levels.getOrDefault(UpgradeType.VITALITE, 0)));
        applyAttribute(player, Attribute.MOVEMENT_SPEED, "cliker_upgrade_vitesse", UpgradeType.VITESSE.bonusAt(levels.getOrDefault(UpgradeType.VITESSE, 0)));
        applyAttribute(player, Attribute.ARMOR, "cliker_upgrade_resistance", UpgradeType.RESISTANCE.bonusAt(levels.getOrDefault(UpgradeType.RESISTANCE, 0)));
    }

    private void applyAttribute(Player player, Attribute attribute, String key, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        NamespacedKey modifierKey = new NamespacedKey(plugin, key);
        AttributeModifier existing = instance.getModifier(modifierKey);
        if (existing != null) instance.removeModifier(modifierKey);
        if (amount > 0) {
            instance.addModifier(new AttributeModifier(modifierKey, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }
}
