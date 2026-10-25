package fr.cliker.clikerv2.bag;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sept coffres personnels persistants, avec migration de l'ancien sac dans le premier coffre. */
public final class BagManager implements Listener {
    public static final int MAX_CAPACITY = 7;
    public static final int STORAGE_SIZE = 54;

    private final JavaPlugin plugin;
    private final BagStore store;
    private final Map<UUID, List<ItemStack[]>> bags = new HashMap<>();

    private record Bonuses(
            Map<MobTier, Double> mobs,
            boolean treasure,
            Map<String, Integer> general,
            java.util.Set<MobTier> bosses) {}

    private final java.util.function.Consumer<org.bukkit.entity.Player> savePlayer;
    private final org.bukkit.NamespacedKey snapshotKey;
    private final Map<UUID, Bonuses> bonuses = new HashMap<>();
    private final Map<UUID, Object> loadingSessions = new HashMap<>();

    public BagManager(JavaPlugin plugin, BagStore store) {
        this(plugin, store, org.bukkit.entity.Player::saveData);
    }

    public BagManager(
            JavaPlugin plugin,
            BagStore store,
            java.util.function.Consumer<org.bukkit.entity.Player> savePlayer) {
        this.plugin = plugin;
        this.store = store;
        this.savePlayer = savePlayer;
        snapshotKey = new org.bukkit.NamespacedKey(plugin, "bags_snapshot_v1");
    }

    public int capacity(Permissible permissible) {
        for (int n = 9; n >= 2; n--) {
            if (permissible.hasPermission("cliker.sac." + n)) return Math.min(n, MAX_CAPACITY);
        }
        return 1;
    }

    public boolean isLoaded(UUID uuid) {
        return bags.containsKey(uuid);
    }

    public ItemStack[] contents(UUID uuid, int bag) {
        return copy(requireBags(uuid).get(checkBag(bag)));
    }

    public void setContents(UUID uuid, int bag, ItemStack[] contents) {
        if (contents.length != STORAGE_SIZE)
            throw new IllegalArgumentException("Un sac doit contenir 54 cases");
        List<ItemStack[]> playerBags = requireBags(uuid);
        if (Arrays.equals(playerBags.get(checkBag(bag)), contents)) return;
        playerBags.set(checkBag(bag), copy(contents));
        refreshBonuses(uuid);
        var player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            player.getPersistentDataContainer()
                    .set(
                            snapshotKey,
                            org.bukkit.persistence.PersistentDataType.BYTE_ARRAY,
                            serialize(playerBags)
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try {
                savePlayer.accept(player);
            } catch (RuntimeException error) {
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.SEVERE,
                                "Sauvegarde joueur des sacs à réessayer : " + uuid,
                                error);
            }
        }
        store.save(uuid, serialize(playerBags))
                .whenComplete(
                        (ignored, error) -> {
                            if (error != null)
                                plugin.getLogger()
                                        .severe(
                                                "Impossible de sauvegarder les sacs de "
                                                        + uuid
                                                        + " : "
                                                        + error.getMessage());
                        });
    }

    /** Commit inventaire+sacs dans le même playerdata, puis copie SQL. */
    public void transfer(
            org.bukkit.entity.Player player, int bag, ItemStack[] after, ItemStack[] inventory) {
        if (after.length != STORAGE_SIZE || inventory.length != 36)
            throw new IllegalArgumentException("Plan de transfert invalide");
        UUID uuid = player.getUniqueId();
        var current = requireBags(uuid);
        var before = copy(current.get(checkBag(bag)));
        var oldInventory = player.getInventory().getStorageContents();
        byte[] oldSnapshot =
                player.getPersistentDataContainer()
                        .get(snapshotKey, org.bukkit.persistence.PersistentDataType.BYTE_ARRAY);
        current.set(bag, copy(after));
        String serialized = serialize(current);
        player.getPersistentDataContainer()
                .set(
                        snapshotKey,
                        org.bukkit.persistence.PersistentDataType.BYTE_ARRAY,
                        serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (int slot = 0; slot < inventory.length; slot++)
            player.getInventory().setItem(slot, inventory[slot]);
        try {
            savePlayer.accept(player);
        } catch (RuntimeException error) {
            current.set(bag, before);
            for (int slot = 0; slot < oldInventory.length; slot++)
                player.getInventory().setItem(slot, oldInventory[slot]);
            if (oldSnapshot == null) player.getPersistentDataContainer().remove(snapshotKey);
            else
                player.getPersistentDataContainer()
                        .set(
                                snapshotKey,
                                org.bukkit.persistence.PersistentDataType.BYTE_ARRAY,
                                oldSnapshot);
            throw error;
        }
        refreshBonuses(uuid);
        store.save(uuid, serialized)
                .exceptionally(
                        error -> {
                            plugin.getLogger()
                                    .log(
                                            java.util.logging.Level.SEVERE,
                                            "Copie SQL des sacs à réessayer ; snapshot joueur"
                                                    + " conservé : "
                                                    + uuid,
                                            error);
                            return null;
                        });
    }

    private List<ItemStack[]> requireBags(UUID uuid) {
        List<ItemStack[]> result = bags.get(uuid);
        if (result == null)
            throw new IllegalStateException("Les sacs du joueur ne sont pas chargés");
        return result;
    }

    private static int checkBag(int bag) {
        if (bag < 0 || bag >= MAX_CAPACITY)
            throw new IllegalArgumentException("Numéro de sac invalide");
        return bag;
    }

    private static ItemStack[] copy(ItemStack[] items) {
        return Arrays.stream(items)
                .map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
    }

    private void refreshBonuses(UUID owner) {
        Map<MobTier, Double> damage = new java.util.EnumMap<>(MobTier.class);
        boolean treasure = false;
        Map<String, Integer> general = new HashMap<>();
        java.util.Set<MobTier> bosses = new java.util.HashSet<>();
        for (ItemStack[] bag : requireBags(owner))
            for (ItemStack item : bag) {
                if (item == null || item.getType().isAir()) continue;
                MobTier tier = TalismanItem.mobTierOf(item);
                if (tier != null)
                    damage.merge(tier, TalismanItem.mobBonusPercent(tier), Double::sum);
                treasure |= TalismanItem.isTreasure(item);
                if (item.hasItemMeta()) {
                    String id =
                            item.getItemMeta()
                                    .getPersistentDataContainer()
                                    .get(
                                            new org.bukkit.NamespacedKey(
                                                    plugin, "collection_talisman"),
                                            org.bukkit.persistence.PersistentDataType.STRING);
                    if (id != null) {
                        String[] parts = id.split(":");
                        if (parts.length == 3
                                && parts[0].equals("general")
                                && java.util.Set.of(
                                                "lame",
                                                "richesse",
                                                "sagesse",
                                                "fortune",
                                                "vitalite")
                                        .contains(parts[1])
                                && parts[2].matches("[1-5]"))
                            general.merge(parts[1], Integer.parseInt(parts[2]), Math::max);
                        if (parts.length == 2
                                && parts[0].equals("boss")
                                && MobTier.parse(parts[1]) != null)
                            bosses.add(MobTier.parse(parts[1]));
                    }
                }
            }
        bonuses.put(
                owner,
                new Bonuses(
                        Map.copyOf(damage),
                        treasure,
                        Map.copyOf(general),
                        java.util.Set.copyOf(bosses)));
    }

    public double mobDamageBonusPercent(UUID owner, MobTier tier) {
        var bonus = bonuses.get(owner);
        return bonus == null ? 0 : bonus.mobs().getOrDefault(tier, 0.0);
    }

    public int generalTier(UUID owner, String kind) {
        var data = bonuses.get(owner);
        return data == null ? 0 : data.general.getOrDefault(kind, 0);
    }

    public boolean hasBossTalisman(UUID owner, MobTier mob) {
        var data = bonuses.get(owner);
        return data != null && data.bosses.contains(mob);
    }

    public boolean hasTreasureTalisman(UUID owner) {
        var bonus = bonuses.get(owner);
        return bonus != null && bonus.treasure();
    }

    static String serialize(List<ItemStack[]> bags) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("version", 2);
        for (int i = 0; i < MAX_CAPACITY; i++) config.set("bags." + i, Arrays.asList(bags.get(i)));
        return config.saveToString();
    }

    static List<ItemStack[]> deserialize(String yaml) throws InvalidConfigurationException {
        List<ItemStack[]> bags = new ArrayList<>();
        for (int i = 0; i < MAX_CAPACITY; i++) bags.add(new ItemStack[STORAGE_SIZE]);
        if (yaml == null || yaml.isEmpty()) return bags;
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (RuntimeException exception) {
            throw new InvalidConfigurationException(exception);
        }
        if (!config.contains("version")) {
            if (!config.contains("items"))
                throw new InvalidConfigurationException("Ancien sac sans contenu reconnu");
            readItems(config, "items", bags.getFirst());
        } else {
            if (config.getInt("version") != 2)
                throw new InvalidConfigurationException("Version des sacs inconnue");
            for (int i = 0; i < MAX_CAPACITY; i++) {
                if (!config.contains("bags." + i))
                    throw new InvalidConfigurationException("Coffre manquant : " + i);
                readItems(config, "bags." + i, bags.get(i));
            }
        }
        return bags;
    }

    private static void readItems(YamlConfiguration config, String path, ItemStack[] target)
            throws InvalidConfigurationException {
        if (!config.contains(path)) return;
        List<?> raw = config.getList(path);
        if (raw == null || raw.size() > STORAGE_SIZE)
            throw new InvalidConfigurationException("Contenu du sac invalide : " + path);
        for (int i = 0; i < raw.size(); i++) {
            Object item = raw.get(i);
            if (item != null && !(item instanceof ItemStack))
                throw new InvalidConfigurationException("Objet du sac invalide : " + path);
            target[i] = item == null ? null : ((ItemStack) item).clone();
        }
    }

    public void loadOnlinePlayers() {
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers())
            loadPlayer(player.getUniqueId(), player.getName());
    }

    private void loadPlayer(UUID uuid, String name) {
        Object session = new Object();
        loadingSessions.put(uuid, session);
        bags.remove(uuid);
        bonuses.remove(uuid);
        store.load(uuid)
                .whenComplete(
                        (yaml, error) -> {
                            if (!plugin.isEnabled()) return;
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        if (loadingSessions.get(uuid) != session) return;
                                        if (error != null) {
                                            plugin.getLogger()
                                                    .severe(
                                                            "Impossible de charger les sacs de "
                                                                    + name
                                                                    + " : "
                                                                    + error.getMessage());
                                            return;
                                        }
                                        try {
                                            var player = plugin.getServer().getPlayer(uuid);
                                            byte[] snapshot =
                                                    player == null
                                                            ? null
                                                            : player.getPersistentDataContainer()
                                                                    .get(
                                                                            snapshotKey,
                                                                            org.bukkit.persistence
                                                                                    .PersistentDataType
                                                                                    .BYTE_ARRAY);
                                            bags.put(
                                                    uuid,
                                                    deserialize(
                                                            snapshot == null
                                                                    ? yaml
                                                                    : new String(
                                                                            snapshot,
                                                                            java.nio.charset
                                                                                    .StandardCharsets
                                                                                    .UTF_8)));
                                            refreshBonuses(uuid);
                                        } catch (InvalidConfigurationException exception) {
                                            plugin.getLogger()
                                                    .severe(
                                                            "Sacs invalides pour "
                                                                    + name
                                                                    + " : "
                                                                    + exception.getMessage());
                                        }
                                    });
                        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        loadingSessions.remove(uuid);
        bags.remove(uuid);
        bonuses.remove(uuid);
    }
}
