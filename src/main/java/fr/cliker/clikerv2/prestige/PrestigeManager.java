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
        UUID uuid = player.getUniqueId();
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
                                            if (!player.isOnline()) return;
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
        cache.remove(event.getPlayer().getUniqueId());
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
        UUID owner = player.getUniqueId();
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
        UUID owner = player.getUniqueId();
        store.pendingReset(owner)
                .whenComplete(
                        (reset, error) -> {
                            if (!plugin.isEnabled()) return;
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        if (error != null || reset == null || !player.isOnline())
                                            return;
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
                                            retryReset(player);
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
                                                                        () -> retryReset(player));
                                                        });
                                    });
                        });
    }

    private void retryReset(Player player) {
        if (!plugin.isEnabled() || !player.isOnline()) return;
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (player.isOnline()) applyPendingReset(player);
                        },
                        100L);
    }
}
