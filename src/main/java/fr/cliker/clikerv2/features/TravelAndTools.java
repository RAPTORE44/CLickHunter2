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
    private final Map<String, String> upgradeBlocks = new HashMap<>();
    private final Map<String, String> bestiaryBlocks = new HashMap<>();
    private final Map<String, String> warpBlocks = new HashMap<>();
    private final Map<String, fr.cliker.clikerv2.hologram.SharedHolograms.Handle> shortcutHolos =
            new HashMap<>();
    private org.bukkit.scheduler.BukkitTask holoTask;
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
        var upgrades = settings.yaml.getConfigurationSection("upgrades");
        if (upgrades != null)
            for (String key : upgrades.getKeys(false)) {
                var at = YamlFiles.location(settings.yaml, "upgrades." + key);
                if (at != null)
                    upgradeBlocks.put(
                            fr.cliker.clikerv2.station.StationStore.idFor(at.getBlock()), key);
            }
        var warps = settings.yaml.getConfigurationSection("raccourcis-warp");
        if (warps != null)
            for (String key : warps.getKeys(false)) {
                var at = YamlFiles.location(settings.yaml, "raccourcis-warp." + key);
                if (at != null)
                    warpBlocks.put(
                            fr.cliker.clikerv2.station.StationStore.idFor(at.getBlock()), key);
            }
        var bestiary = settings.yaml.getConfigurationSection("bestiaires");
        if (bestiary != null)
            for (String key : bestiary.getKeys(false)) {
                var at = YamlFiles.location(settings.yaml, "bestiaires." + key);
                if (at != null)
                    bestiaryBlocks.put(
                            fr.cliker.clikerv2.station.StationStore.idFor(at.getBlock()), key);
            }
        holoTask = Bukkit.getScheduler().runTaskTimer(p, this::refreshHolos, 1, 40);
        for (String cmd :
                List.of(
                        "warp",
                        "warpbloc",
                        "sacbloc",
                        "upgradebloc",
                        "bestiairebloc",
                        "batonspawn")) {
            p.getCommand(cmd).setExecutor(this);
            p.getCommand(cmd).setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, p);
    }

    private boolean access(Player p, MobTier t) {
        if (t == MobTier.VACHE) return true;
        String node = "cliker.zone." + t.id();
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            var permission =
                    net.luckperms.api.LuckPermsProvider.get()
                            .getPlayerAdapter(Player.class)
                            .getPermissionData(p)
                            .checkPermission(node);
            if (permission == net.luckperms.api.util.Tristate.FALSE) return false;
        }
        return p.hasPermission(node);
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
        var service = Bukkit.getServicesManager().load(Talismans.class);
        if (service == null) {
            renderWarp(p, Set.of());
            return;
        }
        var previous = p.getOpenInventory().getTopInventory();
        p.sendActionBar(net.kyori.adventure.text.Component.text("Chargement des destinations…"));
        service.discoveries(p)
                .whenComplete(
                        (known, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (!fr.cliker.clikerv2.runtime.PlayerSessions
                                                            .isCurrent(p)
                                                    || p.getOpenInventory().getTopInventory()
                                                            != previous) return;
                                            if (error != null) {
                                                p.sendMessage(
                                                        "Destinations indisponibles. Réessaie dans"
                                                                + " un instant.");
                                                return;
                                            }
                                            renderWarp(p, known);
                                        }));
    }

    private void renderWarp(Player p, Set<String> known) {
        if (!p.hasPermission("cliker.navigation.use")) return;
        var view = menus.create(p, 54, "Voyager — ClickHunter");
        int visibleSlot = 0;
        for (var t : MobTier.values()) {
            if (!access(p, t)) continue;
            Material icon = Material.matchMaterial(t.entityType().name() + "_SPAWN_EGG");
            if (icon == null) icon = Material.DRAGON_HEAD;
            view.put(
                    visibleSlot++,
                    Menus.item(
                            icon,
                            "&a&lZone " + (t.ordinal() + 1) + " &8— &f" + t.label(),
                            "&aClic gauche &7: entrée de la zone",
                            "&6Clic droit &7: devant le boss",
                            "&8─────────────────",
                            "&7PV de base : &c"
                                    + fr.cliker.clikerv2.Numbers.compact(Math.round(t.maxHealth())),
                            "&7Écus de base : &e"
                                    + fr.cliker.clikerv2.Numbers.compact(
                                            (long)
                                                    CreatureSettings.value(
                                                            t, "ecus", 10L * (t.ordinal() + 1))),
                            "&7Gemmes de base : &5"
                                    + fr.cliker.clikerv2.economy.CurrencyType.GEMMES_SOMBRES.format(
                                            (long)
                                                    CreatureSettings.value(
                                                            t, "gemmes-dixiemes", t.ordinal() + 1)),
                            "&7XP de base : &b"
                                    + fr.cliker.clikerv2.Numbers.compact(
                                            (long)
                                                    CreatureSettings.value(
                                                            t, "xp", t.ordinal() + 1)),
                            "&7Talismans manquants : &d"
                                    + ((known.contains("mob:" + t.id()) ? 0 : 1)
                                            + (known.contains("boss:" + t.id()) ? 0 : 1))
                                    + "/2",
                            "&8Consulte /bestiaire pour les découvertes.",
                            "&8Point non configuré : voyage indisponible."),
                    (player, right) -> {
                        if (!access(player, t)) {
                            player.sendMessage("§cZone verrouillée.");
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
        if (!Double.isFinite(target.getX())
                || !Double.isFinite(target.getY())
                || !Double.isFinite(target.getZ())
                || !Float.isFinite(target.getYaw())
                || !Float.isFinite(target.getPitch())
                || target.getY() < target.getWorld().getMinHeight() + 1
                || target.getY() >= target.getWorld().getMaxHeight() - 1
                || !target.getWorld().getWorldBorder().isInside(target)) {
            p.sendMessage(
                    "Le point d’arrivée est invalide ou hors des limites du monde. Préviens un"
                            + " administrateur.");
            return false;
        }
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
        if (c.getName().equals("batonspawn") && a.length == 0) {
            spawnMenu(p);
            return true;
        }
        try {
            switch (c.getName()) {
                case "warp" -> {
                    if (a.length == 2
                            && a[0].equalsIgnoreCase("set")
                            && a[1].equalsIgnoreCase("all"))
                        return onCommand(
                                p, plugin.getCommand("warpbloc"), "warpbloc", new String[] {"set"});
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
                case "sacbloc", "upgradebloc", "warpbloc", "bestiairebloc" -> {
                    boolean upgrade = c.getName().equals("upgradebloc");
                    boolean warp = c.getName().equals("warpbloc");
                    boolean bestiary = c.getName().equals("bestiairebloc");
                    var blocks =
                            bestiary
                                    ? bestiaryBlocks
                                    : warp ? warpBlocks : upgrade ? upgradeBlocks : bagBlocks;
                    String section =
                            bestiary
                                    ? "bestiaires."
                                    : warp ? "raccourcis-warp." : upgrade ? "upgrades." : "sacs.";
                    Block b = p.getTargetBlockExact(5);
                    if (b == null) {
                        p.sendMessage("Vise un bloc à moins de cinq blocs.");
                        break;
                    }
                    String id = fr.cliker.clikerv2.station.StationStore.idFor(b);
                    if (a.length == 1 && a[0].equalsIgnoreCase("set")) {
                        String key = blocks.getOrDefault(id, UUID.randomUUID().toString());
                        YamlFiles.location(settings.yaml, section + key, b.getLocation());
                        settings.save();
                        blocks.put(id, key);
                        p.sendMessage(
                                bestiary
                                        ? "Bloc lié au menu /bestiaire."
                                        : warp
                                                ? "Bloc lié au menu /warp."
                                                : upgrade
                                                        ? "Bloc lié au menu /upgrade."
                                                        : "Bloc lié au /sac de chaque joueur.");
                    } else if (a.length == 1 && a[0].equalsIgnoreCase("remove")) {
                        String key = blocks.get(id);
                        if (key != null) {
                            settings.yaml.set(section + key, null);
                            settings.save();
                            blocks.remove(id);
                        }
                        p.sendMessage("Raccourci retiré.");
                    } else p.sendMessage("/" + c.getName() + " set|remove (en visant le bloc)");
                    refreshHolos();
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
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)
                && (upgradeBlocks.containsKey(
                                fr.cliker.clikerv2.station.StationStore.idFor(e.getClickedBlock()))
                        || bagBlocks.containsKey(
                                fr.cliker.clikerv2.station.StationStore.idFor(
                                        e.getClickedBlock())))) {
            e.setCancelled(true);
            p.sendMessage("Reviens au /spawn pour utiliser ce raccourci clicker.");
            return;
        }
        if (bestiaryBlocks.containsKey(
                fr.cliker.clikerv2.station.StationStore.idFor(e.getClickedBlock()))) {
            e.setCancelled(true);
            p.performCommand("bestiaire");
            return;
        }
        if (warpBlocks.containsKey(
                fr.cliker.clikerv2.station.StationStore.idFor(e.getClickedBlock()))) {
            e.setCancelled(true);
            open(p);
            return;
        }
        if (upgradeBlocks.containsKey(
                fr.cliker.clikerv2.station.StationStore.idFor(e.getClickedBlock()))) {
            e.setCancelled(true);
            p.performCommand("upgrade");
            return;
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
        if (c.getName().equals("warp")
                && a.length == 2
                && s.hasPermission("cliker.services.admin")) {
            values = new ArrayList<>(MobTier.byId().keySet());
            values.add("all");
        }
        if (c.getName().equals("warp") && a.length == 3 && s.hasPermission("cliker.services.admin"))
            values = List.of("entree", "boss");
        if ((c.getName().equals("sacbloc")
                        || c.getName().equals("upgradebloc")
                        || c.getName().equals("warpbloc")
                        || c.getName().equals("bestiairebloc"))
                && s.hasPermission("cliker.services.admin")) values = List.of("set", "remove");
        if (c.getName().equals("batonspawn") && s.hasPermission("cliker.services.admin"))
            values =
                    a.length == 1
                            ? List.of("mobs", "boss")
                            : a.length == 2 ? new ArrayList<>(MobTier.byId().keySet()) : List.of();
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(x -> x.startsWith(last)).toList();
    }

    private void spawnMenu(Player player) {
        var view = menus.create(player, 54, "Placement des créatures");
        for (var tier : MobTier.values()) {
            Material icon = Material.matchMaterial(tier.entityType().name() + "_SPAWN_EGG");
            view.put(
                    tier.ordinal(),
                    Menus.item(
                            icon == null ? Material.EGG : icon,
                            tier.label(),
                            "Gauche : créature | Droite : boss",
                            "Reçois un bâton puis clique sur le bloc de placement."),
                    (who, right) -> {
                        if (!who.hasPermission("cliker.services.admin")) return;
                        who.closeInventory();
                        who.performCommand("batonspawn " + (right ? "boss " : "mobs ") + tier.id());
                    });
        }
        view.show(player);
    }

    private void refreshHolos() {
        var wanted = new HashSet<String>();
        for (String section : List.of("sacs", "upgrades", "raccourcis-warp", "bestiaires")) {
            var entries = settings.yaml.getConfigurationSection(section);
            if (entries == null) continue;
            for (String key : entries.getKeys(false)) {
                var at = YamlFiles.location(settings.yaml, section + "." + key);
                String id = section + ":" + key;
                if (at == null
                        || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4))
                    continue;
                wanted.add(id);
                var old = shortcutHolos.get(id);
                if (old != null && old.isValid()) continue;
                var provider = fr.cliker.clikerv2.hologram.SharedHolograms.service();
                if (provider == null || !provider.available()) continue;
                var display =
                        provider.create(
                                "shortcut:" + id,
                                at.clone().add(.5, 1.6, .5),
                                java.util.Arrays.asList(
                                        settings.yaml
                                                .getString(
                                                        "hologrammes." + section,
                                                        section.equals("bestiaires")
                                                                ? "&2&lBestiaire\n"
                                                                      + "&fClic droit pour ouvrir"
                                                                : section.equals("sacs")
                                                                        ? "&a&lSacs\n"
                                                                              + "&fClic droit pour"
                                                                              + " ouvrir"
                                                                        : section.equals(
                                                                                        "raccourcis-warp")
                                                                                ? "&a&lVoyager\n"
                                                                                      + "&fClic"
                                                                                      + " droit"
                                                                                      + " pour"
                                                                                      + " ouvrir"
                                                                                : "&a&lAméliorations\n"
                                                                                      + "&fClic"
                                                                                      + " droit"
                                                                                      + " pour"
                                                                                      + " ouvrir")
                                                .split("\n")),
                                null,
                                false);
                shortcutHolos.put(id, display);
            }
        }
        shortcutHolos
                .entrySet()
                .removeIf(
                        entry -> {
                            if (wanted.contains(entry.getKey())) return false;
                            entry.getValue().remove();
                            return true;
                        });
    }

    public void shutdown() {
        if (holoTask != null) holoTask.cancel();
        shortcutHolos.values().forEach(fr.cliker.clikerv2.hologram.SharedHolograms.Handle::remove);
        shortcutHolos.clear();
        HandlerList.unregisterAll(this);
    }
}
