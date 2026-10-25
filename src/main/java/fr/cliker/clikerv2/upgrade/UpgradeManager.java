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

public final class UpgradeManager implements Listener {
    private final JavaPlugin plugin;
    private final UpgradeStore store;
    private final EconomyService economy;
    private final java.util.Set<UUID> pending = new java.util.HashSet<>();
    private final Map<UUID, Object> loadTokens = new HashMap<>();
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
        Object token = new Object();
        loadTokens.put(player.getUniqueId(), token);
        store.getLevels(player.getUniqueId())
                .whenComplete(
                        (levels, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .severe(
                                                                "Impossible de charger les"
                                                                        + " upgrades : "
                                                                        + error.getMessage());
                                                return;
                                            }
                                            if (loadTokens.get(player.getUniqueId()) != token
                                                    || !fr.cliker.clikerv2.runtime.PlayerSessions
                                                            .isCurrent(player)) return;
                                            cache.put(player.getUniqueId(), levels);
                                            if (player.isOnline()) applyAttributes(player, levels);
                                        }));
    }

    /**
     * Recharge le cache pour tous les joueurs déjà connectés — nécessaire après un reload à chaud
     * (PlugMan, /reload) qui ne redéclenche pas PlayerJoinEvent.
     */
    public void loadOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) loadPlayer(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
        loadTokens.remove(event.getPlayer().getUniqueId());
    }

    public boolean isLoaded(UUID owner) {
        return cache.containsKey(owner);
    }

    public java.util.concurrent.CompletableFuture<Long> gems(Player player) {
        return economy.getBalance(
                player.getUniqueId(), player.getName(), CurrencyType.GEMMES_SOMBRES);
    }

    public int getLevel(UUID uuid, UpgradeType type) {
        return cache.getOrDefault(uuid, Map.of()).getOrDefault(type, 0);
    }

    public double getBonus(UUID uuid, UpgradeType type) {
        return type.bonusAt(getLevel(uuid, type));
    }

    public Map<UpgradeType, Integer> levelsOf(UUID uuid) {
        var result = new EnumMap<UpgradeType, Integer>(UpgradeType.class);
        result.putAll(cache.getOrDefault(uuid, Map.of()));
        return result;
    }

    public void purchase(Player player, UpgradeType type) {
        purchase(player, type, null);
    }

    public void purchase(Player player, UpgradeType type, Runnable onComplete) {
        purchase(player, type, false, onComplete);
    }

    public void purchase(Player player, UpgradeType type, boolean maximum, Runnable onComplete) {
        purchase(player, type, maximum ? 0 : 1, onComplete);
    }

    public void purchase(Player player, UpgradeType type, int quantity, Runnable onComplete) {
        UUID owner = player.getUniqueId();
        if (!player.hasPermission("cliker.upgrade.use")
                || !fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(player)) {
            if (onComplete != null) onComplete.run();
            return;
        }
        Object session = loadTokens.get(owner);
        if (!cache.containsKey(owner)) {
            player.sendMessage(
                    Component.text(
                            "Tes améliorations sont encore en cours de chargement.",
                            NamedTextColor.YELLOW));
            if (onComplete != null) onComplete.run();
            return;
        }
        if (!pending.add(owner)) {
            player.sendMessage(
                    Component.text(
                            "Une amélioration est déjà en cours. Patiente un instant.",
                            NamedTextColor.YELLOW));
            if (onComplete != null) onComplete.run();
            return;
        }
        store.purchase(owner, type, quantity)
                .whenComplete(
                        (level, error) -> {
                            if (!plugin.isEnabled()) return;
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        pending.remove(owner);
                                        if (loadTokens.get(owner) != session
                                                || !fr.cliker.clikerv2.runtime.PlayerSessions
                                                        .isCurrent(player)) {
                                            if (error != null)
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Achat terminé après déconnexion"
                                                                        + " avec erreur",
                                                                error);
                                            else if (level >= 0) {
                                                var current = org.bukkit.Bukkit.getPlayer(owner);
                                                if (current != null) loadPlayer(current);
                                            }
                                            return;
                                        }
                                        if (error != null) {
                                            plugin.getLogger()
                                                    .log(
                                                            java.util.logging.Level.SEVERE,
                                                            "Achat d'amélioration" + " impossible",
                                                            error);
                                            if (player.isOnline())
                                                player.sendMessage(
                                                        Component.text(
                                                                "Achat impossible. Aucun"
                                                                        + " débit validé.",
                                                                NamedTextColor.RED));
                                        } else if (level < 0) {
                                            if (player.isOnline())
                                                player.sendMessage(
                                                        Component.text(
                                                                level == -1
                                                                        ? "Gemmes Sombres"
                                                                                + " insuffisantes."
                                                                        : "Cette amélioration :"
                                                                              + " quantité"
                                                                              + " indisponible ou"
                                                                              + " au maximum.",
                                                                NamedTextColor.RED));
                                        } else if (player.isOnline()) {
                                            cache.computeIfAbsent(
                                                            owner,
                                                            key -> new EnumMap<>(UpgradeType.class))
                                                    .put(type, level);
                                            applyAttributes(player, cache.get(owner));
                                            player.sendMessage(
                                                    Component.text(
                                                            type.label()
                                                                    + " : niveau "
                                                                    + level
                                                                    + "/"
                                                                    + UpgradeType.MAX_LEVEL
                                                                    + " ("
                                                                    + formatBonus(type, level)
                                                                    + " "
                                                                    + type.unitLabel()
                                                                    + ").",
                                                            NamedTextColor.GREEN));
                                        }
                                        if (onComplete != null) onComplete.run();
                                    });
                        });
    }

    public String formatBonus(UpgradeType type, int level) {
        return "+" + Numbers.compact(type.bonusAt(level));
    }

    private void applyAttributes(Player player, Map<UpgradeType, Integer> levels) {
        applyAttribute(
                player,
                Attribute.MAX_HEALTH,
                "cliker_upgrade_vitalite",
                UpgradeType.VITALITE.bonusAt(levels.getOrDefault(UpgradeType.VITALITE, 0)));
        applyAttribute(
                player,
                Attribute.MOVEMENT_SPEED,
                "cliker_upgrade_vitesse",
                UpgradeType.VITESSE.bonusAt(levels.getOrDefault(UpgradeType.VITESSE, 0)));
        applyAttribute(
                player,
                Attribute.ARMOR,
                "cliker_upgrade_resistance",
                UpgradeType.RESISTANCE.bonusAt(levels.getOrDefault(UpgradeType.RESISTANCE, 0)));
    }

    private void applyAttribute(Player player, Attribute attribute, String key, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        NamespacedKey modifierKey = new NamespacedKey(plugin, key);
        AttributeModifier existing = instance.getModifier(modifierKey);
        if (existing != null
                && Double.compare(existing.getAmount(), amount) == 0
                && existing.getOperation() == AttributeModifier.Operation.ADD_NUMBER) return;
        if (existing != null) instance.removeModifier(modifierKey);
        if (amount > 0) {
            instance.addModifier(
                    new AttributeModifier(
                            modifierKey, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }
}
