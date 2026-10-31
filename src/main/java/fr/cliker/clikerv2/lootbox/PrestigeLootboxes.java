package fr.cliker.clikerv2.lootbox;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.prestige.PrestigeStore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Même progression de rareté pour le Daily et les jeux de chat. */
public final class PrestigeLootboxes {
    private final NavigableMap<Integer, LootboxTier> tiers = new TreeMap<>();
    private final PrestigeStore prestige;

    public PrestigeLootboxes(JavaPlugin plugin, DatabaseManager db) {
        prestige = new PrestigeStore(db);
        var settings = new Settings(plugin, "recompenses/caisses-prestige.yml");
        var section = settings.yaml.getConfigurationSection("paliers");
        if (section != null)
            for (String key : section.getKeys(false)) {
                int threshold = Integer.parseInt(key);
                var tier = LootboxTier.parse(section.getString(key, ""));
                if (threshold < 0 || threshold > 100 || tier == null)
                    throw new IllegalArgumentException("Palier de caisse invalide : " + key);
                tiers.put(threshold, tier);
            }
        tiers.putIfAbsent(0, LootboxTier.COMMUN);
        Bukkit.getServicesManager()
                .register(
                        PrestigeLootboxes.class,
                        this,
                        plugin,
                        org.bukkit.plugin.ServicePriority.Normal);
    }

    public LootboxTier tier(int level) {
        return tiers.floorEntry(Math.max(0, level)).getValue();
    }

    public static CompletableFuture<LootboxTier> resolve(UUID owner) {
        var service = Bukkit.getServicesManager().load(PrestigeLootboxes.class);
        return service == null
                ? CompletableFuture.completedFuture(LootboxTier.COMMUN)
                : service.prestige.getLevel(owner).thenApply(service::tier);
    }

    public void shutdown() {
        Bukkit.getServicesManager().unregister(PrestigeLootboxes.class, this);
    }
}
