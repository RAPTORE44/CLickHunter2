package fr.cliker.clikerv2.outpost;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.hunting.HuntUI;
import fr.cliker.clikerv2.rewards.ClaimService;
import fr.cliker.clikerv2.world.ActivityWorlds;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class OutpostModule implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final OutpostStore store;
    private final ClaimService claims;
    private final YamlConfiguration config;
    private final Map<UUID, OutpostStore.Plot> plots = new HashMap<>();
    private final Map<Long, OutpostStore.Plot> grid = new HashMap<>();
    private final Map<UUID, Set<UUID>> trusted = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, Location> returns = new HashMap<>();
    private final Map<UUID, OutpostStore.Plot> viewing = new HashMap<>();
    private final int[] radii;
    private final long[] prices;
    private World world;
    private boolean ready, stopped;
    private BukkitTask task;
    private WorldInventories inventories;
    private final DatabaseManager database;
    private OutpostBlocks blocks;

    public OutpostModule(JavaPlugin plugin, DatabaseManager db, ClaimService claims) {
        this.plugin = plugin;
        database = db;
        this.store = new OutpostStore(db);
        this.claims = claims;
        File file = new File(plugin.getDataFolder(), "avantpostes.yml");
        if (!file.exists()) plugin.saveResource("avantpostes.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        radii =
                config.getIntegerList("extensions.rayons").stream()
                        .mapToInt(Integer::intValue)
                        .toArray();
        prices =
                config.getLongList("extensions.prix-ecus").stream()
                        .mapToLong(Long::longValue)
                        .toArray();
        if (radii.length == 0 || radii.length != prices.length)
            throw new IllegalArgumentException("Rayons/prix d'avant-postes invalides");
        for (int i = 0; i < radii.length; i++)
            if (radii[i] < 10
                    || radii[i] > 96
                    || prices[i] < 0
                    || i > 0 && radii[i] <= radii[i - 1])
                throw new IllegalArgumentException(
                        "Extensions invalides : rayons croissants de 10 à 96");
    }

    public void start() {
        world = ActivityWorlds.create(config.getString("monde", "clickhunter_avantpostes"), false);
        inventories = new WorldInventories(plugin, world.getName());
        inventories.start();
        blocks = new OutpostBlocks(plugin, database, config, world);
        blocks.start();
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("avantposte")).setExecutor(this);
        plugin.getCommand("avantposte").setTabCompleter(this);
        result(
                null,
                store.load(),
                snapshot -> {
                    snapshot.plots().forEach(this::cache);
                    snapshot.trusted().forEach((k, v) -> trusted.put(k, new HashSet<>(v)));
                    ready = true;
                });
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 10);
    }

    private void cache(OutpostStore.Plot plot) {
        plots.put(plot.owner(), plot);
        grid.put(plot.id(), plot);
    }

    private int radius(OutpostStore.Plot plot) {
        return radii[Math.min(plot.tier(), radii.length - 1)];
    }

    private OutpostStore.Plot at(Location location) {
        if (location.getWorld() != world || !ready) return null;
        long gx = Math.round(location.getX() / 256.0), gz = Math.round(location.getZ() / 256.0);
        if (gx < 0 || gx >= 100 || gz < 0) return null;
        var p = grid.get(gz * 100 + gx);
        return p != null && p.contains(location.getX(), location.getZ(), radius(p)) ? p : null;
    }

    public boolean canBuild(UUID player, Location location) {
        var plot = at(location);
        return plot != null
                && (plot.owner().equals(player)
                        || trusted.getOrDefault(plot.owner(), Set.of()).contains(player));
    }

    private boolean denied(Player player, Location location) {
        return location.getWorld() == world
                && player.getGameMode() != GameMode.CREATIVE
                && !player.hasPermission("cliker.avantposte.admin")
                && !canBuild(player.getUniqueId(), location);
    }

    private void message(Player player, String key) {
        player.sendMessage(DailySettings.color(config.getString("messages." + key, key)));
    }

    private <T> void result(Player player, CompletableFuture<T> future, Consumer<T> action) {
        future.whenComplete(
                (value, error) -> {
                    if (stopped || !plugin.isEnabled()) return;
                    fr.cliker.clikerv2.runtime.ServerTasks.run(
                            plugin,
                            () -> {
                                if (player != null) busy.remove(player.getUniqueId());
                                if (error != null) {
                                    plugin.getLogger()
                                            .log(
                                                    java.util.logging.Level.SEVERE,
                                                    "Erreur d'avant-poste",
                                                    error);
                                    if (player != null && player.isOnline())
                                        message(player, "erreur");
                                } else action.accept(value);
                            });
                });
    }

    private Location home(OutpostStore.Plot plot) {
        return new Location(world, plot.x() + 0.5, 65, plot.z() + 0.5);
    }

    private Location safeHome(OutpostStore.Plot plot) {
        // Consulter la heightmap évite de parcourir toute la hauteur du monde.
        for (int distance = 0; distance <= 4; distance++)
            for (int dx = -distance; dx <= distance; dx++)
                for (int dz = -distance; dz <= distance; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) continue;
                    int x = plot.x() + dx, z = plot.z() + dz, y = world.getHighestBlockYAt(x, z);
                    if (y >= world.getMaxHeight() - 2) continue;
                    Block floor = world.getBlockAt(x, y, z),
                            feet = world.getBlockAt(x, y + 1, z),
                            head = world.getBlockAt(x, y + 2, z);
                    if (floor.getType().isSolid()
                            && safeBlock(floor)
                            && feet.isPassable()
                            && safeBlock(feet)
                            && head.isPassable()
                            && safeBlock(head)) return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
        return null; // Ne jamais détruire une construction pour fabriquer un point de retour.
    }

    private boolean safeBlock(Block block) {
        return !block.isLiquid()
                && !java.util.Set.of(
                                Material.MAGMA_BLOCK,
                                Material.CACTUS,
                                Material.FIRE,
                                Material.SOUL_FIRE,
                                Material.CAMPFIRE,
                                Material.SOUL_CAMPFIRE,
                                Material.POWDER_SNOW)
                        .contains(block.getType());
    }

    private void visit(Player player, OutpostStore.Plot plot) {
        if (!player.isOnline()) return;
        if (player.getWorld() != world)
            returns.put(player.getUniqueId(), player.getLocation().clone());
        Location destination = safeHome(plot);
        if (destination == null) {
            message(player, "retour-impossible");
            return;
        }
        if (player.teleport(destination)) {
            viewing.put(player.getUniqueId(), plot);
            border(player, plot);
            message(player, plot.owner().equals(player.getUniqueId()) ? "bienvenue" : "visite");
        }
    }

    private void border(Player player, OutpostStore.Plot plot) {
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(plot.x() + 0.5, plot.z() + 0.5);
        border.setSize(radius(plot) * 2 + 1);
        border.setWarningDistance(2);
        border.setDamageAmount(0);
        player.setWorldBorder(border);
    }

    private void menu(Player player) {
        var plot = plots.get(player.getUniqueId());
        var view =
                HuntUI.view(
                        player,
                        "&2Mon avant-poste",
                        27,
                        slot -> {
                            if (slot == 11) {
                                player.closeInventory();
                                player.performCommand("avantposte aller");
                            }
                            if (slot == 15) {
                                player.closeInventory();
                                player.performCommand("avantposte agrandir");
                            }
                        });
        view.getInventory()
                .setItem(
                        11,
                        HuntUI.item(
                                Material.GRASS_BLOCK,
                                plot == null
                                        ? "&aCréer mon avant-poste"
                                        : "&aRejoindre mon avant-poste",
                                List.of(
                                        "&7Un terrain personnel pour construire librement.",
                                        "&7Visites publiques, constructions protégées.",
                                        "&eClique pour rejoindre.")));
        List<String> lore = new ArrayList<>();
        if (plot == null) lore.add("&7Crée d'abord ton avant-poste.");
        else {
            lore.add("&7Surface : &f" + (radius(plot) * 2 + 1) + " × " + (radius(plot) * 2 + 1));
            int next = plot.tier() + 1;
            if (next < radii.length) {
                lore.add(
                        "&7Prochaine taille : &f"
                                + (radii[next] * 2 + 1)
                                + " × "
                                + (radii[next] * 2 + 1));
                lore.add("&6Prix : " + prices[next] + " Écus");
                lore.add("&eClique pour acheter l'extension.");
            } else lore.add("&aTaille maximale atteinte.");
        }
        view.getInventory()
                .setItem(15, HuntUI.item(Material.GOLD_INGOT, "&6Agrandir mon terrain", lore));
        view.getInventory()
                .setItem(
                        22,
                        HuntUI.item(
                                Material.BOOK,
                                "&bCommandes utiles",
                                List.of(
                                        "&f/avantposte visite <pseudo>",
                                        "&f/avantposte ajouter <pseudo>",
                                        "&f/avantposte retirer <pseudo>",
                                        "&f/avantposte top",
                                        "&f/avantposte sortir")));
        fr.cliker.clikerv2.ui.MenuNavigation.back(plugin, view.getInventory(), 26);
        player.openInventory(view.getInventory());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("cliker.avantposte")) {
            message(player, "interdit");
            return true;
        }
        if (!ready) {
            message(player, "chargement");
            return true;
        }
        if (args.length == 0) {
            menu(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("pnj") && args.length == 2 && args[1].equalsIgnoreCase("deplacer")) {
            var own = plots.get(player.getUniqueId());
            if (own == null
                    || player.getWorld() != world
                    || !own.contains(player.getX(), player.getZ(), radius(own))) {
                player.sendMessage(
                        "Place-toi dans ton propre avant-poste pour déplacer ton marchand.");
                return true;
            }
            blocks.move(player, own);
            return true;
        }
        if (sub.equals("blocs")) {
            if (player.getWorld() != world) {
                player.sendMessage("Rejoins ton avant-poste avant d’ouvrir la boutique.");
                return true;
            }
            blocks.open(player, 0);
            return true;
        }
        if (sub.equals("sortir")) {
            leave(player);
            return true;
        }
        if (sub.equals("top")) {
            result(
                    player,
                    store.wealth(),
                    lines -> {
                        if (player.isOnline())
                            lines.forEach(line -> player.sendMessage(DailySettings.color(line)));
                    });
            return true;
        }
        if (sub.equals("visite") && args.length == 2) {
            var plot =
                    plots.values().stream()
                            .filter(p -> p.name().equalsIgnoreCase(args[1]))
                            .findFirst()
                            .orElse(null);
            if (plot == null) message(player, "absent");
            else visit(player, plot);
            return true;
        }
        if (sub.equals("aller")) {
            if (!busy.add(player.getUniqueId())) return true;
            result(
                    player,
                    store.create(player.getUniqueId(), player.getName()),
                    plot -> {
                        cache(plot);
                        visit(player, plot);
                    });
            return true;
        }
        var own = plots.get(player.getUniqueId());
        if (own == null) {
            message(player, "absent");
            return true;
        }
        if (sub.equals("agrandir")) {
            if (!busy.add(player.getUniqueId())) return true;
            result(
                    player,
                    store.expand(player.getUniqueId(), prices),
                    expansion -> {
                        if (expansion.plot() != null) {
                            cache(expansion.plot());
                            viewing.replaceAll(
                                    (id, plot) ->
                                            plot.owner().equals(player.getUniqueId())
                                                    ? expansion.plot()
                                                    : plot);
                            for (Player visitor : world.getPlayers())
                                if (viewing.get(visitor.getUniqueId()) != null
                                        && viewing.get(visitor.getUniqueId())
                                                .owner()
                                                .equals(player.getUniqueId()))
                                    border(visitor, expansion.plot());
                        }
                        if (player.isOnline()) message(player, expansion.status());
                    });
            return true;
        }
        if ((sub.equals("ajouter") || sub.equals("retirer")) && args.length == 2) {
            if (!busy.add(player.getUniqueId())) return true;
            boolean add = sub.equals("ajouter");
            result(
                    player,
                    claims.resolve(args[1])
                            .thenCompose(
                                    target -> {
                                        if (target == null || target.equals(player.getUniqueId()))
                                            return CompletableFuture.completedFuture(null);
                                        return store.trust(player.getUniqueId(), target, add)
                                                .thenApply(done -> target);
                                    }),
                    guest -> {
                        if (guest == null) {
                            if (player.isOnline()) message(player, "joueur-inconnu");
                            return;
                        }
                        var set =
                                trusted.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
                        if (add) set.add(guest);
                        else set.remove(guest);
                        if (player.isOnline()) message(player, add ? "ajoute" : "retire");
                    });
            return true;
        }
        message(player, "aide");
        return true;
    }

    private void leave(Player player) {
        Location target = returns.remove(player.getUniqueId());
        if (target == null || target.getWorld() == world)
            target =
                    Bukkit.getWorlds().stream()
                            .filter(w -> w != world)
                            .findFirst()
                            .orElseThrow()
                            .getSpawnLocation();
        player.teleport(target);
        player.setWorldBorder(null);
        viewing.remove(player.getUniqueId());
    }

    private void tick() {
        if (!ready) return;
        Set<UUID> visiblePlots = new HashSet<>();
        for (Player player : world.getPlayers()) {
            var plot = viewing.get(player.getUniqueId());
            if (plot != null) plot = plots.get(plot.owner());
            if (plot == null) plot = at(player.getLocation());
            if (plot == null) plot = plots.get(player.getUniqueId());
            if (plot == null) {
                leave(player);
                continue;
            }
            visiblePlots.add(plot.owner());
            blocks.ensureNpc(plot);
            if (!plot.equals(viewing.get(player.getUniqueId()))) {
                viewing.put(player.getUniqueId(), plot);
                border(player, plot);
            }
            if (player.getY() < world.getMinHeight() + 10
                    || !plot.contains(player.getX(), player.getZ(), radius(plot))) {
                Location destination = safeHome(plot);
                if (destination == null) {
                    message(player, "retour-impossible");
                    leave(player);
                } else player.teleport(destination);
            }
        }
        blocks.retainNpcs(visiblePlots);
    }

    @EventHandler(ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void multiPlace(BlockMultiPlaceEvent e) {
        for (var state : e.getReplacedBlockStates())
            if (denied(e.getPlayer(), state.getLocation())) {
                e.setCancelled(true);
                break;
            }
    }

    @EventHandler(ignoreCancelled = true)
    public void portal(PlayerPortalEvent e) {
        if (e.getFrom().getWorld() == world) {
            e.setCancelled(true);
            message(e.getPlayer(), "portail");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void entityBlock(EntityChangeBlockEvent e) {
        if (e.getBlock().getWorld() == world && !(e.getEntity() instanceof Player))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && denied(p, e.getItem().getLocation()))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getPlayer().getWorld() != world) return;
        if (e.getClickedBlock() != null && denied(e.getPlayer(), e.getClickedBlock().getLocation()))
            e.setCancelled(true);
        else if (denied(e.getPlayer(), e.getPlayer().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void entity(PlayerInteractEntityEvent e) {
        if (denied(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void armor(PlayerArmorStandManipulateEvent e) {
        if (denied(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void bucket(PlayerBucketEmptyEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getRelative(e.getBlockFace()).getLocation()))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void fill(PlayerBucketFillEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void hanging(HangingBreakEvent e) {
        if (e.getEntity().getWorld() != world) return;
        if (!(e instanceof HangingBreakByEntityEvent by)
                || !(by.getRemover() instanceof Player player)
                || denied(player, e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void hangPlace(HangingPlaceEvent e) {
        if (e.getEntity().getWorld() == world
                && (e.getPlayer() == null || denied(e.getPlayer(), e.getEntity().getLocation())))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent e) {
        if (e.getEntity().getWorld() != world) return;
        Entity attacker = e.getDamager();
        if (attacker instanceof Projectile p && p.getShooter() instanceof Entity shooter)
            attacker = shooter;
        if (e.getEntity() instanceof Player) {
            e.setCancelled(true);
            return;
        }
        if (!(attacker instanceof Player player) || denied(player, e.getEntity().getLocation()))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void inventory(InventoryOpenEvent e) {
        Location location = e.getInventory().getLocation();
        if (location != null && e.getPlayer() instanceof Player player && denied(player, location))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void flow(BlockFromToEvent e) {
        if (e.getBlock().getWorld() == world && !samePlot(e.getBlock(), e.getToBlock()))
            e.setCancelled(true);
    }

    private boolean samePlot(Block from, Block to) {
        var a = at(from.getLocation());
        var b = at(to.getLocation());
        return a != null && b != null && a.owner().equals(b.owner());
    }

    @EventHandler(ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) {
        if (e.getBlock().getWorld() == world)
            for (Block b : e.getBlocks())
                if (!samePlot(e.getBlock(), b)
                        || !samePlot(e.getBlock(), b.getRelative(e.getDirection()))) {
                    e.setCancelled(true);
                    break;
                }
    }

    @EventHandler(ignoreCancelled = true)
    public void retract(BlockPistonRetractEvent e) {
        if (e.getBlock().getWorld() == world)
            for (Block b : e.getBlocks())
                if (!samePlot(e.getBlock(), b)
                        || !samePlot(
                                e.getBlock(), b.getRelative(e.getDirection().getOppositeFace()))) {
                    e.setCancelled(true);
                    break;
                }
    }

    @EventHandler
    public void explosion(EntityExplodeEvent e) {
        if (e.getLocation().getWorld() == world) e.blockList().clear();
    }

    @EventHandler
    public void blockExplosion(BlockExplodeEvent e) {
        if (e.getBlock().getWorld() == world) e.blockList().clear();
    }

    @EventHandler
    public void changed(PlayerChangedWorldEvent e) {
        if (e.getFrom() == world) {
            e.getPlayer().setWorldBorder(null);
            viewing.remove(e.getPlayer().getUniqueId());
            returns.remove(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        viewing.remove(e.getPlayer().getUniqueId());
        returns.remove(e.getPlayer().getUniqueId());
        e.getPlayer().setWorldBorder(null);
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent e) {
        if (e.getPlayer().getWorld() == world) {
            var plot = plots.get(e.getPlayer().getUniqueId());
            if (plot != null) {
                Location destination = safeHome(plot);
                if (destination != null) e.setRespawnLocation(destination);
            }
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1)
            return List.of(
                            "aller",
                            "agrandir",
                            "visite",
                            "ajouter",
                            "retirer",
                            "top",
                            "sortir",
                            "blocs",
                            "pnj")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("pnj"))
            return "deplacer".startsWith(args[1].toLowerCase(Locale.ROOT))
                    ? List.of("deplacer")
                    : List.of();
        if (args.length == 2 && args[0].equalsIgnoreCase("visite"))
            return plots.values().stream()
                    .map(OutpostStore.Plot::name)
                    .filter(
                            n ->
                                    n.toLowerCase(Locale.ROOT)
                                            .startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        return List.of();
    }

    public void shutdown() {
        if (blocks != null) blocks.shutdown();
        if (inventories != null) inventories.shutdown();
        stopped = true;
        if (task != null) task.cancel();
        if (world != null) world.getPlayers().forEach(p -> p.setWorldBorder(null));
        HandlerList.unregisterAll(this);
    }
}
