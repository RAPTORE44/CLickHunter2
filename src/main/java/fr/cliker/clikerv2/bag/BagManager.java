package fr.cliker.clikerv2.bag;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sac persistant par joueur : capacité selon permission cliker.sac.&lt;N&gt; (2-9), défaut 1. Talismans actifs tant qu'ils sont dedans. */
public final class BagManager implements Listener {
    public static final int MAX_CAPACITY = 9;

    private final JavaPlugin plugin;
    private final BagStore store;
    private final Map<UUID, List<ItemStack>> bags = new HashMap<>();

    public BagManager(JavaPlugin plugin, BagStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public int capacity(Permissible permissible) {
        for (int n = MAX_CAPACITY; n >= 2; n--) {
            if (permissible.hasPermission("cliker.sac." + n)) return n;
        }
        return 1;
    }

    public List<ItemStack> items(UUID uuid) {
        return bags.computeIfAbsent(uuid, key -> new ArrayList<>());
    }

    public void setItems(UUID uuid, List<ItemStack> items) {
        List<ItemStack> cleaned = new ArrayList<>();
        for (ItemStack item : items) if (item != null) cleaned.add(item);
        bags.put(uuid, cleaned);
        store.save(uuid, serialize(cleaned));
    }

    /** Somme les bonus des talismans du mob visé présents dans le /sac. */
    public double mobDamageBonusPercent(UUID uuid, MobTier tier) {
        double bonus = 0;
        for (ItemStack item : items(uuid)) {
            if (tier.equals(TalismanItem.mobTierOf(item))) bonus += TalismanItem.mobBonusPercent(tier);
        }
        return bonus;
    }

    public boolean hasTreasureTalisman(UUID uuid) {
        return items(uuid).stream().anyMatch(TalismanItem::isTreasure);
    }

    private static String serialize(List<ItemStack> items) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("items", items);
        return config.saveToString();
    }

    private static List<ItemStack> deserialize(String yaml) {
        List<ItemStack> items = new ArrayList<>();
        if (yaml == null || yaml.isEmpty()) return items;
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(yaml); } catch (InvalidConfigurationException exception) { return items; }
        List<?> raw = config.getList("items");
        if (raw != null) for (Object object : raw) if (object instanceof ItemStack item) items.add(item);
        return items;
    }

    public void loadOnlinePlayers() {
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) loadPlayer(player.getUniqueId(), player.getName());
    }

    private void loadPlayer(UUID uuid, String name) {
        store.load(uuid).whenComplete((yaml, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) { plugin.getLogger().severe("Impossible de charger le sac de " + name + " : " + error.getMessage()); return; }
            bags.put(uuid, deserialize(yaml));
        }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bags.remove(event.getPlayer().getUniqueId());
    }
}
