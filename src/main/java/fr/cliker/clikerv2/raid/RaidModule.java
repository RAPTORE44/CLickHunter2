package fr.cliker.clikerv2.raid;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.hunting.*;
import fr.cliker.clikerv2.mobs.CustomMobManager;
import fr.cliker.clikerv2.world.ActivityWorlds;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

public final class RaidModule implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final RewardLedger ledger;
    private final CustomMobManager combat;
    private final YamlConfiguration config;
    private final Map<UUID, Long> health = new HashMap<>(), damage = new LinkedHashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> normals = new HashSet<>();
    private final Map<UUID, Location> returns = new HashMap<>();
    private final NamespacedKey tag;
    private final long normalHP, configuredBossHP;
    private final int threshold, cap;
    private final double normalDamage, bossDamage, bossRange;
    private final Gains normalReward, bossReward;
    private World world;
    private UUID boss;
    private long bossMax;
    private int kills;
    private BukkitTask task;
    private BossBar bar;
    private boolean stopped;

    public RaidModule(JavaPlugin plugin, RewardLedger ledger, CustomMobManager combat) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.combat = combat;
        tag = new NamespacedKey(plugin, "raid_entity");
        File file = new File(plugin.getDataFolder(), "raid.yml");
        if (!file.exists()) plugin.saveResource("raid.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        normalHP = config.getLong("mobs.pv", 500);
        configuredBossHP = config.getLong("boss.pv", 10000000000L);
        threshold = config.getInt("boss.tous-les-mobs", 100);
        cap = config.getInt("mobs.maximum", 12);
        normalDamage = config.getDouble("mobs.degats", 4);
        bossDamage = config.getDouble("boss.degats", 12);
        bossRange = config.getDouble("boss.portee", 6);
        if (normalHP < 1
                || configuredBossHP < 1
                || threshold < 1
                || threshold > 1000000
                || cap < 1
                || cap > 60
                || !Double.isFinite(normalDamage)
                || normalDamage < 0
                || normalDamage > 2048
                || !Double.isFinite(bossDamage)
                || bossDamage < 0
                || bossDamage > 2048
                || !Double.isFinite(bossRange)
                || bossRange < 1
                || bossRange > 30)
            throw new IllegalArgumentException("Valeurs de raid hors limites");
        normalReward = rewards("mobs.recompenses");
        bossReward = rewards("boss.recompenses");
    }

    public void start() {
        world = ActivityWorlds.create(config.getString("monde", "clickhunter_raid"), true);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        bar = Bukkit.createBossBar("Raid", BarColor.RED, BarStyle.SEGMENTED_10);
        cleanup();
        Objects.requireNonNull(plugin.getCommand("raid")).setExecutor(this);
        plugin.getCommand("raid").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 40);
    }

    private Location spawn() {
        return new Location(world, 0.5, 65, 24.5, 180, 0);
    }

    private void message(Player p, String key) {
        p.sendMessage(DailySettings.color(config.getString("messages." + key, key)));
    }

    private Gains rewards(String path) {
        return Gains.of(
                Math.max(0, config.getLong(path + ".ecus")),
                Math.max(0, config.getLong(path + ".gemmes-sombres")),
                Math.max(0, config.getLong(path + ".cendres")),
                Math.max(0, config.getLong(path + ".xp")));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.hasPermission("cliker.raid")) {
            message(p, "interdit");
            return true;
        }
        if (args.length > 0) {
            if (args.length == 1
                    && List.of("quitter", "sortir").contains(args[0].toLowerCase(Locale.ROOT))) {
                if (p.getWorld() == world) leave(p);
                else message(p, "absent");
            } else message(p, "aide");
            return true;
        }
        if (p.getWorld() == world) return true;
        returns.put(p.getUniqueId(), p.getLocation().clone());
        if (p.teleport(spawn())) {
            message(p, "entree");
            bar.addPlayer(p);
        }
        return true;
    }

    private void leave(Player p) {
        Location target = returns.remove(p.getUniqueId());
        if (target == null || target.getWorld() == null || target.getWorld() == world)
            target =
                    Bukkit.getWorlds().stream()
                            .filter(w -> w != world)
                            .findFirst()
                            .orElseThrow()
                            .getSpawnLocation();
        p.teleport(target);
        bar.removePlayer(p);
    }

    private void tick() {
        if (stopped) return;
        List<Player> players =
                world.getPlayers().stream()
                        .filter(
                                p ->
                                        p.getGameMode() != GameMode.SPECTATOR
                                                && p.isOnline()
                                                && !p.isDead())
                        .toList();
        for (Player p : world.getPlayers()) {
            bar.addPlayer(p);
            if (p.getY() < 50 || Math.abs(p.getX()) > 31 || Math.abs(p.getZ()) > 31)
                p.teleport(spawn());
        }
        for (Player p : new ArrayList<>(bar.getPlayers()))
            if (p.getWorld() != world) bar.removePlayer(p);
        normals.removeIf(
                id -> {
                    var e = Bukkit.getEntity(id);
                    if (e == null || e.isDead()) {
                        health.remove(id);
                        return true;
                    }
                    return false;
                });
        if (players.isEmpty()) return;
        if (boss != null) {
            var entity = Bukkit.getEntity(boss);
            if (entity == null || entity.isDead()) {
                boss = null;
                damage.clear();
                health.clear();
                return;
            }
            long hp = health.getOrDefault(boss, bossMax);
            bar.setTitle("Gardien colossal • " + Numbers.compact(hp) + " PV");
            bar.setProgress(Math.max(0, Math.min(1, (double) hp / bossMax)));
            Player nearest =
                    players.stream()
                            .min(
                                    Comparator.comparingDouble(
                                            p ->
                                                    p.getLocation()
                                                            .distanceSquared(entity.getLocation())))
                            .orElseThrow();
            var direction =
                    nearest.getLocation()
                            .toVector()
                            .subtract(entity.getLocation().toVector())
                            .setY(0);
            double distance = direction.length();
            if (distance > 2) {
                direction.normalize().multiply(Math.min(2, distance - 2));
                entity.teleport(entity.getLocation().add(direction));
            }
            double radius = bossRange;
            for (Player p : players)
                if (p.getLocation().distanceSquared(entity.getLocation()) <= radius * radius) {
                    p.damage(bossDamage, entity);
                    p.spawnParticle(
                            Particle.FLAME, p.getLocation().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0);
                }
            return;
        }

        bar.setTitle("Raid • " + kills + "/" + threshold + " éliminations avant le boss");
        bar.setProgress(Math.min(1, (double) kills / threshold));
        if (kills >= threshold) {
            spawnBoss();
            return;
        }

        while (normals.size() < cap) {
            double x = java.util.concurrent.ThreadLocalRandom.current().nextDouble(-22, 22),
                    z = java.util.concurrent.ThreadLocalRandom.current().nextDouble(-20, 12);
            var mob = world.spawn(new Location(world, x, 65, z), Zombie.class);
            mob.setAdult();
            mob.setShouldBurnInDay(false);
            mob.setRemoveWhenFarAway(false);
            mob.setPersistent(false);
            mob.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            mob.customName(DailySettings.color("&cPillard du raid"));
            mob.setCustomNameVisible(true);
            if (mob.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(normalDamage);
            mob.setTarget(
                    players.get(
                            java.util.concurrent.ThreadLocalRandom.current()
                                    .nextInt(players.size())));
            normals.add(mob.getUniqueId());
            health.put(mob.getUniqueId(), normalHP);
        }
    }

    private void spawnBoss() {
        for (UUID id : normals) {
            var e = Bukkit.getEntity(id);
            if (e != null) e.remove();
            health.remove(id);
        }
        normals.clear();
        var giant = world.spawn(new Location(world, 0.5, 65, 0.5), Giant.class);
        giant.setAI(false);
        giant.setPersistent(false);
        giant.setRemoveWhenFarAway(false);
        giant.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 2);
        giant.customName(DailySettings.color("&4&lGardien colossal"));
        giant.setCustomNameVisible(true);
        boss = giant.getUniqueId();
        bossMax = configuredBossHP;
        health.put(boss, bossMax);
        damage.clear();
        names.clear();
        world.getPlayers().forEach(p -> message(p, "boss"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent e) {
        if (e.getEntity().getWorld() != world) return;
        Entity attacker = e.getDamager();
        if (attacker instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) attacker = shooter;
        if (e.getEntity() instanceof Player && attacker instanceof Player) {
            e.setCancelled(true);
            return;
        }
        UUID id = e.getEntity().getUniqueId();
        if (!health.containsKey(id)) return;
        e.setCancelled(true);
        if (!(attacker instanceof Player p)
                || !(e.getDamager() instanceof Player)
                || p.getWorld() != world
                || p.getGameMode() == GameMode.SPECTATOR) return;
        Long power = combat.calculateDamage(p, MobTier.VACHE);
        if (power == null) {
            message(p, "arme");
            return;
        }
        long before = health.get(id), actual = Math.min(before, Math.max(0, power));
        long after = before - actual;
        health.put(id, after);
        if (e.getEntity() instanceof LivingEntity living) combat.showDamage(p, living, actual);
        if (id.equals(boss)) {
            damage.merge(p.getUniqueId(), actual, Math::addExact);
            names.put(p.getUniqueId(), p.getName());
        }
        if (after > 0) return;
        health.remove(id);
        e.getEntity().remove();
        if (id.equals(boss)) {
            UUID source = boss;
            boss = null;
            kills = 0;
            payBoss(source, new LinkedHashMap<>(damage), new HashMap<>(names));
            damage.clear();
            names.clear();
        } else {
            normals.remove(id);
            kills++;
            ledger.grant("raid-mob:" + id, p.getUniqueId(), p.getName(), normalReward)
                    .whenComplete(
                            (paid, error) ->
                                    onMain(
                                            () -> {
                                                if (error != null) failure(error);
                                                else if (Boolean.TRUE.equals(paid))
                                                    ledger.flushXP(p.getUniqueId());
                                            }));
        }
    }

    private void payBoss(UUID source, Map<UUID, Long> contribution, Map<UUID, String> players) {
        var shares = ProportionalRewards.allocate(bossReward, contribution);
        ledger.database()
                .submit(
                        c -> {
                            try {
                                c.setAutoCommit(false);
                                for (var share : shares.entrySet())
                                    ledger.grant(
                                            c,
                                            "raid-boss:" + source + ":" + share.getKey(),
                                            share.getKey(),
                                            players.getOrDefault(share.getKey(), "Joueur"),
                                            share.getValue());
                                c.commit();
                                return true;
                            } catch (SQLException ex) {
                                try {
                                    c.rollback();
                                } catch (SQLException ignored) {
                                }
                                throw new DatabaseManager.DatabaseException(ex);
                            }
                        })
                .whenComplete(
                        (paid, error) ->
                                onMain(
                                        () -> {
                                            if (error != null) {
                                                failure(error);
                                                return;
                                            }
                                            shares.forEach(
                                                    (id, gain) -> {
                                                        ledger.flushXP(id);
                                                        Player p = Bukkit.getPlayer(id);
                                                        if (p != null) {
                                                            message(p, "victoire");
                                                            for (String line : HuntUI.gains(gain))
                                                                p.sendMessage(
                                                                        DailySettings.color(line));
                                                        }
                                                    });
                                        }));
    }

    private void failure(Throwable error) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Paiement raid impossible", error);
    }

    private void onMain(Runnable action) {
        if (!stopped && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent e) {
        if (e.getEntity().getWorld() == world
                && e.getEntity().getPersistentDataContainer().has(tag)
                && !(e instanceof EntityDamageByEntityEvent)) e.setCancelled(true);
    }

    @EventHandler
    public void death(PlayerDeathEvent e) {
        if (e.getEntity().getWorld() == world) {
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent e) {
        if (e.getPlayer().getWorld() == world) e.setRespawnLocation(spawn());
    }

    @EventHandler
    public void changed(PlayerChangedWorldEvent e) {
        if (e.getFrom() == world) {
            bar.removePlayer(e.getPlayer());
            returns.remove(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        bar.removePlayer(e.getPlayer());
        returns.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void chunk(org.bukkit.event.world.ChunkLoadEvent e) {
        if (e.getWorld() == world)
            for (var entity : e.getChunk().getEntities())
                if (entity.getPersistentDataContainer().has(tag)
                        && !health.containsKey(entity.getUniqueId())) entity.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (e.getBlock().getWorld() == world) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) {
        if (e.getBlock().getWorld() == world) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void bucket(PlayerBucketEmptyEvent e) {
        if (e.getPlayer().getWorld() == world) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getPlayer().getWorld() == world
                && e.getClickedBlock() != null
                && e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)
            e.setCancelled(true);
    }

    @EventHandler
    public void explosion(EntityExplodeEvent e) {
        if (e.getLocation().getWorld() == world) e.blockList().clear();
    }

    private void cleanup() {
        for (var entity : world.getEntities())
            if (entity.getPersistentDataContainer().has(tag)) entity.remove();
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? List.of("quitter") : List.of();
    }

    public void shutdown() {
        stopped = true;
        if (task != null) task.cancel();
        if (bar != null) bar.removeAll();
        if (world != null) cleanup();
        health.clear();
        HandlerList.unregisterAll(this);
    }
}
