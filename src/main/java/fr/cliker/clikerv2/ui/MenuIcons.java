package fr.cliker.clikerv2.ui;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/** Décoration des boutons seulement : aucune substitution d'objet ni de données métier. */
public final class MenuIcons implements Listener {
    private final JavaPlugin plugin;
    private final YamlConfiguration config;
    private final Set<String> catalog;
    private final Map<String, String> words = new LinkedHashMap<>();
    private final Map<UUID, Inventory> views = new HashMap<>();
    private final Set<UUID> loaded = new HashSet<>();
    private BukkitTask task;

    public MenuIcons(JavaPlugin plugin) {
        this.plugin = plugin;
        var file = new java.io.File(plugin.getDataFolder(), "gui/icons.yml");
        if (!file.exists()) plugin.saveResource("gui/icons.yml", false);
        config = fr.cliker.clikerv2.config.YamlFiles.load(file);
        try (var in = Objects.requireNonNull(plugin.getResource("gui/icons-catalog.txt"))) {
            catalog =
                    Set.copyOf(
                            new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Catalogue des icônes illisible", error);
        }
        var section = config.getConfigurationSection("mots");
        if (section != null)
            for (String word : section.getKeys(false)) {
                String id = section.getString(word);
                if (!catalog.contains(id))
                    throw new IllegalArgumentException("Icône inconnue : " + id);
                words.put(normalize(word), id);
            }
    }

    public void start() {
        if (config.getBoolean("active", true))
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    static boolean eligible(Inventory inventory, int slot) {
        var holder = inventory.getHolder();
        if (holder == null) return false;
        String type = holder.getClass().getName();
        if (!type.startsWith("fr.cliker.clikerv2.")) return false;
        if (type.contains("BagMenu$") && inventory.getSize() == 54) return false;
        if (type.contains("SellMenu$") && slot < 45) return false;
        if (type.contains("ClaimService$") && slot < 45) return false;
        if (type.contains("AuctionMenu$Listings") && slot < 45) return false;
        if (type.contains("AuctionMenu$Confirmation") && slot == 13) return false;
        if ((type.contains("EnchantMenu$")
                        || type.contains("ReforgeMenu$")
                        || type.contains("RuneMenu$"))
                && slot == 4) return false;
        if (type.contains("StatsMenu$") && Set.of(12, 21, 30, 39, 22).contains(slot)) return false;
        return true;
    }

    public void render(Inventory inventory) {
        if (!config.getBoolean("active", true)) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!eligible(inventory, slot)) continue;
            var item = inventory.getItem(slot);
            if (item == null || !item.hasItemMeta()) continue;
            var meta = item.getItemMeta();
            if (!meta.hasDisplayName()) continue;
            String name =
                    normalize(
                                    PlainTextComponentSerializer.plainText()
                                            .serialize(meta.displayName()))
                            .strip();
            if (name.isEmpty() || item.getType().name().endsWith("_GLASS_PANE")) continue;
            String menu =
                    inventory.getHolder().getClass().getName().split("\\.")[4].split("\\$")[0];
            String id = config.getString("menus." + menu + "." + slot);
            if (id == null)
                for (var word : words.entrySet())
                    if (name.contains(word.getKey())) {
                        id = word.getValue();
                        break;
                    }
            if (id == null) id = fallback(item.getType());
            if (meta.hasLore()) {
                String lore =
                        normalize(
                                meta.lore().stream()
                                        .map(PlainTextComponentSerializer.plainText()::serialize)
                                        .collect(java.util.stream.Collectors.joining("\n")));
                if (lore.contains("etat : verrouill")) id = "etats_cadenas_ferme_grise";
                else if (lore.contains("etat : deja recupere") || lore.contains("etat : recupere"))
                    id = "etats_coche";
            }
            if ((item.getType() == Material.GRAY_DYE
                            || (item.getType() == Material.BARRIER
                                    && !id.equals("navigation_fermer"))
                            || name.contains("non decouvert"))
                    && catalog.contains(id + "_grise")) id += "_grise";
            if (!catalog.contains(id)) continue;
            NamespacedKey model = new NamespacedKey("clickhunter", id);
            if (model.equals(meta.getItemModel())) continue;
            meta.setItemModel(model);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }
    }

    private static String fallback(Material type) {
        String name = type.name();
        if (name.endsWith("_SWORD")) return "combat_epee";
        if (name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")) return "combat_bouclier";
        if (name.endsWith("_SPAWN_EGG")) return "combat_cible";
        return switch (type) {
            case BARRIER -> "etats_cadenas_ferme_grise";
            case ARROW -> "navigation_retour";
            case NETHER_STAR -> "progression_etoile";
            case EMERALD, DIAMOND -> "economie_gemme";
            case CHEST, ENDER_CHEST -> "recompenses_coffre_ferme";
            case GOLD_INGOT, GOLD_NUGGET -> "economie_piece";
            case PLAYER_HEAD -> "interface_joueur";
            case LIME_DYE -> "etats_coche";
            case GRAY_DYE, RED_DYE -> "etats_croix";
            default -> "interface_info";
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        var inventory = event.getInventory();
        if (!java.util.stream.IntStream.range(0, inventory.getSize())
                .anyMatch(slot -> eligible(inventory, slot))) return;
        if (loaded.contains(player.getUniqueId()) || !config.getBoolean("exiger-pack-charge", true))
            render(inventory);
        views.put(player.getUniqueId(), inventory);
        if (task == null)
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 5, 5);
    }

    private void refresh() {
        views.entrySet()
                .removeIf(
                        entry -> {
                            Player player = Bukkit.getPlayer(entry.getKey());
                            if (player == null
                                    || player.getOpenInventory().getTopInventory()
                                            != entry.getValue()) return true;
                            if (loaded.contains(player.getUniqueId())
                                    || !config.getBoolean("exiger-pack-charge", true))
                                render(entry.getValue());
                            return false;
                        });
        if (views.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        views.remove(event.getPlayer().getUniqueId(), event.getInventory());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        views.remove(event.getPlayer().getUniqueId());
        loaded.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void pack(org.bukkit.event.player.PlayerResourcePackStatusEvent event) {
        if (event.getStatus()
                == org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)
            loaded.add(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        views.clear();
        loaded.clear();
        HandlerList.unregisterAll(this);
    }
}
