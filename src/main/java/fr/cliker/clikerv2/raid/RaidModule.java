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
    private org.bukkit.scheduler.BukkitTask hologramTask;
    private final JavaPlugin plugin;
    private final Map<UUID, fr.cliker.clikerv2.hologram.SharedHolograms.Handle> raidHolos =
            new HashMap<>();
    private final RewardLedger ledger;
    private final CustomMobManager combat;
    private final YamlConfiguration config;
    private final Map<UUID, Long> health = new HashMap<>(), damage = new LinkedHashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> normals = new HashSet<>();
    private final Map<UUID, Location> returns = new HashMap<>();
    private final NamespacedKey tag;
    private long normalHP, configuredBossHP;
    private int threshold, cap;
    private double normalDamage, bossDamage, bossRange;
    private Gains normalReward, bossReward;
    private boolean managed, publicBoss, completed;
    private Runnable finished;
    private UUID publicPartyLeader;
    private Set<UUID> publicPartyHelpers = Set.of();

    public void publicParty(UUID leader, Set<UUID> helpers) {
        if (publicBoss || helpers.size() > 3)
            throw new IllegalArgumentException("Groupe public invalide");
        publicPartyLeader = leader;
        publicPartyHelpers = Set.copyOf(helpers);
    }

    private World world;
    private UUID boss;
    private long bossMax;
    private String lastBossKiller = "inconnu";
    private int lastBossParticipants;

    public Map<String, String> discordInfo() {
        long hp =
                completed
                        ? 0
                        : boss == null
                                ? configuredBossHP
                                : health.getOrDefault(boss, configuredBossHP);
        return Map.of(
                "boss",
                tier.label(),
                "pv",
                Numbers.compact(hp),
                "pv_bruts",
                Long.toString(hp),
                "pv_max",
                Numbers.compact(configuredBossHP),
                "joueur",
                lastBossKiller,
                "participants",
                Integer.toString(lastBossParticipants));
    }

    private int kills;
    private BukkitTask task, movementTask;
    private int rangedCycle;
    private boolean publicBossPending;
    private final java.util.Set<BukkitTask> rangedTasks = new java.util.HashSet<>();
    private MobTier tier = MobTier.VACHE;
    private final Set<UUID> observers = new HashSet<>();
    private BossBar bar;
    private boolean stopped;
    private final Set<Chunk> heldChunks = new HashSet<>();
    private int heldX = Integer.MIN_VALUE, heldZ = Integer.MIN_VALUE;

    private void releaseArena() {
        heldChunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        heldChunks.clear();
        heldX = Integer.MIN_VALUE;
        heldZ = Integer.MIN_VALUE;
    }

    private void pauseVisualTasks() {
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
        if (hologramTask != null) {
            hologramTask.cancel();
            hologramTask = null;
        }
    }

    private void resumeVisualTasks() {
        if (movementTask == null)
            movementTask = Bukkit.getScheduler().runTaskTimer(plugin, this::moveCreatures, 10, 10);
        if (hologramTask == null)
            hologramTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncHolograms, 4, 4);
    }

    private void holdArena() {
        if (!publicBoss || world.getPlayers().isEmpty()) {
            releaseArena();
            return;
        }
        int cx = centre().getBlockX() >> 4, cz = centre().getBlockZ() >> 4;
        if (cx == heldX && cz == heldZ) return;
        heldChunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        heldChunks.clear();
        for (int x = cx - 1; x <= cx + 1; x++)
            for (int z = cz - 1; z <= cz + 1; z++) {
                var chunk = world.getChunkAt(x, z);
                chunk.addPluginChunkTicket(plugin);
                heldChunks.add(chunk);
            }
        heldX = cx;
        heldZ = cz;
    }

    public RaidModule(JavaPlugin plugin, RewardLedger ledger, CustomMobManager combat) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.combat = combat;
        tag = new NamespacedKey(plugin, "raid_entity");
        File file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "raid.yml");
        if (!file.exists()) fr.cliker.clikerv2.config.ConfigFiles.install(plugin, "raid.yml");
        config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        var additions =
                java.util.Map.<String, Object>of(
                        "boss.multiplicateur-pv",
                        1000,
                        "boss-serveur.pv",
                        1000000000L,
                        "boss-serveur.attaques-distance.actives",
                        true,
                        "boss-serveur.attaques-distance.intervalle-secondes",
                        8,
                        "boss-serveur.attaques-distance.rayon",
                        3,
                        "boss-serveur.attaques-distance.degats",
                        8);
        for (var entry : additions.entrySet())
            if (!config.contains(entry.getKey())) {
                config.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        if (changed)
            try {
                config.save(file);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(
                        "Sauvegarde des paramètres de combat impossible", e);
            }
        readRules();
    }

    private void readRules() {
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
        double scale = config.getDouble("boss.echelle", 4),
                speed = config.getDouble("boss.vitesse", 1);
        if (!Double.isFinite(scale)
                || scale < 1
                || scale > 8
                || !Double.isFinite(speed)
                || speed < .1
                || speed > 3)
            throw new IllegalArgumentException("Échelle ou vitesse du boss invalide");
        normalReward = rewards("mobs.recompenses");
        bossReward = rewards("boss.recompenses");
    }

    public static RaidModule managed(
            JavaPlugin plugin,
            RewardLedger ledger,
            CustomMobManager combat,
            String name,
            int difficulty,
            boolean publicBoss,
            Runnable finished) {
        if (difficulty < 1 || difficulty > MobTier.values().length)
            throw new IllegalArgumentException("Difficulté de raid invalide");
        var raid = new RaidModule(plugin, ledger, combat);
        raid.managed = true;
        raid.publicBoss = publicBoss;
        if (publicBoss) {
            raid.config.set("boss.pv", raid.config.getLong("boss-serveur.pv", 1000000000L));
            raid.readRules();
        }
        var setup = Bukkit.getServicesManager().load(RaidSetup.class);
        if (publicBoss && setup != null) {
            setup.applyBossParameters(raid.config);
            raid.readRules();
        }
        raid.tier =
                MobTier.values()[
                        Math.max(0, Math.min(MobTier.values().length - 1, difficulty - 1))];
        raid.finished = finished;
        raid.config.set("monde", name);
        long scale = Math.multiplyExact((long) difficulty, difficulty);
        raid.normalHP = Math.multiplyExact(raid.normalHP, scale);
        if (!publicBoss)
            raid.configuredBossHP =
                    Math.multiplyExact(
                            raid.normalHP,
                            Math.max(
                                    1,
                                    Math.min(
                                            1000000,
                                            raid.config.getLong("boss.multiplicateur-pv", 1000))));
        raid.normalDamage = Math.min(2048, raid.normalDamage * difficulty);
        if (!publicBoss) raid.bossDamage = Math.min(2048, raid.bossDamage * difficulty);
        raid.normalReward = scaled(raid.normalReward, difficulty);
        raid.bossReward = scaled(raid.bossReward, difficulty);
        return raid;
    }

    private static Gains scaled(Gains g, int n) {
        return Gains.of(
                Math.multiplyExact(g.amount(fr.cliker.clikerv2.economy.CurrencyType.ECUS), n),
                Math.multiplyExact(
                        g.amount(fr.cliker.clikerv2.economy.CurrencyType.GEMMES_SOMBRES), n),
                Math.multiplyExact(g.amount(fr.cliker.clikerv2.economy.CurrencyType.CENDRES), n),
                Math.multiplyExact(g.xp(), n));
    }

    EntityType creatureType() {
        return tier.entityType();
    }

    public World world() {
        return world;
    }

    public void enter(Player p) {
        onCommand(p, plugin.getCommand("raid"), "raid", new String[0]);
    }

    public void exit(Player p) {
        if (p.getWorld() == world) leave(p);
    }

    public void beginPublicBoss() {
        if (publicBoss && boss == null) {
            completed = false;
            publicBossPending = true;
            if (!world.getPlayers().isEmpty()) spawnBoss();
        }
    }

    public void evacuate() {
        for (Player p : new ArrayList<>(world.getPlayers())) leave(p);
    }

    public void start() {
        world = ActivityWorlds.create(config.getString("monde", "clickhunter_raid"), true);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        holdArena();
        bar =
                Bukkit.createBossBar(
                        publicBoss ? "Boss serveur : en attente" : "Raid",
                        BarColor.RED,
                        BarStyle.SEGMENTED_10);
        cleanup();
        if (!managed) {
            Objects.requireNonNull(plugin.getCommand("raid")).setExecutor(this);
            plugin.getCommand("raid").setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 40);
        if (!world.getPlayers().isEmpty()) resumeVisualTasks();
    }

    private RaidSetup setup() {
        return Bukkit.getServicesManager().load(RaidSetup.class);
    }

    private Location point(String kind, Location fallback) {
        return setup() == null ? fallback : setup().point(world, kind, fallback);
    }

    private Location centre() {
        return point("centre", new Location(world, .5, 65, .5));
    }

    private Location spawn() {
        return point("entree", new Location(world, .5, 65, 24.5, 180, 0));
    }

    public void observe(Player player) {
        if (!player.hasPermission(publicBoss ? "cliker.boss.admin" : "cliker.raid.admin")) return;
        var previous = player.getLocation().clone();
        if (player.teleport(point("observation", spawn()))) {
            returns.putIfAbsent(player.getUniqueId(), previous);
            observers.add(player.getUniqueId());
            player.sendMessage(
                    "§bObservation • /"
                            + (publicBoss ? "boss" : "raid")
                            + " quitter pour repartir. Les observateurs ne participent pas au"
                            + " combat.");
        }
    }

    private List<Player> fighters() {
        return world.getPlayers().stream()
                .filter(
                        p ->
                                !observers.contains(p.getUniqueId())
                                        && p.getGameMode() != GameMode.SPECTATOR
                                        && p.getGameMode() != GameMode.CREATIVE
                                        && p.isOnline()
                                        && !p.isDead())
                .toList();
    }

    private void moveCreatures() {
        if (stopped || completed) return;
        var players = fighters();
        if (players.isEmpty()) return;
        var origin = centre();
        double limit = (setup() == null ? 31 : setup().radius(world)) + 3;
        for (UUID id : health.keySet()) {
            if (!(Bukkit.getEntity(id) instanceof LivingEntity mob) || mob.isDead()) continue;
            if (mob.getLocation().distanceSquared(origin) > limit * limit) {
                mob.teleport(origin);
                continue;
            }
            var nearest =
                    players.stream()
                            .min(
                                    Comparator.comparingDouble(
                                            p ->
                                                    p.getLocation()
                                                            .distanceSquared(mob.getLocation())))
                            .orElseThrow();
            var direction =
                    nearest.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0);
            if (direction.lengthSquared() > 2.25) {
                double speed =
                        id.equals(boss)
                                ? Math.max(.1, Math.min(3, config.getDouble("boss.vitesse", 1)))
                                        * .12
                                : .16;
                direction.normalize().multiply(speed);
                direction.setY(mob.getVelocity().getY());
                if (mob instanceof org.bukkit.entity.Mob pathMob) {
                    pathMob.getPathfinder()
                            .moveTo(
                                    nearest,
                                    id.equals(boss)
                                            ? Math.max(
                                                    .1,
                                                    Math.min(
                                                            3, config.getDouble("boss.vitesse", 1)))
                                            : 1.1);
                }
                mob.setVelocity(direction);
                var face =
                        mob.getLocation()
                                .setDirection(
                                        nearest.getLocation()
                                                .toVector()
                                                .subtract(mob.getLocation().toVector()));
                mob.setRotation(face.getYaw(), 0);
            } else mob.setVelocity(new org.bukkit.util.Vector(0, mob.getVelocity().getY(), 0));
        }
    }

    /**
     * Trajectoire visible et zone annoncée une seconde avant l’impact, sans entités persistantes.
     */
    private void rangedAttack(org.bukkit.entity.Entity source, List<Player> players) {
        if (players.isEmpty()) return;
        Player target =
                players.get(
                        java.util.concurrent.ThreadLocalRandom.current().nextInt(players.size()));
        Location aim = target.getLocation().clone(),
                start = source.getLocation().add(0, Math.min(6, source.getHeight()), 0);
        double radius =
                Math.max(
                        1,
                        Math.min(8, config.getDouble("boss-serveur.attaques-distance.rayon", 3)));
        double power =
                Math.max(
                        0,
                        Math.min(
                                2048,
                                config.getDouble("boss-serveur.attaques-distance.degats", 8)));
        UUID attacker = source.getUniqueId();
        target.sendActionBar(
                fr.cliker.clikerv2.daily.DailySettings.color(
                        "&c☄ Attaque du boss : esquive la zone rouge !"));
        var timer =
                new org.bukkit.scheduler.BukkitRunnable() {
                    int frame;

                    public void run() {
                        if (stopped || completed || !attacker.equals(boss) || !source.isValid()) {
                            rangedTasks.remove(getTask());
                            cancel();
                            return;
                        }
                        for (int i = 0; i < 24; i++) {
                            double angle = i * Math.PI / 12;
                            world.spawnParticle(
                                    Particle.DUST,
                                    aim.clone()
                                            .add(
                                                    Math.cos(angle) * radius,
                                                    .15,
                                                    Math.sin(angle) * radius),
                                    1,
                                    0,
                                    0,
                                    0,
                                    0,
                                    new Particle.DustOptions(org.bukkit.Color.RED, 1.4f));
                        }
                        var point =
                                start.clone()
                                        .add(
                                                aim.toVector()
                                                        .subtract(start.toVector())
                                                        .multiply(Math.min(1, frame / 10.0)));
                        world.spawnParticle(Particle.FLAME, point, 8, .12, .12, .12, .01);
                        if (++frame > 10) {
                            world.spawnParticle(Particle.EXPLOSION, aim.clone().add(0, .5, 0), 1);
                            for (Player p : fighters())
                                if (p.getLocation().distanceSquared(aim) <= radius * radius)
                                    p.damage(power, source);
                            rangedTasks.remove(getTask());
                            cancel();
                        }
                    }

                    private BukkitTask getTask() {
                        return rangedTasks.stream()
                                .filter(t -> t.getTaskId() == getTaskId())
                                .findFirst()
                                .orElse(null);
                    }
                };
        rangedTasks.add(timer.runTaskTimer(plugin, 0, 2));
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
        if (!publicBoss && !p.hasPermission("cliker.raid")) {
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
        var previous = p.getLocation().clone();
        if (p.teleport(spawn())) {
            returns.put(p.getUniqueId(), previous);
            message(p, "entree");
            bar.addPlayer(p);
        }
        return true;
    }

    private void leave(Player p) {
        Location target = setup() == null ? null : setup().exit(world);
        Location previous = returns.get(p.getUniqueId());
        if (target == null) target = previous;
        if (target == null || target.getWorld() == null || target.getWorld() == world)
            target =
                    Bukkit.getWorlds().stream()
                            .filter(w -> w != world)
                            .findFirst()
                            .orElseThrow()
                            .getSpawnLocation();
        if (!p.teleport(target)) {
            p.sendMessage(
                    "§eLa sortie a été refusée. Tu restes dans ton raid ; réessaie la commande"
                            + " quitter.");
            return;
        }
        returns.remove(p.getUniqueId());
        observers.remove(p.getUniqueId());
        fr.cliker.clikerv2.combat.BossMusic.stop(p);
        bar.removePlayer(p);
    }

    private void syncHolograms() {
        var provider = fr.cliker.clikerv2.hologram.SharedHolograms.service();
        if (provider == null || !provider.available()) return;
        raidHolos
                .entrySet()
                .removeIf(
                        entry -> {
                            if (health.containsKey(entry.getKey())
                                    && Bukkit.getEntity(entry.getKey()) != null) return false;
                            entry.getValue().remove();
                            return true;
                        });
        for (var entry : health.entrySet()) {
            var entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !entity.isValid() || entity.isDead())
                continue;
            var at = entity.getLocation().add(0, entity.getHeight() + .5, 0);
            String label =
                    (entry.getKey().equals(boss) ? "&4&lBoss " + tier.label() : "&c" + tier.label())
                            + " &7— &c"
                            + fr.cliker.clikerv2.Numbers.compact(entry.getValue())
                            + " PV";
            var holo = raidHolos.get(entry.getKey());
            if (holo == null || !holo.isValid())
                raidHolos.put(
                        entry.getKey(),
                        provider.create("raid:" + entry.getKey(), at, List.of(label), null, false));
            else {
                holo.setLocation(at);
                holo.setLines(List.of(label));
            }
            living.setCustomNameVisible(false);
        }
    }

    private void tick() {
        if (stopped || completed) return;
        if (world.getPlayers().isEmpty()) {
            releaseArena();
            pauseVisualTasks();
            return;
        }
        holdArena();
        resumeVisualTasks();
        List<Player> players = fighters();
        if (publicBoss && publicBossPending && boss == null && !players.isEmpty()) spawnBoss();
        for (Player p : world.getPlayers()) {
            bar.addPlayer(p);
            var center = centre();
            double radius = setup() == null ? 31 : setup().radius(world);
            if (!observers.contains(p.getUniqueId())
                    && (p.getY() < center.getY() - 15
                            || Math.abs(p.getX() - center.getX()) > radius
                            || Math.abs(p.getZ() - center.getZ()) > radius)) p.teleport(spawn());
        }
        for (Player p : new ArrayList<>(bar.getPlayers()))
            if (p.getWorld() != world) {
                bar.removePlayer(p);
                fr.cliker.clikerv2.combat.BossMusic.stop(p);
            }
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
            bar.setTitle("Boss " + tier.label() + " • " + Numbers.compact(hp) + " PV");
            bar.setProgress(Math.max(0, Math.min(1, (double) hp / bossMax)));
            for (Player player : players) fr.cliker.clikerv2.combat.BossMusic.play(player);
            if (publicBoss
                    && config.getBoolean("boss-serveur.attaques-distance.actives", true)
                    && ++rangedCycle
                                    % Math.max(
                                            2,
                                            config.getInt(
                                                            "boss-serveur.attaques-distance.intervalle-secondes",
                                                            8)
                                                    / 2)
                            == 0) rangedAttack(entity, players);
            double radius = bossRange;
            for (Player p : players)
                if (p.getLocation().distanceSquared(entity.getLocation()) <= radius * radius) {
                    p.damage(bossDamage, entity);
                    p.spawnParticle(
                            Particle.FLAME, p.getLocation().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0);
                }
            return;
        }

        if (publicBoss) return;
        bar.setTitle("Raid • " + kills + "/" + threshold + " éliminations avant le boss");
        bar.setProgress(Math.min(1, (double) kills / threshold));
        if (kills >= threshold) {
            spawnBoss();
            return;
        }

        for (UUID id : normals) {
            var mob = Bukkit.getEntity(id);
            if (mob == null) continue;
            for (Player player : players)
                if (player.getLocation().distanceSquared(mob.getLocation()) <= 4)
                    player.damage(normalDamage, mob);
        }
        while (normals.size() < cap) {
            double extent = Math.min(22, (setup() == null ? 31 : setup().radius(world)) - 3);
            double x = java.util.concurrent.ThreadLocalRandom.current().nextDouble(-extent, extent),
                    z =
                            java.util.concurrent.ThreadLocalRandom.current()
                                    .nextDouble(-extent, extent * .55);
            var mob = (LivingEntity) world.spawnEntity(centre().add(x, 0, z), creatureType());
            mob.setSilent(true);
            if (mob instanceof Ageable ageable) ageable.setAdult();
            if (mob instanceof Zombie zombie) zombie.setShouldBurnInDay(false);
            if (mob instanceof Slime slime) slime.setSize(2);
            mob.setAI(true);
            if (mob instanceof Mob creature) Bukkit.getMobGoals().removeAllGoals(creature);
            if (mob instanceof PiglinAbstract piglin) piglin.setImmuneToZombification(true);
            if (mob instanceof Hoglin hoglin) hoglin.setImmuneToZombification(true);
            if (mob instanceof Wither wither) wither.setInvulnerabilityTicks(0);
            if (mob instanceof Ghast && mob.getAttribute(Attribute.SCALE) != null)
                mob.getAttribute(Attribute.SCALE).setBaseValue(.5);
            mob.setRemoveWhenFarAway(false);
            mob.setPersistent(false);
            mob.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            mob.customName(DailySettings.color("&c" + tier.label()));
            mob.setCustomNameVisible(
                    fr.cliker.clikerv2.hologram.SharedHolograms.service() == null
                            || !fr.cliker.clikerv2.hologram.SharedHolograms.service().available());
            if (mob.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(normalDamage);
            normals.add(mob.getUniqueId());
            health.put(mob.getUniqueId(), normalHP);
        }
    }

    private void spawnBoss() {
        publicBossPending = false;
        for (UUID id : normals) {
            var e = Bukkit.getEntity(id);
            if (e != null) e.remove();
            health.remove(id);
        }
        normals.clear();
        var giant = (LivingEntity) world.spawnEntity(centre(), creatureType());
        if (giant instanceof Ageable ageable) ageable.setAdult();
        if (giant instanceof Slime slime) slime.setSize(2);
        var scale = giant.getAttribute(Attribute.SCALE);
        if (scale != null)
            scale.setBaseValue(Math.max(1, Math.min(8, config.getDouble("boss.echelle", 4))));
        giant.setAI(true);
        if (giant instanceof Mob creature) Bukkit.getMobGoals().removeAllGoals(creature);
        if (giant instanceof PiglinAbstract piglin) piglin.setImmuneToZombification(true);
        if (giant instanceof Hoglin hoglin) hoglin.setImmuneToZombification(true);
        if (giant instanceof Wither wither) wither.setInvulnerabilityTicks(0);
        giant.setPersistent(false);
        giant.setRemoveWhenFarAway(false);
        giant.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 2);
        giant.customName(DailySettings.color("&4&lBoss " + tier.label()));
        giant.setCustomNameVisible(
                fr.cliker.clikerv2.hologram.SharedHolograms.service() == null
                        || !fr.cliker.clikerv2.hologram.SharedHolograms.service().available());
        giant.setSilent(true);
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
                || !(e.getEntity() instanceof LivingEntity target)
                || !fr.cliker.clikerv2.combat.CombatReach.canHit(p, target)) return;
        if (observers.contains(p.getUniqueId())) return;
        Long power = combat.calculateDamage(p, tier, id.equals(boss));
        if (power == null) {
            message(p, "arme");
            return;
        }
        long before = health.get(id), actual = Math.min(before, Math.max(0, power));
        long after = before - actual;
        health.put(id, after);
        if (e.getEntity() instanceof LivingEntity living) {
            combat.showDamage(p, living, actual);
            if (actual > 0) {
                fr.cliker.clikerv2.combat.PersonalCombatAudio.hurt(p, living);
                p.spawnParticle(
                        Particle.DAMAGE_INDICATOR,
                        living.getLocation().add(0, living.getHeight() * .6, 0),
                        6,
                        .4,
                        .4,
                        .4,
                        .05);
            }
        }
        if (id.equals(boss)) {
            damage.merge(p.getUniqueId(), actual, Math::addExact);
            names.put(p.getUniqueId(), p.getName());
        }
        if (after > 0) return;
        health.remove(id);
        var removedHolo = raidHolos.remove(id);
        if (removedHolo != null) removedHolo.remove();
        e.getEntity().remove();
        if (id.equals(boss)) {
            world.getPlayers().forEach(fr.cliker.clikerv2.combat.BossMusic::stop);
            UUID source = boss;
            lastBossKiller = p.getName();
            lastBossParticipants = damage.size();
            boss = null;
            if (managed) {
                completed = true;
                bar.setTitle(
                        publicBoss ? "Boss vaincu — en attente du prochain seuil" : "Raid terminé");
                bar.setProgress(0);
            }
            kills = 0;
            payBoss(source, new LinkedHashMap<>(damage), new HashMap<>(names));
            damage.clear();
            names.clear();
        } else {
            normals.remove(id);
            kills++;
            ledger.grant(
                            "raid-mob:" + id,
                            p.getUniqueId(),
                            p.getName(),
                            fr.cliker.clikerv2.features.Boosts.rewards(
                                    p,
                                    publicPartyHelpers.contains(p.getUniqueId())
                                            ? PublicRaidRewards.third(normalReward)
                                            : normalReward))
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
        var shares =
                publicPartyLeader == null
                        ? ProportionalRewards.allocate(bossReward, contribution)
                        : PublicRaidRewards.allocate(
                                bossReward, publicPartyLeader, publicPartyHelpers, contribution);
        Map<UUID, Gains> credited = new LinkedHashMap<>();
        shares.forEach(
                (id, gain) ->
                        credited.put(
                                id,
                                fr.cliker.clikerv2.features.Boosts.rewards(
                                        Bukkit.getPlayer(id), gain)));
        Map<UUID, String> scrollRewards = new LinkedHashMap<>();
        if (managed && !publicBoss && config.getBoolean("parchemins.victoire", true))
            for (UUID id : credited.keySet())
                scrollRewards.put(
                        id,
                        fr.cliker.clikerv2.rewards.ClaimService.serialize(
                                RaidScrolls.create(plugin, tier)));
        payShares(
                source,
                credited,
                players,
                scrollRewards,
                contribution,
                (publicBoss ? "serveur:" : "raid:") + tier.id(),
                (publicBoss ? "Boss serveur • " : "Raid • ") + tier.label());
    }

    private void payShares(
            UUID source,
            Map<UUID, Gains> shares,
            Map<UUID, String> players,
            Map<UUID, String> scrollRewards,
            Map<UUID, Long> contribution,
            String bossKey,
            String bossLabel) {
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
                                for (var reward : scrollRewards.entrySet())
                                    fr.cliker.clikerv2.rewards.MailStore.insert(
                                            c,
                                            reward.getKey(),
                                            "raid-victory-scroll:" + source + ":" + reward.getKey(),
                                            reward.getValue());
                                long totalDamage = BossReports.total(contribution);
                                for (var entry : contribution.entrySet())
                                    BossReports.record(
                                            c,
                                            source.toString(),
                                            entry.getKey(),
                                            bossKey,
                                            bossLabel,
                                            entry.getValue(),
                                            totalDamage,
                                            shares.getOrDefault(
                                                    entry.getKey(), Gains.of(0, 0, 0, 0)),
                                            scrollRewards.containsKey(entry.getKey())
                                                    ? "&dParchemin de raid enregistré (inventaire"
                                                            + " ou /claim)."
                                                    : "&7Aucun objet supplémentaire.");
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
                                                Bukkit.getScheduler()
                                                        .runTaskLater(
                                                                plugin,
                                                                () -> {
                                                                    if (!stopped)
                                                                        payShares(
                                                                                source,
                                                                                shares,
                                                                                players,
                                                                                scrollRewards,
                                                                                contribution,
                                                                                bossKey,
                                                                                bossLabel);
                                                                },
                                                                100);
                                                return;
                                            }
                                            shares.forEach(
                                                    (id, gain) -> {
                                                        ledger.flushXP(id);
                                                        Player p = Bukkit.getPlayer(id);
                                                        if (p != null) {
                                                            BossReports.notify(p);
                                                            var claims =
                                                                    Bukkit.getServicesManager()
                                                                            .load(
                                                                                    fr.cliker
                                                                                            .clikerv2
                                                                                            .rewards
                                                                                            .ClaimService
                                                                                            .class);
                                                            if (claims != null) claims.resume(id);
                                                            fr.cliker.clikerv2.community
                                                                    .ToastNotifications.show(
                                                                    p,
                                                                    Material.PAPER,
                                                                    publicBoss
                                                                            ? "Boss serveur vaincu"
                                                                                    + " !"
                                                                            : "Raid remporté !",
                                                                    publicBoss
                                                                            ? "Ta contribution a"
                                                                                  + " été récompensée."
                                                                            : "Récompenses et"
                                                                                  + " parchemin"
                                                                                  + " enregistrés.");
                                                        }
                                                    });
                                            if (managed && finished != null) finished.run();
                                        }));
    }

    private void failure(Throwable error) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Paiement raid impossible", error);
    }

    private void onMain(Runnable action) {
        if (!stopped && plugin.isEnabled())
            fr.cliker.clikerv2.runtime.ServerTasks.run(plugin, action);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player player
                && player.getWorld() == world
                && observers.contains(player.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
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
            observers.remove(e.getPlayer().getUniqueId());
            fr.cliker.clikerv2.combat.BossMusic.stop(e.getPlayer());
            bar.removePlayer(e.getPlayer());
            returns.remove(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        observers.remove(e.getPlayer().getUniqueId());
        fr.cliker.clikerv2.combat.BossMusic.stop(e.getPlayer());
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
        if (movementTask != null) movementTask.cancel();
        rangedTasks.forEach(BukkitTask::cancel);
        rangedTasks.clear();
        if (world != null) world.getPlayers().forEach(fr.cliker.clikerv2.combat.BossMusic::stop);
        observers.clear();
        if (hologramTask != null) hologramTask.cancel();
        raidHolos.values().forEach(fr.cliker.clikerv2.hologram.SharedHolograms.Handle::remove);
        raidHolos.clear();
        heldChunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        heldChunks.clear();
        stopped = true;
        if (task != null) task.cancel();
        if (bar != null) bar.removeAll();
        if (world != null) cleanup();
        health.clear();
        HandlerList.unregisterAll(this);
    }
}
