package fr.cliker.clikerv2.prestige;

import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.display.TabListManager;
import fr.cliker.clikerv2.mobs.MobKillStore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public final class PrestigeManager implements Listener {
    private final JavaPlugin plugin;
    private final PrestigeStore store;
    private final MobKillStore killStore;
    private final Map<UUID, Integer> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Object> loadTokens = new java.util.HashMap<>();
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> resetRetries =
            new java.util.HashMap<>();
    private boolean stopped;
    private TabListManager tabList;
    private final java.util.Set<UUID> pending = new java.util.HashSet<>();

    public PrestigeManager(JavaPlugin plugin, PrestigeStore store, MobKillStore killStore) {
        this.plugin = plugin;
        this.store = store;
        this.killStore = killStore;
    }

    /**
     * Permet de rafraîchir le tab dès que le niveau de prestige est chargé (évite le tag manquant à
     * la connexion).
     */
    public void setTabListManager(TabListManager tabList) {
        this.tabList = tabList;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer());
    }

    private void loadPlayer(Player player) {
        if (stopped) return;
        UUID uuid = player.getUniqueId();
        Object token = new Object();
        loadTokens.put(uuid, token);
        store.getLevel(uuid)
                .whenComplete(
                        (level, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .severe(
                                                                "Impossible de charger le"
                                                                        + " prestige : "
                                                                        + error.getMessage());
                                                return;
                                            }
                                            if (stopped
                                                    || loadTokens.get(uuid) != token
                                                    || !fr.cliker.clikerv2.runtime.PlayerSessions
                                                            .isCurrent(player)) return;
                                            cache.put(uuid, level);
                                            applyPendingReset(player);
                                            if (tabList != null && player.isOnline())
                                                tabList.apply(player);
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
        UUID owner = event.getPlayer().getUniqueId();
        cache.remove(owner);
        loadTokens.remove(owner);
        var retry = resetRetries.remove(owner);
        if (retry != null) retry.cancel();
    }

    public int getLevel(UUID uuid) {
        return cache.getOrDefault(uuid, 0);
    }

    public long ecusBonus(UUID uuid) {
        return getLevel(uuid) * PrestigeType.ECUS_PER_LEVEL;
    }

    public long gemmesBonusTenths(UUID uuid) {
        return getLevel(uuid) * PrestigeType.GEMMES_PER_LEVEL * 10;
    }

    public long xpBonus(UUID uuid) {
        return getLevel(uuid) * PrestigeType.XP_PER_LEVEL;
    }

    public long forceBonus(UUID uuid) {
        return getLevel(uuid) * PrestigeType.FORCE_PER_LEVEL;
    }

    /**
     * Vérifie et applique le prestige suivant si les conditions sont remplies. Callback appelé
     * après résolution (succès ou échec).
     */
    public void attemptPrestige(Player player, Runnable onComplete) {
        if (!player.hasPermission("cliker.prestige.use")) {
            player.sendMessage(
                    Component.text(
                            "Tu n’as pas la permission de passer un prestige.",
                            NamedTextColor.RED));
            if (onComplete != null) onComplete.run();
            return;
        }
        UUID owner = player.getUniqueId();
        if (stopped || !fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(player)) return;
        Object session = loadTokens.get(owner);
        if (!cache.containsKey(owner) || !pending.add(owner)) {
            if (onComplete != null) onComplete.run();
            return;
        }
        int current = getLevel(owner), next = current + 1;
        if (next > 100) {
            pending.remove(owner);
            player.sendMessage(
                    Component.text("Tu as atteint le prestige maximal !", NamedTextColor.GREEN));
            if (onComplete != null) onComplete.run();
            return;
        }
        int requiredLevel = PrestigeType.requiredLevel(next);
        long requiredKills = PrestigeType.requiredKills(next);
        if (player.getLevel() < requiredLevel) {
            pending.remove(owner);
            player.sendMessage(
                    Component.text(
                            "Prestige "
                                    + PrestigeType.toRoman(next)
                                    + " : niveau "
                                    + requiredLevel
                                    + " et "
                                    + Numbers.compact(requiredKills)
                                    + " éliminations requis.",
                            NamedTextColor.YELLOW));
            if (onComplete != null) onComplete.run();
            return;
        }
        store.advance(owner, current, requiredKills)
                .whenComplete(
                        (accepted, error) -> {
                            if (!plugin.isEnabled()) return;
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        pending.remove(owner);
                                        if (stopped) return;
                                        if (loadTokens.get(owner) != session
                                                || !fr.cliker.clikerv2.runtime.PlayerSessions
                                                        .isCurrent(player)) {
                                            if (error != null)
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Prestige terminé après déconnexion"
                                                                        + " avec erreur",
                                                                error);
                                            else if (Boolean.TRUE.equals(accepted)) {
                                                var currentPlayer =
                                                        org.bukkit.Bukkit.getPlayer(owner);
                                                if (currentPlayer != null)
                                                    loadPlayer(currentPlayer);
                                            }
                                            return;
                                        }
                                        if (error != null) {
                                            plugin.getLogger()
                                                    .log(
                                                            java.util.logging.Level.SEVERE,
                                                            "Prestige non enregistré",
                                                            error);
                                            if (player.isOnline())
                                                player.sendMessage(
                                                        Component.text(
                                                                "Prestige impossible."
                                                                        + " Réessaie dans"
                                                                        + " quelques instants.",
                                                                NamedTextColor.RED));
                                        } else if (!Boolean.TRUE.equals(accepted)) {
                                            if (player.isOnline())
                                                player.sendMessage(
                                                        Component.text(
                                                                "Conditions non remplies : "
                                                                        + Numbers.compact(
                                                                                requiredKills)
                                                                        + " éliminations"
                                                                        + " requises, ou"
                                                                        + " prestige déjà"
                                                                        + " modifié.",
                                                                NamedTextColor.RED));
                                        } else if (player.isOnline()) {
                                            cache.put(owner, next);
                                            applyPendingReset(player);
                                            if (tabList != null) tabList.apply(player);
                                            player.sendMessage(
                                                    Component.text(
                                                            "Prestige "
                                                                    + PrestigeType.toRoman(next)
                                                                    + " atteint ! Tes bonus"
                                                                    + " par élimination"
                                                                    + " augmentent.",
                                                            NamedTextColor.LIGHT_PURPLE));
                                        }
                                        if (onComplete != null) onComplete.run();
                                    });
                        });
    }

    private void applyPendingReset(Player player) {
        if (stopped) return;
        UUID owner = player.getUniqueId();
        Object session = loadTokens.get(owner);
        store.pendingReset(owner)
                .whenComplete(
                        (reset, error) -> {
                            if (!plugin.isEnabled()) return;
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        if (stopped) return;
                                        if (error != null) {
                                            plugin.getLogger()
                                                    .log(
                                                            java.util.logging.Level.SEVERE,
                                                            "Lecture de la réinitialisation XP"
                                                                    + " impossible",
                                                            error);
                                            retryReset(owner);
                                            return;
                                        }
                                        if (reset == null
                                                || loadTokens.get(owner) != session
                                                || !fr.cliker.clikerv2.runtime.PlayerSessions
                                                        .isCurrent(player)) return;
                                        var marker =
                                                new org.bukkit.NamespacedKey(
                                                        plugin, "prestige_xp_reset");
                                        var receipt =
                                                new org.bukkit.NamespacedKey(
                                                        plugin, "earned_xp_applied");
                                        var pdc = player.getPersistentDataContainer();
                                        if (pdc.getOrDefault(
                                                        marker,
                                                        org.bukkit.persistence.PersistentDataType
                                                                .INTEGER,
                                                        0)
                                                < reset.level()) {
                                            player.setLevel(0);
                                            player.setExp(0);
                                            player.setTotalExperience(0);
                                            pdc.set(
                                                    receipt,
                                                    org.bukkit.persistence.PersistentDataType.LONG,
                                                    Math.max(
                                                            reset.earnedXP(),
                                                            pdc.getOrDefault(
                                                                    receipt,
                                                                    org.bukkit.persistence
                                                                            .PersistentDataType
                                                                            .LONG,
                                                                    0L)));
                                            pdc.set(
                                                    marker,
                                                    org.bukkit.persistence.PersistentDataType
                                                            .INTEGER,
                                                    reset.level());
                                        }
                                        try {
                                            player.saveData();
                                        } catch (RuntimeException failure) {
                                            plugin.getLogger()
                                                    .log(
                                                            java.util.logging.Level.SEVERE,
                                                            "Réinitialisation XP non"
                                                                    + " sauvegardée : reprise à"
                                                                    + " la connexion",
                                                            failure);
                                            retryReset(owner);
                                            return;
                                        }
                                        store.acknowledgeReset(owner, reset.level())
                                                .whenComplete(
                                                        (ignored, ackError) -> {
                                                            if (ackError != null
                                                                    && plugin.isEnabled())
                                                                fr.cliker.clikerv2.runtime
                                                                        .ServerTasks.run(
                                                                        plugin,
                                                                        () -> retryReset(owner));
                                                        });
                                    });
                        });
    }

    private void retryReset(UUID owner) {
        if (stopped
                || !plugin.isEnabled()
                || org.bukkit.Bukkit.getPlayer(owner) == null
                || resetRetries.containsKey(owner)) return;
        resetRetries.put(
                owner,
                plugin.getServer()
                        .getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    resetRetries.remove(owner);
                                    var current = org.bukkit.Bukkit.getPlayer(owner);
                                    if (!stopped && current != null) applyPendingReset(current);
                                },
                                100L));
    }

    public void shutdown() {
        stopped = true;
        resetRetries.values().forEach(org.bukkit.scheduler.BukkitTask::cancel);
        resetRetries.clear();
        loadTokens.clear();
        cache.clear();
        pending.clear();
        org.bukkit.event.HandlerList.unregisterAll(this);
    }
}
