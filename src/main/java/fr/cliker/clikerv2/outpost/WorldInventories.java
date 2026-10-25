package fr.cliker.clikerv2.outpost;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Deux inventaires et XP dans le même fichier playerdata, sans transfert de blocs hors
 * avant-postes.
 */
public final class WorldInventories implements Listener {
    private final JavaPlugin plugin;
    private final String world;
    private final NamespacedKey active, clicker, builder;
    private final Set<UUID> failed = new HashSet<>();
    private static final Set<String> COMMANDS =
            Set.of(
                    "avantposte",
                    "avantpostes",
                    "is",
                    "spawn",
                    "warp",
                    "tallisement",
                    "menu",
                    "gm",
                    "gamemode",
                    "help",
                    "discord",
                    "daily",
                    "contrats",
                    "bestiaire",
                    "stats",
                    "progression",
                    "zone",
                    "z",
                    "boss",
                    "raid",
                    "settings",
                    "parametres",
                    "antidrop",
                    "ramassage");

    public WorldInventories(JavaPlugin plugin, String world) {
        this.plugin = plugin;
        this.world = world;
        active = new NamespacedKey(plugin, "inventory_profile");
        clicker = new NamespacedKey(plugin, "inventory_clicker");
        builder = new NamespacedKey(plugin, "inventory_builder");
    }

    public void start() {
        Bukkit.getServicesManager()
                .register(
                        WorldInventories.class,
                        this,
                        plugin,
                        org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (var p : Bukkit.getOnlinePlayers()) reconcile(p);
    }

    public static boolean isolated(Player player) {
        var service = Bukkit.getServicesManager().load(WorldInventories.class);
        return service != null
                && (service.inWorld(player)
                        || "builder"
                                .equals(
                                        player.getPersistentDataContainer()
                                                .get(service.active, PersistentDataType.STRING))
                        || service.failed.contains(player.getUniqueId()));
    }

    public static boolean readyForBuilder(Player player) {
        var service = Bukkit.getServicesManager().load(WorldInventories.class);
        return service != null
                && service.inWorld(player)
                && !service.failed.contains(player.getUniqueId())
                && "builder"
                        .equals(
                                player.getPersistentDataContainer()
                                        .get(service.active, PersistentDataType.STRING));
    }

    public static boolean commandAllowed(Player player, String command) {
        if (!isolated(player)) return true;
        String root = command.toLowerCase(Locale.ROOT).replaceFirst("^/", "").split(" ")[0];
        if (root.contains(":")) root = root.substring(root.indexOf(':') + 1);
        return COMMANDS.contains(root);
    }

    private boolean inWorld(Player p) {
        return p.getWorld().getName().equals(world);
    }

    private String snapshot(Player player) {
        var yaml = new YamlConfiguration();
        yaml.set("items", Arrays.asList(player.getInventory().getContents()));
        yaml.set("level", player.getLevel());
        yaml.set("progress", player.getExp());
        yaml.set("xp", player.getTotalExperience());
        return yaml.saveToString();
    }

    private record InventoryState(ItemStack[] items, int level, float progress, int xp) {}

    private InventoryState decode(String data) throws Exception {
        if (data == null) return new InventoryState(new ItemStack[41], 0, 0, 0);
        var yaml = new YamlConfiguration();
        yaml.loadFromString(data);
        var list = yaml.getList("items");
        if (list == null || list.size() != 41)
            throw new IllegalStateException("Inventaire sauvegardé invalide");
        var items = new ItemStack[41];
        for (int i = 0; i < 41; i++) {
            Object value = list.get(i);
            if (value != null && !(value instanceof ItemStack))
                throw new IllegalStateException("Objet sauvegardé invalide");
            items[i] = value == null ? null : ((ItemStack) value).clone();
        }
        int level = yaml.getInt("level"), xp = yaml.getInt("xp");
        double progress = yaml.getDouble("progress");
        if (level < 0 || xp < 0 || !Double.isFinite(progress) || progress < 0 || progress > 1)
            throw new IllegalStateException("XP sauvegardée invalide");
        return new InventoryState(items, level, (float) progress, xp);
    }

    private void apply(Player player, InventoryState state) {
        player.getInventory().setContents(state.items);
        player.setLevel(state.level);
        player.setExp(state.progress);
        player.setTotalExperience(state.xp);
    }

    public void reconcile(Player player) {
        if (failed.contains(player.getUniqueId())) return;
        var data = player.getPersistentDataContainer();
        String current = data.getOrDefault(active, PersistentDataType.STRING, "clicker");
        if (!current.equals("clicker") && !current.equals("builder")) {
            failed.add(player.getUniqueId());
            plugin.getLogger().severe("Profil d’inventaire inconnu pour " + player.getUniqueId());
            player.kickPlayer("Profil d’inventaire invalide. Contacte un administrateur.");
            return;
        }
        String wanted = inWorld(player) ? "builder" : "clicker";
        if (current.equals(wanted)) {
            if (!data.has(active, PersistentDataType.STRING))
                data.set(active, PersistentDataType.STRING, current);
            return;
        }
        var delivery =
                Bukkit.getServicesManager().load(fr.cliker.clikerv2.rewards.ClaimService.class);
        if (delivery != null && delivery.isDelivering(player.getUniqueId())) {
            plugin.getServer()
                    .getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(player))
                                    reconcile(player);
                            },
                            20);
            return;
        }
        String original;
        try {
            original = snapshot(player);
        } catch (RuntimeException error) {
            failed.add(player.getUniqueId());
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Lecture d’inventaire impossible pour " + player.getUniqueId(),
                            error);
            player.kickPlayer("Inventaire impossible à sauvegarder. Contacte un administrateur.");
            return;
        }
        NamespacedKey source = current.equals("builder") ? builder : clicker;
        NamespacedKey target = wanted.equals("builder") ? builder : clicker;
        String oldSource = data.get(source, PersistentDataType.STRING);
        try {
            var next = decode(data.get(target, PersistentDataType.STRING));
            data.set(source, PersistentDataType.STRING, original);
            data.set(active, PersistentDataType.STRING, wanted);
            apply(player, next);
            player.saveData();
            if (wanted.equals("clicker")) {
                if (delivery != null) delivery.resume(player.getUniqueId());
                var prestige =
                        Bukkit.getServicesManager()
                                .load(fr.cliker.clikerv2.prestige.PrestigeManager.class);
                if (prestige != null) prestige.resume(player);
                var ledger =
                        Bukkit.getServicesManager()
                                .load(fr.cliker.clikerv2.hunting.RewardLedger.class);
                if (ledger != null) ledger.flushXP(player.getUniqueId());
            }
        } catch (Exception error) {
            try {
                apply(player, decode(original));
            } catch (Exception ignored) {
                /* conserver le diagnostic initial */
            }
            if (oldSource == null) data.remove(source);
            else data.set(source, PersistentDataType.STRING, oldSource);
            data.set(active, PersistentDataType.STRING, current);
            failed.add(player.getUniqueId());
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Changement d’inventaire impossible pour " + player.getUniqueId(),
                            error);
            player.kickPlayer(
                    "Sauvegarde d’inventaire indisponible. Réessaie plus tard ou contacte un"
                            + " administrateur.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void join(PlayerJoinEvent e) {
        reconcile(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void changed(PlayerChangedWorldEvent e) {
        e.getPlayer().closeInventory();
        reconcile(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent e) {
        if (failed.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (e.getTo() == null || e.getFrom().getWorld().equals(e.getTo().getWorld())) return;
        var claims =
                Bukkit.getServicesManager().load(fr.cliker.clikerv2.rewards.ClaimService.class);
        if (claims != null && claims.isDelivering(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("Attends la fin de ta livraison avant de changer de monde.");
            return;
        }
        e.getPlayer().closeInventory();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void respawn(PlayerRespawnEvent e) {
        fr.cliker.clikerv2.runtime.ServerTasks.run(
                plugin,
                () -> {
                    if (fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(e.getPlayer()))
                        reconcile(e.getPlayer());
                });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent e) {
        if (!commandAllowed(e.getPlayer(), e.getMessage())) {
            e.setCancelled(true);
            e.getPlayer()
                    .sendMessage(
                            "Cette commande n’est pas disponible dans l’avant-poste. Reviens au"
                                    + " /spawn pour utiliser ton inventaire clicker.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !isolated(p)) return;
        if (e.getInventory().getType() == InventoryType.ENDER_CHEST) {
            e.setCancelled(true);
            p.sendMessage("Le coffre de l’End est désactivé dans les avant-postes.");
            return;
        }
        var holder = e.getInventory().getHolder();
        var at = e.getInventory().getLocation();
        if (e.getInventory().getType() != InventoryType.CRAFTING
                && at == null
                && (holder == null
                        || !holder.getClass().getName().startsWith("fr.cliker.clikerv2.")))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityTeleport(EntityTeleportEvent e) {
        if (!(e.getEntity() instanceof Player)
                && e.getFrom().getWorld().getName().equals(world)
                && e.getTo() != null
                && !e.getTo().getWorld().getName().equals(world)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityPortal(EntityPortalEvent e) {
        if (!(e.getEntity() instanceof Player)
                && e.getFrom().getWorld().getName().equals(world)
                && e.getTo() != null
                && !e.getTo().getWorld().getName().equals(world)) e.setCancelled(true);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        failed.remove(e.getPlayer().getUniqueId());
    }

    public void shutdown() {
        Bukkit.getServicesManager().unregister(WorldInventories.class, this);
        HandlerList.unregisterAll(this);
        failed.clear();
    }
}
