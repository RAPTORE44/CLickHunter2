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

    @FunctionalInterface
    interface Resolver {
        ItemStack resolve(String id);
    }

    private final Resolver resolver;
    private final Map<String, ItemStack> templates = new HashMap<>();
    private final Map<String, Long> retryAfter = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private java.lang.reflect.Method getInstance, getItemStack;
    private final NamespacedKey originalMaterial;
    private BukkitTask task;

    public MenuIcons(JavaPlugin plugin) {
        this(plugin, null);
    }

    MenuIcons(JavaPlugin plugin, Resolver resolver) {
        this.plugin = plugin;
        this.resolver = resolver == null ? this::itemsAdder : resolver;
        originalMaterial = new NamespacedKey(plugin, "menu_original_material");
        var file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "gui/icons.yml");
        if (!file.exists()) fr.cliker.clikerv2.config.ConfigFiles.install(plugin, "gui/icons.yml");
        config = fr.cliker.clikerv2.config.YamlFiles.load(file);
        try (var in = Objects.requireNonNull(plugin.getResource("gui/icons-catalog.txt"))) {
            catalog =
                    Set.copyOf(
                            new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Catalogue des icônes illisible", error);
        }
        boolean migrated = config.contains("exiger-pack-charge");
        config.set("exiger-pack-charge", null);
        for (String word : java.util.List.of("enchere", "hotel")) {
            if ("boutique_enchere".equals(config.getString("mots." + word))) {
                config.set("mots." + word, "boutique_vitrine");
                migrated = true;
            }
        }
        if (migrated)
            try {
                config.save(file);
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Migration des icônes impossible", error);
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

    public static void decorate(Inventory inventory) {
        var service = Bukkit.getServicesManager().load(MenuIcons.class);
        if (service != null) service.render(inventory);
    }

    public void start() {
        Bukkit.getServicesManager()
                .register(MenuIcons.class, this, plugin, org.bukkit.plugin.ServicePriority.Normal);
        if (config.getBoolean("active", true))
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        try {
            var type =
                    Class.forName("dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent")
                            .asSubclass(Event.class);
            plugin.getServer()
                    .getPluginManager()
                    .registerEvent(
                            type,
                            this,
                            EventPriority.MONITOR,
                            (listener, event) -> invalidate(),
                            plugin);
        } catch (ClassNotFoundException ignored) {
            // ItemsAdder optionnel : les boutons vanilla restent disponibles.
        }
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
            Material material = item.getType();
            String original =
                    meta.getPersistentDataContainer()
                            .get(
                                    originalMaterial,
                                    org.bukkit.persistence.PersistentDataType.STRING);
            if (original != null && Material.matchMaterial(original) != null)
                material = Material.matchMaterial(original);
            String name =
                    normalize(
                                    PlainTextComponentSerializer.plainText()
                                            .serialize(meta.displayName()))
                            .strip();
            if (name.isEmpty() || material.name().endsWith("_GLASS_PANE")) continue;
            String menu =
                    inventory.getHolder().getClass().getName().split("\\.")[4].split("\\$")[0];
            String id = config.getString("menus." + menu + "." + slot);
            if (id == null)
                for (var word : words.entrySet())
                    if (name.contains(word.getKey())) {
                        id = word.getValue();
                        break;
                    }
            if (id == null) id = fallback(material);
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
            if ((material == Material.GRAY_DYE
                            || (material == Material.BARRIER && !id.equals("navigation_fermer"))
                            || name.contains("non decouvert"))
                    && catalog.contains(id + "_grise")) id += "_grise";
            if (!catalog.contains(id)) continue;
            ItemStack icon = template(id);
            if (icon == null) continue;
            var decorated = item.withType(icon.getType());
            var target = decorated.getItemMeta();
            target.getPersistentDataContainer()
                    .set(
                            originalMaterial,
                            org.bukkit.persistence.PersistentDataType.STRING,
                            material.name());
            var appearance = icon.getItemMeta();
            target.setCustomModelData(
                    appearance.hasCustomModelData() ? appearance.getCustomModelData() : null);
            Object modelValue = appearance.serialize().get("item-model");
            if (modelValue != null)
                target.setItemModel(NamespacedKey.fromString(modelValue.toString()));
            else if (target.serialize().containsKey("item-model")) target.setItemModel(null);
            decorated.setItemMeta(target);
            if (!decorated.equals(item)) inventory.setItem(slot, decorated);
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
    }

    private ItemStack template(String id) {
        if (templates.containsKey(id)) return templates.get(id);
        long now = System.nanoTime();
        if (now < retryAfter.getOrDefault(id, Long.MIN_VALUE)) return null;
        ItemStack result = resolver.resolve("icons:" + id);
        if (result != null) {
            templates.put(id, result.clone());
            return templates.get(id);
        }
        retryAfter.put(id, now + 10_000_000_000L);
        return null;
    }

    private ItemStack itemsAdder(String id) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder")) return null;
        try {
            if (getInstance == null) {
                Class<?> api = Class.forName("dev.lone.itemsadder.api.CustomStack");
                getInstance = api.getMethod("getInstance", String.class);
                getItemStack = api.getMethod("getItemStack");
            }
            Object custom = getInstance.invoke(null, id);
            if (custom != null) return ((ItemStack) getItemStack.invoke(custom)).clone();
            if (warned.add(id))
                plugin.getLogger()
                        .warning(
                                "Icône ItemsAdder absente : " + id + " ; icône vanilla conservée.");
        } catch (ReflectiveOperationException | LinkageError error) {
            if (warned.add("api"))
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.WARNING,
                                "API ItemsAdder indisponible : icônes vanilla conservées.",
                                error);
        }
        return null;
    }

    public void invalidate() {
        templates.clear();
        retryAfter.clear();
        warned.clear();
    }

    @EventHandler
    public void enabled(org.bukkit.event.server.PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("ItemsAdder")) invalidate();
    }

    @EventHandler
    public void disabled(org.bukkit.event.server.PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("ItemsAdder")) invalidate();
    }

    public void shutdown() {
        Bukkit.getServicesManager().unregister(MenuIcons.class, this);
        if (task != null) task.cancel();
        task = null;
        views.clear();
        invalidate();
        HandlerList.unregisterAll(this);
    }
}
