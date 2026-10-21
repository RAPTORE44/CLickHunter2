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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PrestigeManager implements Listener {
    private final JavaPlugin plugin;
    private final PrestigeStore store;
    private final MobKillStore killStore;
    private final Map<UUID, Integer> cache = new HashMap<>();
    private TabListManager tabList;

    public PrestigeManager(JavaPlugin plugin, PrestigeStore store, MobKillStore killStore) {
        this.plugin = plugin;
        this.store = store;
        this.killStore = killStore;
    }

    /** Permet de rafraîchir le tab dès que le niveau de prestige est chargé (évite le tag manquant à la connexion). */
    public void setTabListManager(TabListManager tabList) { this.tabList = tabList; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer());
    }

    private void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        store.getLevel(uuid).whenComplete((level, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) { plugin.getLogger().severe("Impossible de charger le prestige : " + error.getMessage()); return; }
            cache.put(uuid, level);
            if (tabList != null && player.isOnline()) tabList.apply(player);
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

    public int getLevel(UUID uuid) { return cache.getOrDefault(uuid, 0); }

    public long ecusBonus(UUID uuid) { return getLevel(uuid) * PrestigeType.ECUS_PER_LEVEL; }
    public long gemmesBonusTenths(UUID uuid) { return getLevel(uuid) * PrestigeType.GEMMES_PER_LEVEL * 10; }
    public long xpBonus(UUID uuid) { return getLevel(uuid) * PrestigeType.XP_PER_LEVEL; }
    public long forceBonus(UUID uuid) { return getLevel(uuid) * PrestigeType.FORCE_PER_LEVEL; }

    /** Vérifie et applique le prestige suivant si les conditions sont remplies. Callback appelé après résolution (succès ou échec). */
    public void attemptPrestige(Player player, Runnable onComplete) {
        int currentLevel = getLevel(player.getUniqueId());
        int nextPrestige = currentLevel + 1;
        int requiredLevel = PrestigeType.requiredLevel(nextPrestige);
        long requiredKills = PrestigeType.requiredKills(nextPrestige);

        killStore.getTotal(player.getUniqueId()).whenComplete((kills, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null || !player.isOnline()) { if (onComplete != null) onComplete.run(); return; }
            if (player.getLevel() < requiredLevel || kills < requiredKills) {
                player.sendMessage(Component.text("Conditions non remplies pour le Prestige " + PrestigeType.toRoman(nextPrestige) + " : ", NamedTextColor.RED)
                        .append(Component.text("niveau " + player.getLevel() + "/" + requiredLevel + ", ", NamedTextColor.GRAY))
                        .append(Component.text(Numbers.compact(kills) + "/" + Numbers.compact(requiredKills) + " kills", NamedTextColor.GRAY)));
                if (onComplete != null) onComplete.run();
                return;
            }
            store.setLevel(player.getUniqueId(), nextPrestige);
            cache.put(player.getUniqueId(), nextPrestige);
            player.setLevel(0);
            player.setExp(0f);
            player.setTotalExperience(0);
            player.sendMessage(Component.text("✦ Prestige " + PrestigeType.toRoman(nextPrestige) + " atteint ! ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("+100 Écus, +1 Gemme, +10 XP, +10 Force par kill (cumulatif).", NamedTextColor.WHITE)));
            plugin.getLogger().info(player.getName() + " a atteint le Prestige " + PrestigeType.toRoman(nextPrestige)
                    + " (total cumulé : +" + ecusBonus(player.getUniqueId()) + " Écus/kill, +" + (gemmesBonusTenths(player.getUniqueId()) / 10)
                    + " Gemmes/kill, +" + xpBonus(player.getUniqueId()) + " XP/kill, +" + forceBonus(player.getUniqueId()) + " Dégâts).");
            if (onComplete != null) onComplete.run();
        }));
    }
}
