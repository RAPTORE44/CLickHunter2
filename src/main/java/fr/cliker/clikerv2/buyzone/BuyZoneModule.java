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
    private final Map<UUID, Map<String, Boolean>> decisions = new HashMap<>();
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
        file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "buyzone.yml");
        if (!file.exists()) fr.cliker.clikerv2.config.ConfigFiles.install(plugin, "buyzone.yml");
        config = readConfig(file);
        migratePrices();
        ConfigurationSection catalog = config.getConfigurationSection("zones");
        if (catalog == null) throw new IllegalArgumentException("Catalogue des zones absent");
        for (String id : catalog.getKeys(false)) {
            String permission =
                    config.getString("zones." + id + ".permission", "cliker.zone." + id);
            if (plugin.getServer().getPluginManager().getPermission(permission) == null) {
                plugin.getServer()
                        .getPluginManager()
                        .addPermission(new Permission(permission, PermissionDefault.FALSE));
            }
        }
        restorePortals();
    }

    private static YamlConfiguration readConfig(File file) {
        return fr.cliker.clikerv2.config.YamlFiles.load(file);
    }

    private Location readPosition(String id) {
        return fr.cliker.clikerv2.config.YamlFiles.location(config, "portails." + id);
    }

    private void savePosition(String id, Location at) {
        fr.cliker.clikerv2.config.YamlFiles.location(config, "portails." + id, at);
    }

    private void restorePortals() {
        var positions = config.getConfigurationSection("portails");
        if (positions == null) return;
        for (String id : positions.getKeys(false)) {
            if (gates.containsKey(id)) continue;
            try {
                Location at = readPosition(id);
                if (at != null) gates.put(id, definition(id, at));
                else
                    plugin.getLogger()
                            .warning(
                                    "Portail "
                                            + id
                                            + " en attente de son monde : /buyzone info "
                                            + id);
            } catch (IllegalArgumentException error) {
                plugin.getLogger().warning("Portail " + id + " ignoré : " + error.getMessage());
            }
        }
    }

    @EventHandler
    public void worldLoaded(org.bukkit.event.world.WorldLoadEvent event) {
        restorePortals();
    }

    private void migratePrices() {
        if (config.getInt("version-prix", 1) >= 2) return;
        try {
            var backup =
                    file.toPath()
                            .getParent()
                            .resolve("backups/buyzone-" + System.currentTimeMillis() + ".yml");
            java.nio.file.Files.createDirectories(backup.getParent());
            java.nio.file.Files.copy(file.toPath(), backup);
            try (var input = plugin.getResource("buyzone.yml")) {
                var defaults =
                        YamlConfiguration.loadConfiguration(
                                new java.io.InputStreamReader(
                                        Objects.requireNonNull(input),
                                        java.nio.charset.StandardCharsets.UTF_8));
                for (String id : defaults.getConfigurationSection("zones").getKeys(false)) {
                    String key = "zones." + id + ".prix";
                    // Conserver les prix déjà personnalisés et les entrées de zones
                    // supplémentaires.
                    if (config.isConfigurationSection("zones." + id)
                            && (!config.contains(key) || config.getLong(key) == -1)) {
                        config.set(key, defaults.getLong(key));
                    }
                }
            }
            config.set("version-prix", 2);
            saveConfig();
            plugin.getLogger()
                    .info(
                            "Buyzone : tarifs provisoires appliqués ; ancienne configuration"
                                    + " sauvegardée dans "
                                    + backup.getFileName());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Migration des tarifs impossible", e);
        }
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("buyzone")).setExecutor(this);
        plugin.getCommand("buyzone").setTabCompleter(this);
        for (Player player : plugin.getServer().getOnlinePlayers()) load(player);
        // Couvre aussi les véhicules, réapparitions et joueurs déjà dans le volume au démarrage.
        ticker =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    for (Player player : plugin.getServer().getOnlinePlayers())
                                        checkPresence(player);
                                },
                                20L,
                                10L);
    }

    private BuyZone definition(String id, Location location) {
        String path = "zones." + id;
        if (!config.isConfigurationSection(path))
            throw new IllegalArgumentException("Zone inconnue : " + id);
        CurrencyType currency = CurrencyType.parse(config.getString(path + ".devise", "ecus"));
        String permission = config.getString(path + ".permission", "cliker.zone." + id);
        return new BuyZone(
                id,
                config.getString(path + ".nom", id),
                permission,
                currency,
                config.getLong(path + ".prix", -1),
                location,
                config.getDouble("dimensions.demi-largeur", 20),
                config.getDouble("dimensions.hauteur", 10),
                config.getDouble("dimensions.profondeur", 1));
    }

    public boolean hasAccess(Player player, BuyZone zone) {
        return effectiveAccess(player, zone.id(), zone.permission());
    }

    private boolean effectiveAccess(Player player, String id, String permission) {
        if (id.equals("vache")
                || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.hasPermission(BYPASS)) return true;
        if (!unlocked.containsKey(player.getUniqueId())) return false;
        Boolean decision = decisions.getOrDefault(player.getUniqueId(), Map.of()).get(id);
        if (decision != null) return decision;
        return player.hasPermission(permission) || unlocked.get(player.getUniqueId()).contains(id);
    }

    public boolean canTravel(Player player, String id) {
        return config.contains("zones." + id)
                && effectiveAccess(
                        player,
                        id,
                        config.getString("zones." + id + ".permission", "cliker.zone." + id));
    }

    /** Les téléportations sociales ne doivent pas sauter un portail d’achat. */
    public boolean canSocialTravel(Player player, Location destination) {
        if (destination == null || destination.getWorld() == null) return false;
        for (BuyZone zone : gates.values()) {
            if (hasAccess(player, zone)) continue;
            Location center = zone.center();
            if (!center.getWorld().equals(destination.getWorld())) continue;
            if (zone.contains(destination)
                    || zone.crosses(player.getLocation(), destination)
                    || (destination.getZ() >= center.getZ() - zone.depth() / 2
                            && Math.abs(destination.getX() - center.getX()) <= zone.halfWidth()))
                return false;
        }
        return true;
    }

    private void load(Player player) {
        UUID uuid = player.getUniqueId();
        Object token = new Object();
        sessions.put(uuid, token);
        store.state(uuid)
                .whenComplete(
                        (owned, error) ->
                                sync(
                                        () -> {
                                            if (sessions.get(uuid) != token || !player.isOnline())
                                                return;
                                            if (error != null) {
                                                failure(player, error);
                                                return;
                                            }
                                            var previous = attachments.remove(uuid);
                                            if (previous != null) player.removeAttachment(previous);
                                            unlocked.put(uuid, new HashSet<>(owned.owned()));
                                            decisions.put(uuid, new HashMap<>(owned.overrides()));
                                            for (String id : owned.owned()) grant(player, id);
                                            for (var decision : owned.overrides().entrySet())
                                                if (decision.getValue())
                                                    grant(player, decision.getKey());
                                            var offer = offers.get(uuid);
                                            if (offer != null && hasAccess(player, offer.zone)) {
                                                offer.completed = true;
                                                offers.remove(uuid, offer);
                                                closeIfOpen(player, offer);
                                            }
                                            checkPresence(player);
                                        }));
    }

    private void grant(Player player, String id) {
        String node = config.getString("zones." + id + ".permission", "cliker.zone." + id);
        if (node == null) return;
        PermissionAttachment attachment =
                attachments.computeIfAbsent(
                        player.getUniqueId(), uuid -> player.addAttachment(plugin));
        attachment.setPermission(node, true);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        unlocked.remove(uuid);
        decisions.remove(uuid);
        offers.remove(uuid);
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment != null) event.getPlayer().removeAttachment(attachment);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        if (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) return;
        for (BuyZone zone : gates.values()) {
            if (!hasAccess(event.getPlayer(), zone)
                    && zone.crosses(event.getFrom(), event.getTo())) {
                event.setCancelled(true);
                prompt(event.getPlayer(), zone, event.getFrom());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        for (BuyZone zone : gates.values()) {
            boolean crossingToLockedSide =
                    event.getTo() != null
                            && event.getTo().getZ() >= zone.center().getZ()
                            && zone.crosses(event.getFrom(), event.getTo());
            if (!hasAccess(event.getPlayer(), zone)
                    && (zone.contains(event.getTo()) || crossingToLockedSide)) {
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

    @EventHandler
    public void vehicle(VehicleMoveEvent event) {
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
        if (!unlocked.containsKey(uuid) || paying.contains(uuid) || offers.containsKey(uuid))
            return;
        Offer offer = new Offer(uuid, zone, previous == null ? null : previous.clone());
        offers.put(uuid, offer);
        sync(
                () -> {
                    if (!player.isOnline()
                            || offers.get(uuid) != offer
                            || hasAccess(player, zone)) {
                        offers.remove(uuid, offer);
                        return;
                    }
                    Inventory inventory =
                            Bukkit.createInventory(
                                    offer,
                                    InventoryType.DISPENSER,
                                    Component.text(
                                            "Acheter : " + zone.name(), NamedTextColor.GOLD));
                    offer.inventory = inventory;
                    ItemStack pane = icon(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
                    for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, pane);
                    List<String> lore = new ArrayList<>();
                    lore.add("&7Zone : &f" + zone.name());
                    lore.add(
                            zone.price() < 0
                                    ? "&cPrix non configuré"
                                    : "&7Prix : &e"
                                            + zone.currency().format(zone.price())
                                            + " "
                                            + zone.currency().displayName());
                    lore.add("");
                    lore.add(
                            zone.price() < 0
                                    ? "&7Contacte un administrateur."
                                    : "&aClique pour acheter !");
                    inventory.setItem(4, icon(Material.NETHER_STAR, "&e" + zone.name(), lore));
                    player.openInventory(inventory);
                });
    }

    private static ItemStack icon(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(
                fr.cliker.clikerv2.daily.DailySettings.color(title)
                        .decoration(TextDecoration.ITALIC, false));
        meta.lore(
                lore.stream()
                        .map(
                                line ->
                                        fr.cliker.clikerv2.daily.DailySettings.color(line)
                                                .decoration(TextDecoration.ITALIC, false))
                        .toList());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Offer offer)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !offer.owner.equals(player.getUniqueId())) return;
        if (event.getRawSlot() != 4 || (!event.isLeftClick() && !event.isRightClick())) return;
        if (gates.get(offer.zone.id()) != offer.zone) {
            offer.completed = true;
            player.closeInventory();
            player.sendMessage(Component.text("Ce portail n'existe plus.", NamedTextColor.RED));
            return;
        }
        if (hasAccess(player, offer.zone)) {
            offer.completed = true;
            player.closeInventory();
            return;
        }
        if (offer.zone.price() < 0) {
            player.sendMessage(
                    Component.text(
                            "Le prix de cette zone n'est pas encore configuré.",
                            NamedTextColor.RED));
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!paying.add(uuid)) return;
        offer.inventory.setItem(
                4,
                icon(
                        Material.CLOCK,
                        "&eAchat en cours…",
                        List.of("&7Vérification de ton solde.", "&7Patiente un instant.")));
        economy.purchaseZone(
                        uuid,
                        player.getName(),
                        offer.zone.id(),
                        offer.zone.permission(),
                        offer.zone.currency(),
                        offer.zone.price())
                .whenComplete(
                        (result, error) ->
                                sync(
                                        () -> {
                                            paying.remove(uuid);
                                            Player online = plugin.getServer().getPlayer(uuid);
                                            if (error != null) {
                                                if (online != null) {
                                                    failure(online, error);
                                                    closeIfOpen(online, offer);
                                                } else
                                                    plugin.getLogger()
                                                            .log(
                                                                    Level.SEVERE,
                                                                    "Achat de zone impossible pour "
                                                                            + uuid,
                                                                    error);
                                                return;
                                            }
                                            if (result
                                                    == EconomyService.ZonePurchase
                                                            .INSUFFICIENT_FUNDS) {
                                                if (online != null) {
                                                    closeIfOpen(online, offer);
                                                    online.sendMessage(
                                                            Component.text(
                                                                    "Tu n'as pas assez de "
                                                                            + offer.zone
                                                                                    .currency()
                                                                                    .displayName()
                                                                            + " pour acheter cette"
                                                                            + " zone.",
                                                                    NamedTextColor.RED));
                                                }
                                                return;
                                            }
                                            if (online == null)
                                                return; // Le droit sera rechargé depuis SQLite à la
                                            // prochaine connexion.
                                            unlocked.computeIfAbsent(uuid, key -> new HashSet<>())
                                                    .add(offer.zone.id());
                                            decisions
                                                    .computeIfAbsent(uuid, key -> new HashMap<>())
                                                    .remove(offer.zone.id());
                                            grant(online, offer.zone.id());
                                            load(online);
                                            Inventory top =
                                                    online.getOpenInventory().getTopInventory();
                                            boolean stillOpen =
                                                    top != null && top.getHolder() == offer;
                                            offer.completed = true;
                                            offers.remove(uuid, offer);
                                            closeIfOpen(online, offer);
                                            online.sendMessage(
                                                    Component.text(
                                                            "Zone "
                                                                    + offer.zone.name()
                                                                    + " débloquée !",
                                                            NamedTextColor.GREEN));
                                            if (stillOpen) online.teleport(offer.zone.center());
                                        }));
    }

    private void closeIfOpen(Player player, Offer offer) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top != null && top.getHolder() == offer) player.closeInventory();
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Offer)
            event.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Offer offer)) return;
        offers.remove(offer.owner, offer);
        if (event.getPlayer() instanceof Player player)
            sync(
                    () -> {
                        if (player.isOnline() && !offer.completed && !hasAccess(player, offer.zone))
                            retreat(player, offer.zone, offer.previous);
                    });
    }

    private boolean safe(Player player, Location at) {
        if (at == null || at.getWorld() == null) return false;
        for (BuyZone zone : gates.values())
            if (!hasAccess(player, zone) && zone.contains(at)) return false;
        var feet = at.getBlock();
        var floor = feet.getRelative(0, -1, 0);
        return feet.isPassable()
                && feet.getRelative(0, 1, 0).isPassable()
                && floor.getType().isSolid()
                && floor.getType() != Material.MAGMA_BLOCK
                && floor.getType() != Material.CACTUS
                && !feet.isLiquid()
                && feet.getType() != Material.FIRE
                && feet.getType() != Material.SOUL_FIRE
                && floor.getType() != Material.CAMPFIRE
                && floor.getType() != Material.SOUL_CAMPFIRE;
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
        if (target == null && safe(player, player.getWorld().getSpawnLocation()))
            target = player.getWorld().getSpawnLocation();
        if (target != null) {
            target = target.clone();
            target.setYaw(0);
            target.setPitch(0);
            player.teleport(target);
        } else
            plugin.getLogger()
                    .warning(
                            "Aucun retour sûr pour "
                                    + player.getName()
                                    + " à la zone "
                                    + zone.id()
                                    + ". Vérifier le sol au nord du portail.");
    }

    private void failure(Player player, Throwable error) {
        plugin.getLogger().log(Level.SEVERE, "Erreur buyzone pour " + player.getUniqueId(), error);
        player.sendMessage(
                Component.text(
                        "L'achat n'a pas pu être traité. Contacte un administrateur.",
                        NamedTextColor.RED));
    }

    private void sync(Runnable task) {
        if (!stopped && plugin.isEnabled())
            fr.cliker.clikerv2.runtime.ServerTasks.run(plugin, task);
    }

    private void saveConfig() throws java.io.IOException {
        config.save(file);
    }

    private java.util.concurrent.CompletableFuture<UUID> resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null)
            return java.util.concurrent.CompletableFuture.completedFuture(online.getUniqueId());
        var cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.hasPlayedBefore())
            return java.util.concurrent.CompletableFuture.completedFuture(cached.getUniqueId());
        return store.resolve(name);
    }

    private java.util.concurrent.CompletableFuture<Map<String, Boolean>> permissionSnapshot(
            UUID owner, Map<String, String> nodes) {
        Player player = Bukkit.getPlayer(owner);
        if (player != null) {
            var permissions = new HashMap<String, Boolean>();
            nodes.forEach((id, node) -> permissions.put(id, player.hasPermission(node)));
            return java.util.concurrent.CompletableFuture.completedFuture(permissions);
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms"))
            return java.util.concurrent.CompletableFuture.completedFuture(Map.of());
        return fr.cliker.clikerv2.integration.LuckPermsAccess.offline(owner, nodes);
    }

    private boolean adminAvailable(CommandSender sender) {
        return !stopped
                && sender.hasPermission(ADMIN)
                && (!(sender instanceof Player player)
                        || fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(player));
    }

    private void playerZones(CommandSender sender, String name, String zone, Boolean allow) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            sender.sendMessage("§cIndique un pseudo Minecraft valide.");
            return;
        }
        if (zone != null && !config.isConfigurationSection("zones." + zone)) {
            sender.sendMessage("§cZone inconnue. /buyzone liste");
            return;
        }
        if ("vache".equals(zone) && Boolean.FALSE.equals(allow)) {
            sender.sendMessage(
                    "§eLa zone Vache est la zone de départ : son accès reste gratuit pour tous.");
            return;
        }
        resolveTarget(name)
                .whenComplete(
                        (owner, error) ->
                                sync(
                                        () -> {
                                            if (!adminAvailable(sender)) return;
                                            if (error != null) {
                                                adminError(sender, error);
                                                return;
                                            }
                                            if (owner == null) {
                                                sender.sendMessage(
                                                        "§cJoueur inconnu : il doit avoir déjà"
                                                                + " rejoint le serveur.");
                                                return;
                                            }
                                            if (allow != null)
                                                updateAccess(sender, name, owner, zone, allow);
                                            else inspectAccess(sender, name, owner);
                                        }));
    }

    private void updateAccess(
            CommandSender sender, String name, UUID owner, String zone, boolean allow) {
        String permission =
                config.getString("zones." + zone + ".permission", "cliker.zone." + zone);
        store.setAccess(owner, zone, permission, allow)
                .whenComplete(
                        (ignored, error) ->
                                sync(
                                        () -> {
                                            if (stopped) return;
                                            if (error != null) {
                                                if (adminAvailable(sender))
                                                    adminError(sender, error);
                                                return;
                                            }
                                            Player online = Bukkit.getPlayer(owner);
                                            if (online != null) {
                                                decisions
                                                        .computeIfAbsent(
                                                                owner, key -> new HashMap<>())
                                                        .put(zone, allow);
                                                var owned =
                                                        unlocked.computeIfAbsent(
                                                                owner, key -> new HashSet<>());
                                                if (allow) owned.add(zone);
                                                else owned.remove(zone);
                                                var attachment =
                                                        attachments.computeIfAbsent(
                                                                owner,
                                                                key ->
                                                                        online.addAttachment(
                                                                                plugin));
                                                if (allow)
                                                    attachment.setPermission(permission, true);
                                                else attachment.unsetPermission(permission);
                                                load(online);
                                            }
                                            if (adminAvailable(sender))
                                                sender.sendMessage(
                                                        (allow
                                                                        ? "§aAccès accordé : "
                                                                        : "§eAccès retiré : ")
                                                                + name
                                                                + " → "
                                                                + zone
                                                                + ". §7Aucun prélèvement ni"
                                                                + " remboursement.");
                                        }));
    }

    private record Inspection(BuyZoneStore.Access access, Map<String, Boolean> permissions) {}

    private void inspectAccess(CommandSender sender, String name, UUID owner) {
        var nodes = new LinkedHashMap<String, String>();
        for (String id : config.getConfigurationSection("zones").getKeys(false))
            nodes.put(id, config.getString("zones." + id + ".permission", "cliker.zone." + id));
        store.state(owner)
                .thenCombine(permissionSnapshot(owner, nodes), Inspection::new)
                .whenComplete(
                        (snapshot, error) ->
                                sync(
                                        () -> {
                                            if (!adminAvailable(sender)) return;
                                            if (error != null) {
                                                adminError(sender, error);
                                                return;
                                            }
                                            sender.sendMessage(
                                                    "§6Zones de §e" + name + " §7(" + owner + ")");
                                            var access = snapshot.access();
                                            for (String id : nodes.keySet()) {
                                                Boolean decision = access.overrides().get(id);
                                                boolean granted =
                                                        id.equals("vache")
                                                                || (decision != null
                                                                        ? decision
                                                                        : access.owned()
                                                                                        .contains(
                                                                                                id)
                                                                                || snapshot.permissions()
                                                                                        .getOrDefault(
                                                                                                id,
                                                                                                false));
                                                String source =
                                                        id.equals("vache")
                                                                ? "départ"
                                                                : decision != null
                                                                        ? (decision
                                                                                ? "accord"
                                                                                      + " administrateur"
                                                                                : "retrait"
                                                                                      + " administrateur")
                                                                        : access.owned()
                                                                                        .contains(
                                                                                                id)
                                                                                ? "accès sauvegardé"
                                                                                : snapshot.permissions()
                                                                                                .getOrDefault(
                                                                                                        id,
                                                                                                        false)
                                                                                        ? "permission"
                                                                                        : "verrouillée";
                                                sender.sendMessage(
                                                        (granted ? "§a✔ " : "§c✘ ")
                                                                + id
                                                                + " §7— "
                                                                + source
                                                                + (gates.containsKey(id)
                                                                        ? " §8| §aPortail placé"
                                                                        : " §8| §7Sans portail"
                                                                                + " actif"));
                                            }
                                            Player target = Bukkit.getPlayer(owner);
                                            if (target == null)
                                                sender.sendMessage(
                                                        "§7Hors ligne : les permissions LuckPerms"
                                                                + " dépendant d’un monde/contexte"
                                                                + " devront être confirmées après"
                                                                + " connexion.");
                                            else if (target.getGameMode() == GameMode.CREATIVE
                                                    || target.hasPermission(BYPASS))
                                                sender.sendMessage(
                                                        "§eContournement temporaire actif pour ce"
                                                            + " joueur (créatif ou bypass), en plus"
                                                            + " des droits listés.");
                                        }));
    }

    private void adminError(CommandSender sender, Throwable error) {
        plugin.getLogger().log(Level.SEVERE, "Administration des accès BuyZone impossible", error);
        sender.sendMessage("§cOpération impossible. Consulte les journaux.");
    }

    private void placedPortals(CommandSender sender) {
        var placed = config.getConfigurationSection("portails");
        if (placed == null || placed.getKeys(false).isEmpty()) {
            sender.sendMessage("§7Aucun portail placé.");
            return;
        }
        sender.sendMessage("§6Portails enregistrés :");
        for (String id : placed.getKeys(false)) {
            String path = "portails." + id;
            sender.sendMessage(
                    "§e"
                            + id
                            + " §7— "
                            + config.getString(path + ".world", "monde inconnu")
                            + " §f"
                            + config.get(path + ".x")
                            + ", "
                            + config.get(path + ".y")
                            + ", "
                            + config.get(path + ".z")
                            + (gates.containsKey(id)
                                    ? " §a(actif)"
                                    : " §c(monde non chargé ou position invalide)"));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage(
                    Component.text("Permission administrateur requise.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("portails")) {
            placedPortals(sender);
            return true;
        }
        if (args.length == 2
                && (args[0].equalsIgnoreCase("joueur") || args[0].equalsIgnoreCase("player"))) {
            playerZones(sender, args[1], null, null);
            return true;
        }
        if (args.length >= 3
                && Set.of("debloquer", "unlock", "retirer", "revoke")
                        .contains(args[0].toLowerCase(Locale.ROOT))) {
            String id =
                    java.text.Normalizer.normalize(
                                    String.join("_", Arrays.copyOfRange(args, 2, args.length))
                                            .toLowerCase(Locale.ROOT),
                                    java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "");
            playerZones(
                    sender,
                    args[1],
                    id,
                    args[0].equalsIgnoreCase("debloquer") || args[0].equalsIgnoreCase("unlock"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("liste")) {
            sender.sendMessage(
                    Component.text(
                            "Zones disponibles : "
                                    + String.join(
                                            ", ",
                                            config.getConfigurationSection("zones").getKeys(false)),
                            NamedTextColor.YELLOW));
            sender.sendMessage(
                    Component.text(
                            "Portails actifs : "
                                    + (gates.isEmpty()
                                            ? "aucun"
                                            : String.join(", ", gates.keySet())),
                            NamedTextColor.GRAY));
            placedPortals(sender);
            return true;
        }
        if (args.length >= 2) {
            String id =
                    String.join("_", Arrays.copyOfRange(args, 1, args.length))
                            .toLowerCase(Locale.ROOT)
                            .replace(' ', '_');
            String normalized =
                    java.text.Normalizer.normalize(id, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "");
            if (config.isConfigurationSection("zones." + normalized)) id = normalized;
            if (args[0].equalsIgnoreCase("info")) {
                if (!config.isConfigurationSection("zones." + id)) {
                    sender.sendMessage(
                            Component.text("Zone inconnue. /buyzone liste", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(
                        Component.text(
                                "Zone "
                                        + id
                                        + " • permission : "
                                        + config.getString(
                                                "zones." + id + ".permission", "cliker.zone." + id),
                                NamedTextColor.YELLOW));
                sender.sendMessage(
                        Component.text(
                                "Portail : "
                                        + (gates.containsKey(id)
                                                ? "actif à "
                                                        + gates.get(id).center().toVector()
                                                        + " dans "
                                                        + gates.get(id)
                                                                .center()
                                                                .getWorld()
                                                                .getName()
                                                : config.contains("portails." + id)
                                                        ? "monde non chargé ou configuration"
                                                                + " invalide"
                                                        : "non créé"),
                                NamedTextColor.GRAY));
                sender.sendMessage(
                        Component.text(
                                "Contournement actif pour toi : "
                                        + sender.hasPermission(BYPASS)
                                        + " • /buyzone spawn "
                                        + id
                                        + " pour créer le portail",
                                NamedTextColor.GRAY));
                return true;
            }
            if (args[0].equalsIgnoreCase("remove")) {
                BuyZone previous = gates.remove(id);
                if (previous == null && !config.contains("portails." + id)) {
                    sender.sendMessage(
                            Component.text("Aucun portail pour cette zone.", NamedTextColor.RED));
                    return true;
                }
                config.set("portails." + id, null);
                try {
                    saveConfig();
                } catch (Exception e) {
                    if (previous != null) {
                        gates.put(id, previous);
                        savePosition(id, previous.center());
                    }
                    sender.sendMessage(
                            Component.text("Échec de la sauvegarde.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(
                        Component.text(
                                "Portail " + id + " supprimé. Les achats sont conservés.",
                                NamedTextColor.GREEN));
                return true;
            }
            if (args[0].equalsIgnoreCase("spawn") && sender instanceof Player player) {
                try {
                    if (config.contains("portails." + id)) {
                        player.sendMessage(
                                Component.text(
                                        "Cette zone a déjà un portail. Utilise /buyzone remove "
                                                + id
                                                + " avant de le déplacer.",
                                        NamedTextColor.RED));
                        return true;
                    }
                    Location origin = player.getLocation();
                    origin.setY(origin.getBlockY());
                    BuyZone zone = definition(id, origin);
                    if (zone.price() < 0) {
                        player.sendMessage(
                                Component.text(
                                        "Le prix de "
                                                + zone.name()
                                                + " manque dans buyzone.yml (zones."
                                                + id
                                                + ".prix). Configure-le puis redémarre.",
                                        NamedTextColor.RED));
                        return true;
                    }
                    if (northSafe(player, zone) == null) {
                        player.sendMessage(
                                Component.text(
                                        "Prévois un sol sûr à 15 blocs au nord du portail pour le"
                                                + " retour des joueurs.",
                                        NamedTextColor.RED));
                        return true;
                    }
                    gates.put(id, zone);
                    savePosition(id, zone.center());
                    try {
                        saveConfig();
                    } catch (Exception e) {
                        gates.remove(id);
                        config.set("portails." + id, null);
                        throw e;
                    }
                    player.sendMessage(
                            Component.text(
                                    "Portail "
                                            + zone.name()
                                            + " créé : 40 blocs sur l’axe est-ouest, entrée depuis"
                                            + " le nord.",
                                    NamedTextColor.GREEN));
                    if (player.hasPermission(BYPASS))
                        player.sendMessage(
                                Component.text(
                                        "Tu disposes de cliker.buyzone.bypass : le menu d’achat ne"
                                                + " s’ouvrira pas pour toi.",
                                        NamedTextColor.YELLOW));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Création buyzone impossible : " + id, e);
                    player.sendMessage(
                            Component.text(String.valueOf(e.getMessage()), NamedTextColor.RED));
                }
                return true;
            }
        }
        sender.sendMessage(
                Component.text(
                        "/buyzone spawn|remove|info <zone> | liste | portails | joueur <pseudo> |"
                                + " debloquer|retirer <pseudo> <zone>",
                        NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN)) return List.of();
        Set<String> options = new HashSet<>();
        if (args.length == 1)
            options.addAll(
                    Set.of(
                            "spawn",
                            "remove",
                            "info",
                            "liste",
                            "portails",
                            "joueur",
                            "player",
                            "debloquer",
                            "unlock",
                            "retirer",
                            "revoke"));
        else if (args.length == 2
                && Set.of("spawn", "remove", "info").contains(args[0].toLowerCase(Locale.ROOT)))
            options.addAll(config.getConfigurationSection("zones").getKeys(false));
        else if (args.length == 2
                && Set.of("joueur", "player", "debloquer", "unlock", "retirer", "revoke")
                        .contains(args[0].toLowerCase(Locale.ROOT)))
            for (var player : Bukkit.getOnlinePlayers()) options.add(player.getName());
        else if (args.length == 3
                && Set.of("debloquer", "unlock", "retirer", "revoke")
                        .contains(args[0].toLowerCase(Locale.ROOT)))
            options.addAll(config.getConfigurationSection("zones").getKeys(false));
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
        attachments.clear();
        offers.clear();
        sessions.clear();
        unlocked.clear();
        decisions.clear();
    }

    private static final class Offer implements InventoryHolder {
        private final UUID owner;
        private final BuyZone zone;
        private final Location previous;
        private Inventory inventory;
        private boolean completed;

        private Offer(UUID owner, BuyZone zone, Location previous) {
            this.owner = owner;
            this.zone = zone;
            this.previous = previous;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
