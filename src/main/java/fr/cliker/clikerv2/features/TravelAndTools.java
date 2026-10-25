package fr.cliker.clikerv2.features;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.buyzone.*;
import fr.cliker.clikerv2.config.YamlFiles;
import fr.cliker.clikerv2.mobs.*;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class TravelAndTools implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final Menus menus;
    private final BuyZoneModule zones;
    private final CustomMobManager mobs;
    private final Settings settings;
    private final String builderWorld;
    private final NamespacedKey wand, boss, egg;
    private final Map<String, String> bagBlocks = new HashMap<>();
    private final Map<UUID, Long> useTimes = new HashMap<>();

    public TravelAndTools(JavaPlugin p, Menus menus, BuyZoneModule zones, CustomMobManager mobs) {
        plugin = p;
        this.menus = menus;
        this.zones = zones;
        this.mobs = mobs;
        settings = new Settings(p, "navigation/warps.yml");
        builderWorld =
                new Settings(p, "activites/avantpostes.yml")
                        .yaml.getString("monde", "clickhunter_avantpostes");
        wand = new NamespacedKey(p, "spawn_wand");
        boss = new NamespacedKey(p, "spawn_wand_boss");
        egg = new NamespacedKey(p, "market_egg");
        var sec = settings.yaml.getConfigurationSection("sacs");
        if (sec != null)
            for (String key : sec.getKeys(false)) {
                var at = YamlFiles.location(settings.yaml, "sacs." + key);
                if (at != null)
                    bagBlocks.put(
                            fr.cliker.clikerv2.station.StationStore.idFor(at.getBlock()), key);
            }
        for (String cmd : List.of("warp", "sacbloc", "batonspawn")) {
            p.getCommand(cmd).setExecutor(this);
            p.getCommand(cmd).setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, p);
    }

    private boolean access(Player p, MobTier t) {
        return zones != null && zones.canTravel(p, t.id());
    }

    private Location point(MobTier t, boolean b) {
        var configured =
                YamlFiles.location(
                        settings.yaml, "zones." + t.id() + "." + (b ? "boss" : "entree"));
        return configured != null
                ? configured
                : mobs.travelPoint(t, b).map(l -> l.add(0, 0, -5)).orElse(null);
    }

    public void open(Player p) {
        if (!p.hasPermission("cliker.navigation.use")) return;
        var view = menus.create(p, 54, "Voyager — ClickHunter");
        for (var t : MobTier.values()) {
            boolean unlocked = access(p, t);
            Material icon = Material.matchMaterial(t.entityType().name() + "_SPAWN_EGG");
            if (icon == null) icon = Material.DRAGON_HEAD;
            view.put(
                    t.ordinal(),
                    Menus.item(
                            unlocked ? icon : Material.BARRIER,
                            unlocked
                                    ? "Zone " + (t.ordinal() + 1) + " — " + t.label()
                                    : "Zone " + (t.ordinal() + 1) + " — Mystère",
                            unlocked
                                    ? "Gauche : début de zone | Droite : devant le boss"
                                    : "Débloque cette zone pour voyager.",
                            "Un point non configuré reste inaccessible."),
                    (player, right) -> {
                        if (!access(player, t)) {
                            player.sendMessage("Zone verrouillée.");
                            return;
                        }
                        safeTeleport(player, point(t, right));
                    });
        }
        view.put(
                45,
                Menus.item(Material.NETHER_STAR, "Spawn"),
                (player, r) -> {
                    player.closeInventory();
                    player.performCommand("spawn");
                });
        view.put(
                49,
                Menus.item(Material.MAP, "Raid en groupe"),
                (player, r) -> {
                    player.closeInventory();
                    player.performCommand("raid");
                });
        view.put(
                53,
                Menus.item(Material.NETHERITE_SWORD, "Boss du serveur"),
                (player, r) -> {
                    player.closeInventory();
                    player.performCommand("boss");
                });
        view.show(p);
    }

    public static boolean safeTeleport(Player p, Location target) {
        if (target == null || target.getWorld() == null) {
            p.sendMessage("Point non configuré. Préviens un administrateur.");
            return false;
        }
        target = target.clone();
        if (!Double.isFinite(target.getX()) || !Double.isFinite(target.getZ())) return false;
        var floor = target.clone().subtract(0, 1, 0).getBlock();
        if (!floor.getType().isSolid()
                || floor.isLiquid()
                || !target.getBlock().isPassable()
                || !target.clone().add(0, 1, 0).getBlock().isPassable()
                || Set.of(
                                Material.MAGMA_BLOCK,
                                Material.CACTUS,
                                Material.CAMPFIRE,
                                Material.SOUL_CAMPFIRE)
                        .contains(floor.getType())) {
            p.sendMessage("Le point d’arrivée est dangereux. Préviens un administrateur.");
            return false;
        }
        p.closeInventory();
        return p.teleport(target);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) return true;
        if (c.getName().equals("warp") && a.length == 0) {
            open(p);
            return true;
        }
        if (!p.hasPermission("cliker.services.admin")) {
            p.sendMessage("Permission administrateur requise.");
            return true;
        }
        try {
            switch (c.getName()) {
                case "warp" -> {
                    if (a.length == 3
                            && a[0].equalsIgnoreCase("set")
                            && MobTier.parse(a[1]) != null
                            && Set.of("entree", "boss").contains(a[2])) {
                        var t = MobTier.parse(a[1]);
                        YamlFiles.location(
                                settings.yaml, "zones." + t.id() + "." + a[2], p.getLocation());
                        settings.save();
                        p.sendMessage("Point de voyage sauvegardé.");
                    } else p.sendMessage("/warp set <zone> <entree|boss>");
                }
                case "sacbloc" -> {
                    Block b = p.getTargetBlockExact(5);
                    if (b == null) {
                        p.sendMessage("Vise un bloc à moins de cinq blocs.");
                        break;
                    }
                    String id = fr.cliker.clikerv2.station.StationStore.idFor(b);
                    if (a.length == 1 && a[0].equalsIgnoreCase("set")) {
                        String key = bagBlocks.getOrDefault(id, UUID.randomUUID().toString());
                        YamlFiles.location(settings.yaml, "sacs." + key, b.getLocation());
                        settings.save();
                        bagBlocks.put(id, key);
                        p.sendMessage("Bloc lié au /sac de chaque joueur.");
                    } else if (a.length == 1 && a[0].equalsIgnoreCase("remove")) {
                        String key = bagBlocks.get(id);
                        if (key != null) {
                            settings.yaml.set("sacs." + key, null);
                            settings.save();
                            bagBlocks.remove(id);
                        }
                        p.sendMessage("Raccourci retiré.");
                    } else p.sendMessage("/sacbloc set|remove (en visant le bloc)");
                }
                case "batonspawn" -> {
                    if (a.length == 2
                            && Set.of("mobs", "mob", "boss").contains(a[0].toLowerCase(Locale.ROOT))
                            && MobTier.parse(a[1]) != null) {
                        var t = MobTier.parse(a[1]);
                        var i =
                                Menus.item(
                                        Material.BLAZE_ROD,
                                        "Bâton — "
                                                + (a[0].equalsIgnoreCase("boss") ? "Boss " : "")
                                                + t.label(),
                                        "Administrateur : clic droit sur un bloc.",
                                        "Crée un point d’apparition persistant.");
                        var m = i.getItemMeta();
                        m.getPersistentDataContainer().set(wand, PersistentDataType.STRING, t.id());
                        m.getPersistentDataContainer()
                                .set(
                                        boss,
                                        PersistentDataType.BYTE,
                                        (byte) (a[0].equalsIgnoreCase("boss") ? 1 : 0));
                        i.setItemMeta(m);
                        fr.cliker.clikerv2.rewards.ItemDelivery.give(p, i);
                        p.sendMessage("Bâton attribué.");
                    } else p.sendMessage("/batonspawn <mobs|boss> <nom>");
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger()
                    .log(java.util.logging.Level.SEVERE, "Outil de navigation impossible", e);
            p.sendMessage("Modification non confirmée. Consulte les journaux.");
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND
                || e.getAction() != Action.RIGHT_CLICK_BLOCK
                || e.getClickedBlock() == null) return;
        var p = e.getPlayer();
        var item = e.getItem();
        if (item != null && item.hasItemMeta()) {
            var data = item.getItemMeta().getPersistentDataContainer();
            String type = data.get(wand, PersistentDataType.STRING),
                    spawnEgg = data.get(egg, PersistentDataType.STRING);
            if (type != null || spawnEgg != null) {
                e.setCancelled(true);
                if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)) return;
                long now = System.nanoTime();
                if (now - useTimes.getOrDefault(p.getUniqueId(), now - 2_000_000_000L)
                        < 500_000_000L) return;
                useTimes.put(p.getUniqueId(), now);
                MobTier t = MobTier.parse(type != null ? type : spawnEgg);
                if (t == null) return;
                var at = e.getClickedBlock().getLocation().add(.5, 1, .5);
                if (type != null) {
                    if (!p.hasPermission("cliker.services.admin")) return;
                    mobs.addSpawn(
                            t.id(),
                            at,
                            data.getOrDefault(boss, PersistentDataType.BYTE, (byte) 0) == 1);
                    p.sendMessage("Point d’apparition créé.");
                } else {
                    var origin = mobs.travelPoint(t, false).orElse(null);
                    if (!access(p, t)
                            || origin == null
                            || origin.getWorld() != at.getWorld()
                            || origin.distanceSquared(at) > 1024) {
                        p.sendMessage("Utilise cet œuf près des créatures de sa zone débloquée.");
                        return;
                    }
                    if (!at.getBlock().isPassable()
                            || !at.clone().add(0, 1, 0).getBlock().isPassable()) return;
                    mobs.spawnOnce(t, at);
                    item.setAmount(item.getAmount() - 1);
                    p.getInventory().setItemInMainHand(item.getAmount() == 0 ? null : item);
                }
                return;
            }
        }
        if (bagBlocks.containsKey(
                fr.cliker.clikerv2.station.StationStore.idFor(e.getClickedBlock()))) {
            e.setCancelled(true);
            p.performCommand("sac");
        }
    }

    private boolean map(Player p, World w) {
        return !p.isOp()
                && !fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)
                && settings.yaml.getBoolean("protection-map", true)
                && !w.getName().equals(builderWorld);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) {
        if (map(e.getPlayer(), e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (map(e.getPlayer(), e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bucket(PlayerBucketEmptyEvent e) {
        if (map(e.getPlayer(), e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fill(PlayerBucketFillEvent e) {
        if (map(e.getPlayer(), e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hang(HangingBreakByEntityEvent e) {
        Player p = actor(e.getRemover());
        if (p != null && map(p, e.getEntity().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent e) {
        Player actor = actor(e.getDamager());
        if (actor != null
                && map(actor, e.getEntity().getWorld())
                && (e.getEntity() instanceof Hanging || e.getEntity() instanceof ArmorStand))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void manipulate(PlayerArmorStandManipulateEvent e) {
        if (map(e.getPlayer(), e.getRightClicked().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void frame(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof ItemFrame
                && map(e.getPlayer(), e.getRightClicked().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && map(e.getPlayer(), e.getEntity().getWorld()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void trample(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL
                && e.getClickedBlock() != null
                && map(e.getPlayer(), e.getClickedBlock().getWorld())) e.setCancelled(true);
    }

    private Player actor(Entity entity) {
        if (entity instanceof Player p) return p;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player p)
            return p;
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        return null;
    }

    private boolean protectedWorld(World w) {
        return settings.yaml.getBoolean("protection-map", true)
                && !w.getName().equals(builderWorld);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityPlace(EntityPlaceEvent e) {
        if (e.getPlayer() != null && map(e.getPlayer(), e.getEntity().getWorld()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void ignite(BlockIgniteEvent e) {
        if (e.getPlayer() != null && map(e.getPlayer(), e.getBlock().getWorld()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent e) {
        if (protectedWorld(e.getLocation().getWorld())) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockExplosion(BlockExplodeEvent e) {
        if (protectedWorld(e.getBlock().getWorld())) e.blockList().clear();
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        useTimes.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> values = List.of();
        if (c.getName().equals("warp") && a.length == 1 && s.hasPermission("cliker.services.admin"))
            values = List.of("set");
        if (c.getName().equals("warp") && a.length == 2 && s.hasPermission("cliker.services.admin"))
            values = new ArrayList<>(MobTier.byId().keySet());
        if (c.getName().equals("warp") && a.length == 3 && s.hasPermission("cliker.services.admin"))
            values = List.of("entree", "boss");
        if (c.getName().equals("sacbloc") && s.hasPermission("cliker.services.admin"))
            values = List.of("set", "remove");
        if (c.getName().equals("batonspawn") && s.hasPermission("cliker.services.admin"))
            values =
                    a.length == 1
                            ? List.of("mobs", "boss")
                            : a.length == 2 ? new ArrayList<>(MobTier.byId().keySet()) : List.of();
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(x -> x.startsWith(last)).toList();
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
    }
}
