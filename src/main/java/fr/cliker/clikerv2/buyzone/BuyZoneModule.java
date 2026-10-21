package fr.cliker.clikerv2.buyzone;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.economy.CurrencyType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/** Achat d'accès intégré à l'économie et aux permissions de ClikerV2. */
public final class BuyZoneModule implements Listener, CommandExecutor, TabCompleter {
    public static final String ADMIN = "cliker.buyzone.admin";
    public static final String BYPASS = "cliker.buyzone.bypass";
    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final BuyZoneStore store;
    private final File file;
    private final YamlConfiguration config;
    private final Map<String, BuyZone> gates = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> unlocked = new HashMap<>();
    private final Map<UUID, Object> sessions = new HashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Map<UUID, Offer> offers = new HashMap<>();
    private final Set<UUID> paying = new HashSet<>();
    private BukkitTask ticker;
    private volatile boolean stopped;

    public BuyZoneModule(JavaPlugin plugin, EconomyService economy, DatabaseManager database) {
        this.plugin = plugin;
        this.economy = economy;
        store = new BuyZoneStore(database);
        file = new File(plugin.getDataFolder(), "buyzone.yml");
        if (!file.exists()) plugin.saveResource("buyzone.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection catalog = config.getConfigurationSection("zones");
        if (catalog == null) throw new IllegalArgumentException("Catalogue des zones absent");
        for (String id : catalog.getKeys(false)) {
            String permission = config.getString("zones." + id + ".permission", "cliker.zone." + id);
            if (plugin.getServer().getPluginManager().getPermission(permission) == null) {
                plugin.getServer().getPluginManager().addPermission(new Permission(permission, PermissionDefault.FALSE));
            }
        }
        ConfigurationSection positions = config.getConfigurationSection("portails");
        if (positions != null) for (String id : positions.getKeys(false)) {
            Location location = config.getLocation("portails." + id);
            if (location == null || location.getWorld() == null) throw new IllegalArgumentException("Position ou monde de zone introuvable : " + id);
            gates.put(id, definition(id, location));
        }
    }
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("buyzone")).setExecutor(this);
        plugin.getCommand("buyzone").setTabCompleter(this);
        for (Player player : plugin.getServer().getOnlinePlayers()) load(player);
        // Couvre aussi les véhicules, réapparitions et joueurs déjà dans le volume au démarrage.
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) checkPresence(player);
        }, 20L, 10L);
    }
    private BuyZone definition(String id, Location location) {
        String path = "zones." + id;
        if (!config.isConfigurationSection(path)) throw new IllegalArgumentException("Zone inconnue : " + id);
        CurrencyType currency = CurrencyType.parse(config.getString(path + ".devise", "ecus"));
        String permission = config.getString(path + ".permission", "cliker.zone." + id);
        return new BuyZone(id, config.getString(path + ".nom", id), permission, currency, config.getLong(path + ".prix", -1),
                location, config.getDouble("dimensions.demi-largeur", 20), config.getDouble("dimensions.hauteur", 10), config.getDouble("dimensions.profondeur", 1));
    }
    public boolean hasAccess(Player player, BuyZone zone) {
        return player.hasPermission(BYPASS) || player.hasPermission(zone.permission())
                || unlocked.getOrDefault(player.getUniqueId(), Set.of()).contains(zone.id());
    }
    private void load(Player player) {
        UUID uuid = player.getUniqueId();
        Object token = new Object();
        sessions.put(uuid, token);
        unlocked.remove(uuid);
        store.load(uuid).whenComplete((owned, error) -> sync(() -> {
            if (sessions.get(uuid) != token || !player.isOnline()) return;
            if (error != null) { failure(player, error); return; }
            unlocked.put(uuid, new HashSet<>(owned));
            for (String id : owned) grant(player, id);
            checkPresence(player);
        }));
    }
    private void grant(Player player, String id) {
        String node = config.getString("zones." + id + ".permission");
        if (node == null) return;
        PermissionAttachment attachment = attachments.computeIfAbsent(player.getUniqueId(), uuid -> player.addAttachment(plugin));
        attachment.setPermission(node, true);
    }
    @EventHandler public void join(PlayerJoinEvent event) { load(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid); unlocked.remove(uuid); offers.remove(uuid);
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment != null) event.getPlayer().removeAttachment(attachment);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        if (event.getFrom().getX() == event.getTo().getX() && event.getFrom().getY() == event.getTo().getY() && event.getFrom().getZ() == event.getTo().getZ()) return;
        for (BuyZone zone : gates.values()) {
            if (!hasAccess(event.getPlayer(), zone) && zone.crosses(event.getFrom(), event.getTo())) {
                event.setCancelled(true);
                prompt(event.getPlayer(), zone, event.getFrom());
                return;
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        for (BuyZone zone : gates.values()) {
            if (!hasAccess(event.getPlayer(), zone) && zone.contains(event.getTo())) {
                event.setCancelled(true);
                prompt(event.getPlayer(), zone, event.getFrom());
                return;
            }
        }
    }
    private void checkPresence(Player player) {
        for (BuyZone zone : gates.values()) {
            if (!hasAccess(player, zone) && zone.contains(player.getLocation())) {
                if (player.isInsideVehicle()) player.leaveVehicle();
                retreat(player, zone, null);
                prompt(player, zone, null);
                return;
            }
        }
    }
    @EventHandler public void vehicle(VehicleMoveEvent event) {
        for (var passenger : new ArrayList<>(event.getVehicle().getPassengers())) {
            if (!(passenger instanceof Player player)) continue;
            for (BuyZone zone : gates.values()) {
                if (!hasAccess(player, zone) && zone.crosses(event.getFrom(), event.getTo())) {
                    player.leaveVehicle();
                    retreat(player, zone, event.getFrom());
                    prompt(player, zone, event.getFrom());
                    break;
                }
            }
        }
    }
    private void prompt(Player player, BuyZone zone, Location previous) {
        UUID uuid = player.getUniqueId();
        if (!unlocked.containsKey(uuid) || paying.contains(uuid) || offers.containsKey(uuid)) return;
        Offer offer = new Offer(uuid, zone, previous == null ? null : previous.clone());
        offers.put(uuid, offer);
        sync(() -> {
            if (!player.isOnline() || offers.get(uuid) != offer || hasAccess(player, zone)) { offers.remove(uuid, offer); return; }
            Inventory inventory = Bukkit.createInventory(offer, InventoryType.DISPENSER, Component.text("Acheter : " + zone.name(), NamedTextColor.GOLD));
            offer.inventory = inventory;
            ItemStack pane = icon(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
            for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, pane);
            List<String> lore = new ArrayList<>();
            lore.add("&7Zone : &f" + zone.name());
            lore.add(zone.price() < 0 ? "&cPrix non configuré" : "&7Prix : &e" + zone.currency().format(zone.price()) + " " + zone.currency().displayName());
            lore.add(""); lore.add(zone.price() < 0 ? "&7Contacte un administrateur." : "&aClique pour acheter !");
            inventory.setItem(4, icon(Material.NETHER_STAR, "&e" + zone.name(), lore));
            player.openInventory(inventory);
        });
    }
    private static ItemStack icon(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(fr.cliker.clikerv2.daily.DailySettings.color(title).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> fr.cliker.clikerv2.daily.DailySettings.color(line).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Offer offer)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !offer.owner.equals(player.getUniqueId())) return;
        if (event.getRawSlot() != 4 || (!event.isLeftClick() && !event.isRightClick())) return;
        if (gates.get(offer.zone.id()) != offer.zone) {
            offer.completed = true;
            player.closeInventory();
            player.sendMessage(Component.text("Ce portail n'existe plus.", NamedTextColor.RED));
            return;
        }
        if (hasAccess(player, offer.zone)) { offer.completed = true; player.closeInventory(); return; }
        if (offer.zone.price() < 0) { player.sendMessage(Component.text("Le prix de cette zone n'est pas encore configuré.", NamedTextColor.RED)); return; }
        UUID uuid = player.getUniqueId();
        if (!paying.add(uuid)) return;
        economy.purchaseZone(uuid, player.getName(), offer.zone.id(), offer.zone.permission(), offer.zone.currency(), offer.zone.price())
                .whenComplete((result, error) -> sync(() -> {
                    paying.remove(uuid);
                    Player online = plugin.getServer().getPlayer(uuid);
                    if (error != null) {
                        if (online != null) { failure(online, error); closeIfOpen(online, offer); }
                        else plugin.getLogger().log(Level.SEVERE, "Achat de zone impossible pour " + uuid, error);
                        return;
                    }
                    if (result == EconomyService.ZonePurchase.INSUFFICIENT_FUNDS) {
                        if (online != null) {
                            closeIfOpen(online, offer);
                            online.sendMessage(Component.text("Tu n'as pas assez de " + offer.zone.currency().displayName() + " pour acheter cette zone.", NamedTextColor.RED));
                        }
                        return;
                    }
                    if (online == null) return; // Le droit sera rechargé depuis SQLite à la prochaine connexion.
                    unlocked.computeIfAbsent(uuid, key -> new HashSet<>()).add(offer.zone.id());
                    grant(online, offer.zone.id());
                    Inventory top = online.getOpenInventory().getTopInventory();
                    boolean stillOpen = top != null && top.getHolder() == offer;
                    offer.completed = true;
                    offers.remove(uuid, offer);
                    closeIfOpen(online, offer);
                    online.sendMessage(Component.text("Zone " + offer.zone.name() + " débloquée !", NamedTextColor.GREEN));
                    if (stillOpen) online.teleport(offer.zone.center());
                }));
    }
    private void closeIfOpen(Player player, Offer offer) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top != null && top.getHolder() == offer) player.closeInventory();
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Offer) event.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Offer offer)) return;
        offers.remove(offer.owner, offer);
        if (event.getPlayer() instanceof Player player) sync(() -> {
            if (player.isOnline() && !offer.completed && !hasAccess(player, offer.zone)) retreat(player, offer.zone, offer.previous);
        });
    }
    private boolean safe(Player player, Location at) {
        if (at == null || at.getWorld() == null) return false;
        for (BuyZone zone : gates.values()) if (!hasAccess(player, zone) && zone.contains(at)) return false;
        var feet = at.getBlock();
        var floor = feet.getRelative(0, -1, 0);
        return feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && floor.getType().isSolid()
                && floor.getType() != Material.MAGMA_BLOCK && floor.getType() != Material.CACTUS
                && !feet.isLiquid() && feet.getType() != Material.FIRE && feet.getType() != Material.SOUL_FIRE
                && floor.getType() != Material.CAMPFIRE && floor.getType() != Material.SOUL_CAMPFIRE;
    }
    private Location northSafe(Player player, BuyZone zone) {
        Location target = zone.northReturn();
        for (int step = 0; step <= 12; step++) {
            int offset = step == 0 ? 0 : (step + 1) / 2 * (step % 2 == 1 ? 1 : -1);
            Location candidate = target.clone().add(0, offset, 0);
            if (safe(player, candidate)) return candidate;
        }
        return null;
    }
    private void retreat(Player player, BuyZone zone, Location previous) {
        Location target = northSafe(player, zone);
        if (target == null && safe(player, previous)) target = previous;
        if (target == null && safe(player, player.getWorld().getSpawnLocation())) target = player.getWorld().getSpawnLocation();
        if (target != null) player.teleport(target);
        else plugin.getLogger().warning("Aucun retour sûr pour " + player.getName() + " à la zone " + zone.id() + ". Vérifier le sol au nord du portail.");
    }
    private void failure(Player player, Throwable error) {
        plugin.getLogger().log(Level.SEVERE, "Erreur buyzone pour " + player.getUniqueId(), error);
        player.sendMessage(Component.text("L'achat n'a pas pu être traité. Contacte un administrateur.", NamedTextColor.RED));
    }
    private void sync(Runnable task) { if (!stopped && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task); }
    private void saveConfig() throws java.io.IOException { config.save(file); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) { sender.sendMessage(Component.text("Permission administrateur requise.", NamedTextColor.RED)); return true; }
        if (args.length >= 2) {
            String id = String.join("_", Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT).replace(' ', '_');
            if (args[0].equalsIgnoreCase("remove")) {
                BuyZone previous = gates.remove(id);
                if (previous == null) { sender.sendMessage(Component.text("Aucun portail pour cette zone.", NamedTextColor.RED)); return true; }
                config.set("portails." + id, null);
                try { saveConfig(); }
                catch (Exception e) { gates.put(id, previous); config.set("portails." + id, previous.center()); sender.sendMessage(Component.text("Échec de la sauvegarde.", NamedTextColor.RED)); return true; }
                sender.sendMessage(Component.text("Portail " + id + " supprimé. Les achats sont conservés.", NamedTextColor.GREEN));
                return true;
            }
            if (args[0].equalsIgnoreCase("spawn") && sender instanceof Player player) {
                try {
                    if (gates.containsKey(id)) { player.sendMessage(Component.text("Cette zone a déjà un portail. Utilise /buyzone remove " + id + " avant de le déplacer.", NamedTextColor.RED)); return true; }
                    BuyZone zone = definition(id, player.getLocation());
                    if (zone.price() < 0) { player.sendMessage(Component.text("Le prix de " + zone.name() + " manque dans buyzone.yml (zones." + id + ".prix). Configure-le puis redémarre.", NamedTextColor.RED)); return true; }
                    if (northSafe(player, zone) == null) { player.sendMessage(Component.text("Prévois un sol sûr à 5 blocs au nord du portail pour le retour des joueurs.", NamedTextColor.RED)); return true; }
                    gates.put(id, zone); config.set("portails." + id, zone.center());
                    try { saveConfig(); } catch (Exception e) { gates.remove(id); config.set("portails." + id, null); throw e; }
                    player.sendMessage(Component.text("Zone invisible " + zone.name() + " créée ici.", NamedTextColor.GREEN));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Création buyzone impossible : " + id, e);
                    player.sendMessage(Component.text(String.valueOf(e.getMessage()), NamedTextColor.RED));
                }
                return true;
            }
        }
        sender.sendMessage(Component.text("/buyzone spawn <zone> | /buyzone remove <zone>", NamedTextColor.YELLOW));
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN)) return List.of();
        Set<String> options = args.length == 1 ? Set.of("spawn", "remove") : args.length == 2 ? config.getConfigurationSection("zones").getKeys(false) : Set.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).sorted().toList();
    }
    public void shutdown() {
        stopped = true;
        HandlerList.unregisterAll(this);
        if (ticker != null) ticker.cancel();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof Offer) player.closeInventory();
        }
        for (PermissionAttachment attachment : attachments.values()) attachment.remove();
        attachments.clear(); offers.clear(); sessions.clear(); unlocked.clear();
    }
    private static final class Offer implements InventoryHolder {
        private final UUID owner;
        private final BuyZone zone;
        private final Location previous;
        private Inventory inventory;
        private boolean completed;
        private Offer(UUID owner, BuyZone zone, Location previous) { this.owner = owner; this.zone = zone; this.previous = previous; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
