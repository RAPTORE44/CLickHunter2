package fr.cliker.clikerv2.comfort;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Préférences persistantes et autorisations de transfert limitées à la connexion courante. */
public final class PlayerComfort implements Listener, CommandExecutor {
    private final JavaPlugin plugin;
    private final Set<UUID> dropsAllowed = new HashSet<>(), pickupAllowed = new HashSet<>();
    private final Map<String, Long> reminders = new HashMap<>();
    private final Map<UUID, Boolean> previousCollision = new HashMap<>();
    private final Set<UUID> pendingMenus = new HashSet<>();

    public PlayerComfort(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean enabled(Player player, String name, boolean fallback) {
        Byte value =
                player.getPersistentDataContainer()
                        .get(
                                new NamespacedKey("clikerv2", "preference_" + name),
                                PersistentDataType.BYTE);
        return value == null ? fallback : value != 0;
    }

    private void toggle(Player player, String name, boolean fallback) {
        boolean value = !enabled(player, name, fallback);
        player.getPersistentDataContainer()
                .set(
                        new NamespacedKey("clikerv2", "preference_" + name),
                        PersistentDataType.BYTE,
                        (byte) (value ? 1 : 0));
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getServicesManager()
                .register(
                        PlayerComfort.class,
                        this,
                        plugin,
                        org.bukkit.plugin.ServicePriority.Normal);
        for (String name : List.of("antidrop", "ramassage", "settings", "lock")) {
            var command = plugin.getCommand(name);
            if (command != null) command.setExecutor(this);
        }
        for (var player : Bukkit.getOnlinePlayers()) {
            initialize(player);
            for (var target : Bukkit.getOnlinePlayers()) visibility(player, target);
        }
    }

    public static boolean locked(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta()
                        .getPersistentDataContainer()
                        .has(
                                new NamespacedKey("clikerv2", "equipment_locked"),
                                PersistentDataType.BYTE);
    }

    private static boolean equipment(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var key = fr.cliker.clikerv2.combat.SwordCommand.SWORD_KEY;
        return fr.cliker.clikerv2.gear.TierGear.isGear(item)
                || fr.cliker.clikerv2.essence.CompressorItem.tierOf(item) != null
                || (key != null
                        && item.getItemMeta()
                                .getPersistentDataContainer()
                                .has(key, PersistentDataType.BYTE));
    }

    public static boolean protectedForSale(Player player, ItemStack item) {
        if (locked(item) || SessionConvenience.protectedMenu(item)) return true;
        PlayerComfort service = Bukkit.getServicesManager().load(PlayerComfort.class);
        return service != null
                && !service.dropsAllowed.contains(player.getUniqueId())
                && equipment(item);
    }

    private void visibility(Player viewer, Player target) {
        if (viewer.equals(target)) return;
        if (enabled(viewer, "hide_players", false)) viewer.hidePlayer(plugin, target);
        else viewer.showPlayer(plugin, target);
    }

    private void initialize(Player player) {
        if (plugin.getConfig().getBoolean("comfort.no-player-collisions", true)) {
            previousCollision.putIfAbsent(player.getUniqueId(), player.isCollidable());
            player.setCollidable(false);
        }
        if (plugin.getConfig().getBoolean("comfort.no-hunger", true)) player.setFoodLevel(20);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        initialize(event.getPlayer());
        for (var viewer : Bukkit.getOnlinePlayers()) {
            visibility(viewer, event.getPlayer());
            visibility(event.getPlayer(), viewer);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var id = player.getUniqueId();
        dropsAllowed.remove(id);
        pickupAllowed.remove(id);
        pendingMenus.remove(id);
        reminders.remove(id + ":drop");
        reminders.remove(id + ":pickup");
        Boolean collision = previousCollision.remove(id);
        if (collision != null) player.setCollidable(collision);
    }

    private void remind(Player player, String kind, String text) {
        String key = player.getUniqueId() + ":" + kind;
        long now = System.nanoTime();
        Long last = reminders.get(key);
        if (last != null && now - last < 3_000_000_000L) return;
        reminders.put(key, now);
        player.sendMessage(Component.text(text, NamedTextColor.YELLOW));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        if (locked(event.getItemDrop().getItemStack())
                || !dropsAllowed.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            remind(
                    event.getPlayer(),
                    "drop",
                    locked(event.getItemDrop().getItemStack())
                            ? "Cet équipement est verrouillé. /lock avec l’objet en main pour le"
                                    + " déverrouiller."
                            : "Jet d’objets protégé. /antidrop pour jeter jusqu’à ta"
                                    + " déconnexion.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && !pickupAllowed.contains(player.getUniqueId())) {
            event.setCancelled(true);
            remind(
                    player,
                    "pickup",
                    "Ramassage au sol protégé. /ramassage autoriser pour ramasser jusqu’à ta"
                        + " déconnexion. Les récompenses du plugin arrivent directement dans ton"
                        + " inventaire ou /claim.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player
                && plugin.getConfig().getBoolean("comfort.no-hunger", true)) {
            event.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player
                && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && plugin.getConfig().getBoolean("comfort.no-fall-damage", true))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void decoration(PlayerInteractEvent event) {
        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || !plugin.getConfig().getBoolean("comfort.protect-decoration", true)) return;
        String type = event.getClickedBlock().getType().name();
        if (type.endsWith("_TRAPDOOR")
                || type.endsWith("_FENCE_GATE")
                || type.equals("FLOWER_POT")
                || type.startsWith("POTTED_")) event.setUseInteractedBlock(Event.Result.DENY);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (!player.hasPermission("cliker.comfort.use")) {
            player.sendMessage(Component.text("Tu n’as pas la permission.", NamedTextColor.RED));
            return true;
        }
        if (command.getName().equals("lock")) {
            if (args.length != 0) {
                player.sendMessage("Usage : /lock, avec l’équipement en main.");
                return true;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!equipment(held)) {
                player.sendMessage(
                        "Tiens une épée, une armure ClickHunter ou un autocompresseur en main.");
                return true;
            }
            boolean wasLocked = locked(held);
            var meta = held.getItemMeta();
            var key = new NamespacedKey("clikerv2", "equipment_locked");
            if (wasLocked) meta.getPersistentDataContainer().remove(key);
            else meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            held.setItemMeta(meta);
            player.getInventory().setItemInMainHand(held);
            player.sendMessage(
                    Component.text(
                            wasLocked
                                    ? "Équipement déverrouillé."
                                    : "Équipement verrouillé : jet et vente à l’hôtel des ventes"
                                            + " bloqués. /lock pour déverrouiller.",
                            NamedTextColor.GREEN));
            return true;
        }
        if (command.getName().equals("settings")) {
            if (args.length != 0) {
                player.sendMessage("Usage : /settings");
                return true;
            }
            open(player);
            return true;
        }
        boolean antidrop = command.getName().equals("antidrop");
        var allowed = antidrop ? dropsAllowed : pickupAllowed;
        if (antidrop
                ? args.length != 0
                : args.length != 1
                        || !(args[0].equalsIgnoreCase("autoriser")
                                || args[0].equalsIgnoreCase("bloquer"))) {
            player.sendMessage(
                    Component.text(
                            antidrop
                                    ? "Usage : /antidrop pour inverser la protection."
                                    : "Usage : /ramassage <autoriser|bloquer>.",
                            NamedTextColor.YELLOW));
            return true;
        }
        boolean allow =
                antidrop
                        ? !allowed.contains(player.getUniqueId())
                        : args[0].equalsIgnoreCase("autoriser");
        if (allow) allowed.add(player.getUniqueId());
        else allowed.remove(player.getUniqueId());
        player.sendMessage(
                Component.text(
                        (command.getName().equals("antidrop")
                                        ? "Jet d’objets "
                                        : "Ramassage au sol ")
                                + (allow ? "autorisé jusqu’à ta déconnexion." : "bloqué."),
                        NamedTextColor.GREEN));
        return true;
    }

    private static final String[] OPTIONS = {
        "damage_texts",
        "compression_sound",
        "soft_visuals",
        "common_drop_messages",
        "farm_actionbar",
        "hide_players"
    };
    private static final String[] LABELS = {
        "Textes de dégâts",
        "Son de compression",
        "Effets doux et dispersion réduite",
        "Messages des drops communs",
        "Récapitulatif des gains en combat",
        "Masquer les autres joueurs"
    };
    private static final boolean[] DEFAULTS = {true, true, false, true, true, false};
    private static final int[] OPTION_SLOTS = {11, 13, 15, 17, 4, 6};

    private void open(Player player) {
        SettingsView view = new SettingsView(player.getUniqueId());
        view.inventory =
                Bukkit.createInventory(
                        view,
                        27,
                        Component.text("Paramètres ClickHunter", NamedTextColor.DARK_GREEN));
        for (int i = 0; i < OPTIONS.length; i++) {
            boolean active = enabled(player, OPTIONS[i], DEFAULTS[i]);
            var item = new ItemStack(active ? Material.LIME_DYE : Material.GRAY_DYE);
            var meta = item.getItemMeta();
            meta.displayName(Component.text(LABELS[i], NamedTextColor.AQUA));
            meta.lore(
                    List.of(
                            Component.text(
                                    active ? "Activé" : "Désactivé",
                                    active ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                            Component.text("Clic gauche pour changer.", NamedTextColor.YELLOW)));
            item.setItemMeta(meta);
            view.inventory.setItem(OPTION_SLOTS[i], item);
        }
        fr.cliker.clikerv2.ui.MenuNavigation.back(plugin, view.inventory, 22);
        player.openInventory(view.inventory);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SettingsView view)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !view.owner.equals(player.getUniqueId())
                || !player.hasPermission("cliker.comfort.use")
                || event.getClick() != ClickType.LEFT
                || event.getClickedInventory() != view.inventory) return;
        int index =
                switch (event.getRawSlot()) {
                    case 11 -> 0;
                    case 13 -> 1;
                    case 15 -> 2;
                    case 17 -> 3;
                    case 4 -> 4;
                    case 6 -> 5;
                    default -> -1;
                };
        if (index < 0 || !pendingMenus.add(player.getUniqueId())) return;
        toggle(player, OPTIONS[index], DEFAULTS[index]);
        if (index == 5) for (var target : Bukkit.getOnlinePlayers()) visibility(player, target);
        fr.cliker.clikerv2.runtime.ServerTasks.run(
                plugin,
                () -> {
                    pendingMenus.remove(player.getUniqueId());
                    if (player.isOnline()
                            && player.getOpenInventory().getTopInventory() == view.inventory)
                        open(player);
                });
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SettingsView)
            event.setCancelled(true);
    }

    public void shutdown() {
        for (var player : Bukkit.getOnlinePlayers()) {
            for (var target : Bukkit.getOnlinePlayers()) player.showPlayer(plugin, target);
            Boolean collision = previousCollision.get(player.getUniqueId());
            if (collision != null) player.setCollidable(collision);
        }
        Bukkit.getServicesManager().unregister(PlayerComfort.class, this);
        previousCollision.clear();
        dropsAllowed.clear();
        pickupAllowed.clear();
        reminders.clear();
        pendingMenus.clear();
        HandlerList.unregisterAll(this);
    }

    private static final class SettingsView implements InventoryHolder {
        final UUID owner;
        Inventory inventory;

        SettingsView(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
