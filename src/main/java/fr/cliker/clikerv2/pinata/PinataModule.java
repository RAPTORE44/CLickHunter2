package fr.cliker.clikerv2.pinata;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.hologram.SharedHolograms;
import fr.cliker.clikerv2.hunting.*;
import fr.cliker.clikerv2.runtime.*;

import net.kyori.adventure.text.event.ClickEvent;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** Événement partagé : la santé du mob et la puissance des armes ne modifient pas les gains. */
public final class PinataModule implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final Settings settings;
    private final RewardLedger ledger;
    private final PinataStore store;
    private final NamespacedKey marker;
    private final Map<UUID, Long> lastHit = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>(), notifying = new HashSet<>();
    private final long minimum, maximum, duration, hitInterval;
    private Map<UUID, Long> totals = new HashMap<>();
    private long health, maxHealth;
    private Strider entity;
    private Location point;
    private org.bukkit.Chunk heldChunk;
    private String round;
    private String rewardType;
    private long until, next, shownSecond = -1;
    private boolean ready, spawning, stopped;
    private BukkitTask task;

    public PinataModule(JavaPlugin plugin, DatabaseManager db, RewardLedger ledger) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.store = new PinataStore(db, ledger);
        settings = new Settings(plugin, "activites/pinata.yml");
        marker = new NamespacedKey(plugin, "pinata");
        minimum = bounded("intervalle-min-secondes", 1200, 1, 86400);
        maximum = bounded("intervalle-max-secondes", 1800, minimum, 86400);
        duration = bounded("duree-secondes", 180, 1, 3600);
        hitInterval = bounded("intervalle-coups-millisecondes", 400, 100, 10000) * 1_000_000L;
        for (String type : List.of("ecus", "gemmes", "xp")) {
            long min = settings.yaml.getLong("recompenses." + type + ".min", 1),
                    max = settings.yaml.getLong("recompenses." + type + ".max", 1);
            if (min < 1 || max < min || max > 1_000_000)
                throw new IllegalArgumentException("Récompense piñata invalide : " + type);
        }
    }

    private long bounded(String key, long fallback, long min, long max) {
        long value = settings.yaml.getLong(key, fallback);
        if (value < min || value > max)
            throw new IllegalArgumentException("Paramètre piñata invalide : " + key);
        return value;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        var command = Objects.requireNonNull(plugin.getCommand("pinata"));
        command.setExecutor(this);
        command.setTabCompleter(this);
        for (World world : Bukkit.getWorlds()) for (Entity e : world.getEntities()) clean(e);
        next = settings.yaml.getLong("prochaine-apparition", 0);
        if (next <= 0) schedule();
        else if (settings.yaml.getBoolean("evenement-en-cours", false)) {
            settings.yaml.set("evenement-en-cours", false);
            settings.save();
        }
        store.initialize()
                .whenComplete(
                        (v, error) ->
                                sync(
                                        () -> {
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                Level.SEVERE,
                                                                "Initialisation piñata impossible",
                                                                error);
                                                return;
                                            }
                                            ready = true;
                                            Bukkit.getOnlinePlayers().forEach(this::notifySummary);
                                        }));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
    }

    private void schedule() {
        next =
                System.currentTimeMillis()
                        + ThreadLocalRandom.current().nextLong(minimum, maximum + 1) * 1000;
        settings.yaml.set("prochaine-apparition", next);
        settings.yaml.set("evenement-en-cours", false);
        settings.save();
    }

    private void tick() {
        if (!ready || !settings.yaml.getBoolean("active", true)) return;
        if (round == null) {
            if (!spawning && System.currentTimeMillis() >= next) spawn(null);
            return;
        }
        if (System.nanoTime() >= until || entity == null || !entity.isValid()) {
            finish();
            return;
        }
        var at = entity.getLocation();
        if (!at.getWorld().equals(point.getWorld()) || at.distanceSquared(point) > .01)
            entity.teleport(point);
        if (entity.getVelocity().lengthSquared() != 0) entity.setVelocity(new Vector());
        long seconds = Math.max(0, (until - System.nanoTime() + 999_999_999L) / 1_000_000_000L);
        if (seconds != shownSecond) {
            shownSecond = seconds;
            String title =
                    settings.yaml
                            .getString("messages.titre", "{couleur}&lPiñata {type}")
                            .replace(
                                    "{couleur}",
                                    "ecus".equals(rewardType)
                                            ? "&e"
                                            : "xp".equals(rewardType) ? "&b" : "&5")
                            .replace("{type}", rewardLabel())
                            .replaceAll("\\{secondes\\}\\s*s?", "");
            var holos = SharedHolograms.service();
            if (holos != null && holos.available()) {
                var holo = holos.get("pinata");
                if (holo == null)
                    holos.create("pinata", point.clone().add(0, 2, 0), List.of(title), null, false);
                else holo.setLines(List.of(title));
                entity.setCustomNameVisible(false);
                entity.customName(null);
            } else {
                entity.customName(DailySettings.color(title));
                entity.setCustomNameVisible(true);
            }
        }
    }

    public boolean spawn(CommandSender sender) {
        if (!ready || stopped) {
            if (sender != null) sender.sendMessage("La piñata est en cours de chargement.");
            return false;
        }
        if (round != null || spawning) {
            if (sender != null) message(sender, "deja-active");
            return false;
        }
        Location location = fr.cliker.clikerv2.config.YamlFiles.location(settings.yaml, "point");
        if (location == null) {
            if (sender != null) message(sender, "point-manquant");
            return false;
        }
        if (!settings.yaml.getBoolean("active", true)) {
            if (sender != null)
                sender.sendMessage("La piñata est désactivée dans activites/pinata.yml.");
            return false;
        }
        spawning = true;
        String id = UUID.randomUUID().toString();
        String selectedType =
                List.of("ecus", "xp", "gemmes").get(ThreadLocalRandom.current().nextInt(3));
        store.begin(id, selectedType)
                .whenComplete(
                        (v, error) ->
                                sync(
                                        () -> {
                                            spawning = false;
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                Level.SEVERE,
                                                                "Apparition piñata impossible",
                                                                error);
                                                if (sender != null)
                                                    sender.sendMessage(
                                                            "Apparition impossible. Consulte la"
                                                                    + " console.");
                                                schedule();
                                                return;
                                            }
                                            try {
                                                point = location;
                                                heldChunk = point.getChunk();
                                                heldChunk.addPluginChunkTicket(plugin);
                                                entity =
                                                        point.getWorld()
                                                                .spawn(
                                                                        point,
                                                                        Strider.class,
                                                                        CreatureSpawnEvent
                                                                                .SpawnReason.CUSTOM,
                                                                        s -> {
                                                                            s.getPersistentDataContainer()
                                                                                    .set(
                                                                                            marker,
                                                                                            PersistentDataType
                                                                                                    .BYTE,
                                                                                            (byte)
                                                                                                    1);
                                                                            s.setAI(false);
                                                                            s.setGravity(false);
                                                                            s.setSilent(true);
                                                                            s.setCollidable(false);
                                                                            s.setPersistent(false);
                                                                            s.setRemoveWhenFarAway(
                                                                                    false);
                                                                            s.setShivering(false);
                                                                        });
                                                if (!entity.isValid())
                                                    throw new IllegalStateException(
                                                            "Apparition annulée");
                                                totals = new HashMap<>();
                                                maxHealth =
                                                        bounded("points-vie", 10000, 1, 1000000000);
                                                health = maxHealth;
                                                rewardType = selectedType;
                                                round = id;
                                                until =
                                                        System.nanoTime()
                                                                + duration * 1_000_000_000L;
                                                shownSecond = -1;
                                                next =
                                                        System.currentTimeMillis()
                                                                + ThreadLocalRandom.current()
                                                                                .nextLong(
                                                                                        minimum,
                                                                                        maximum + 1)
                                                                        * 1000;
                                                settings.yaml.set("prochaine-apparition", next);
                                                settings.yaml.set("evenement-en-cours", true);
                                                settings.save();
                                                Bukkit.broadcast(
                                                        DailySettings.color(
                                                                        text("apparition")
                                                                                .replace(
                                                                                        "{type}",
                                                                                        rewardColor()
                                                                                                + rewardLabel()))
                                                                .clickEvent(
                                                                        ClickEvent.runCommand(
                                                                                "/pinata tp")));
                                                tick();
                                            } catch (RuntimeException failure) {
                                                plugin.getLogger()
                                                        .log(
                                                                Level.SEVERE,
                                                                "Création de l’arpenteur"
                                                                        + " impossible",
                                                                failure);
                                                round = id;
                                                finish();
                                            }
                                        }));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(marker, PersistentDataType.BYTE))
            return;
        event.setCancelled(true);
        if (!(event instanceof EntityDamageByEntityEvent attack)
                || !(attack.getDamager() instanceof Player player)) return;
        if (round == null
                || entity == null
                || !entity.getUniqueId().equals(event.getEntity().getUniqueId())
                || System.nanoTime() >= until) return;
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)
            return;
        if (!player.getWorld().equals(point.getWorld())
                || player.getLocation().distanceSquared(point) > 36
                || !player.hasLineOfSight(entity)) return;
        var claims =
                Bukkit.getServicesManager().load(fr.cliker.clikerv2.rewards.ClaimService.class);
        if ((claims != null && claims.isDelivering(player.getUniqueId()))
                || fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)) return;
        UUID owner = player.getUniqueId();
        long now = System.nanoTime();
        if (now - lastHit.getOrDefault(owner, now - hitInterval) < hitInterval
                || !pending.add(owner)) return;
        lastHit.put(owner, now);
        String type = rewardType;
        long damage = Math.max(1, Math.min(1000, (long) Math.ceil(event.getFinalDamage())));
        String hitRound = round;
        Map<UUID, Long> hitTotals = totals;
        long amount =
                ThreadLocalRandom.current()
                        .nextLong(
                                settings.yaml.getLong("recompenses." + type + ".min"),
                                settings.yaml.getLong("recompenses." + type + ".max") + 1);
        Gains gains =
                Gains.of(
                        type.equals("ecus") ? amount : 0,
                        type.equals("gemmes") ? amount * 10 : 0,
                        0,
                        type.equals("xp") ? amount : 0);
        store.hit(round, UUID.randomUUID().toString(), owner, player.getName(), gains)
                .whenComplete(
                        (paid, error) ->
                                sync(
                                        () -> {
                                            pending.remove(owner);
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                Level.SEVERE,
                                                                "Gain piñata non validé pour "
                                                                        + owner,
                                                                error);
                                                return;
                                            }
                                            if (Boolean.TRUE.equals(paid)) {
                                                if (Objects.equals(round, hitRound)
                                                        && entity != null
                                                        && entity.isValid()) {
                                                    var burst = entity.getLocation().add(0, 1, 0);
                                                    burst.getWorld()
                                                            .spawnParticle(
                                                                    Particle.ITEM,
                                                                    burst,
                                                                    12,
                                                                    .35,
                                                                    .4,
                                                                    .35,
                                                                    .15,
                                                                    new org.bukkit.inventory
                                                                            .ItemStack(
                                                                            Material.EMERALD));
                                                    burst.getWorld()
                                                            .playSound(
                                                                    burst,
                                                                    Sound.BLOCK_NOTE_BLOCK_PLING,
                                                                    .5f,
                                                                    1.4f);
                                                    health = Math.max(0, health - damage);
                                                    entity.playHurtAnimation(player.getYaw());
                                                    shownSecond = -1;
                                                    if (health == 0) finish();
                                                    else tick();
                                                }
                                                long total =
                                                        hitTotals.merge(
                                                                owner, amount, Math::addExact);
                                                if (gains.xp() > 0) ledger.flushXP(owner);
                                                if (PlayerSessions.isCurrent(player)) {
                                                    player.sendActionBar(
                                                            DailySettings.color(
                                                                    rewardColor()
                                                                            + settings.yaml
                                                                                    .getString(
                                                                                            "messages.gain-total",
                                                                                            "&a+{montant}"
                                                                                                + " {type}"
                                                                                                + " &8| &fTotal"
                                                                                                + " : &e{total}")
                                                                                    .replaceAll(
                                                                                            "(?i)&[0-9a-f]",
                                                                                            "")
                                                                                    .replaceAll(
                                                                                            "\\{secondes\\}\\s*s?",
                                                                                            "")
                                                                                    .replace(
                                                                                            "{total}",
                                                                                            Long
                                                                                                    .toString(
                                                                                                            total))
                                                                                    .replace(
                                                                                            "{montant}",
                                                                                            Long
                                                                                                    .toString(
                                                                                                            amount))
                                                                                    .replace(
                                                                                            "{type}",
                                                                                            type
                                                                                                            .equals(
                                                                                                                    "ecus")
                                                                                                    ? "Écus"
                                                                                                    : type
                                                                                                                    .equals(
                                                                                                                            "xp")
                                                                                                            ? "XP"
                                                                                                            : "Gemmes"
                                                                                                                  + " Sombres")));
                                                }
                                            }
                                        }));
    }

    private void finish() {
        String id = round;
        round = null;
        if (entity != null) {
            entity.remove();
            entity = null;
        }
        if (heldChunk != null) {
            heldChunk.removePluginChunkTicket(plugin);
            heldChunk = null;
        }
        var holos = SharedHolograms.service();
        if (holos != null) holos.remove("pinata");
        lastHit.clear();
        if (next <= System.currentTimeMillis()) schedule();
        else {
            settings.yaml.set("evenement-en-cours", false);
            settings.save();
        }
        if (id != null)
            store.finish(id)
                    .whenComplete(
                            (v, error) ->
                                    sync(
                                            () -> {
                                                if (error != null) {
                                                    plugin.getLogger()
                                                            .log(
                                                                    Level.SEVERE,
                                                                    "Clôture piñata impossible ;"
                                                                        + " reprise au redémarrage",
                                                                    error);
                                                    return;
                                                }
                                                Bukkit.broadcast(DailySettings.color(text("fin")));
                                                Bukkit.getOnlinePlayers()
                                                        .forEach(this::notifySummary);
                                            }));
    }

    private void notifySummary(Player player) {
        UUID owner = player.getUniqueId();
        if (!ready || !notifying.add(owner)) return;
        store.pending(owner)
                .whenComplete(
                        (summaries, error) ->
                                sync(
                                        () -> {
                                            notifying.remove(owner);
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                Level.SEVERE,
                                                                "Récapitulatif piñata indisponible",
                                                                error);
                                                return;
                                            }
                                            if (!PlayerSessions.isCurrent(player)) {
                                                var current = Bukkit.getPlayer(owner);
                                                if (current != null) notifySummary(current);
                                                return;
                                            }
                                            for (var s : summaries) {
                                                player.sendMessage(
                                                        DailySettings.color(
                                                                text("recap")
                                                                        .replace(
                                                                                "{coups}",
                                                                                Long.toString(
                                                                                        s.hits()))
                                                                        .replace(
                                                                                "{ecus}",
                                                                                CurrencyType.ECUS
                                                                                        .format(
                                                                                                s
                                                                                                        .coins()))
                                                                        .replace(
                                                                                "{gemmes}",
                                                                                CurrencyType
                                                                                        .GEMMES_SOMBRES
                                                                                        .format(
                                                                                                s
                                                                                                        .gems()))
                                                                        .replace(
                                                                                "{xp}",
                                                                                Long.toString(
                                                                                        s.xp()))));
                                                store.reported(s.round(), owner)
                                                        .exceptionally(
                                                                failure -> {
                                                                    plugin.getLogger()
                                                                            .log(
                                                                                    Level.WARNING,
                                                                                    "Récapitulatif"
                                                                                        + " piñata"
                                                                                        + " à confirmer",
                                                                                    failure);
                                                                    return null;
                                                                });
                                            }
                                        }));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEntityEvent event) {
        if (event.getRightClicked()
                .getPersistentDataContainer()
                .has(marker, PersistentDataType.BYTE)) event.setCancelled(true);
    }

    @EventHandler
    public void death(EntityDeathEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        notifySummary(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        lastHit.remove(event.getPlayer().getUniqueId());
    }

    private void clean(Entity e) {
        if (e.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)
                && (entity == null || !entity.getUniqueId().equals(e.getUniqueId()))) e.remove();
    }

    @EventHandler
    public void loaded(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::clean);
    }

    private String rewardColor() {
        return "ecus".equals(rewardType) ? "&e" : "xp".equals(rewardType) ? "&b" : "&5";
    }

    private String rewardLabel() {
        return "ecus".equals(rewardType)
                ? "Écus"
                : "xp".equals(rewardType) ? "XP" : "Gemmes Sombres";
    }

    private String text(String key) {
        return settings.yaml
                .getString("messages." + key, key)
                .replaceAll("\\{secondes\\}\\s*s?", "");
    }

    private void message(CommandSender sender, String key) {
        sender.sendMessage(DailySettings.color(text(key)));
    }

    private void sync(Runnable r) {
        if (!stopped)
            ServerTasks.run(
                    plugin,
                    () -> {
                        if (!stopped) r.run();
                    });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("setspawn") || sub.equals("settp") || sub.equals("spawn")) {
            if (!sender.hasPermission("cliker.pinata.admin")) {
                sender.sendMessage("Tu n’as pas la permission.");
                return true;
            }
            if (sub.equals("spawn")) {
                spawn(sender);
                return true;
            }
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Commande réservée aux joueurs.");
                return true;
            }
            if (round != null || spawning) {
                message(sender, "deja-active");
                return true;
            }
            fr.cliker.clikerv2.config.YamlFiles.location(
                    settings.yaml,
                    sub.equals("settp") ? "teleportation" : "point",
                    p.getLocation());
            settings.save();
            message(sender, "point-defini");
            return true;
        }
        if (sub.equals("tp") && sender instanceof Player player) {
            if (round == null
                    || entity == null
                    || !entity.isValid()
                    || System.nanoTime() >= until) {
                message(sender, "absent");
                return true;
            }
            if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)) {
                player.sendMessage("Reviens au /spawn avant de rejoindre la piñata.");
                return true;
            }
            Location arrival =
                    fr.cliker.clikerv2.config.YamlFiles.location(settings.yaml, "teleportation");
            if (arrival == null) arrival = point.clone().add(0, 0, -3);
            arrival = arrival.clone();
            arrival.setYaw(0);
            arrival.setPitch(0);
            if (!fr.cliker.clikerv2.features.TravelAndTools.safeTeleport(player, arrival))
                player.sendMessage(
                        "Téléportation refusée. Termine ton action en cours puis réessaie.");
            return true;
        }
        if (!sub.equals("info")) {
            sender.sendMessage(
                    "/pinata [tp]"
                            + (sender.hasPermission("cliker.pinata.admin")
                                    ? " · /pinata setspawn · /pinata settp · /pinata spawn"
                                    : ""));
            return true;
        }
        if (round == null) message(sender, "absent");
        else
            sender.sendMessage(
                    DailySettings.color("&aPiñata active ! &e[Clique pour la rejoindre]")
                            .clickEvent(ClickEvent.runCommand("/pinata tp")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] args) {
        if (args.length != 1) return List.of();
        var choices = new ArrayList<>(List.of("tp"));
        if (s.hasPermission("cliker.pinata.admin"))
            choices.addAll(List.of("setspawn", "settp", "spawn"));
        return choices.stream()
                .filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        finish();
        stopped = true;
        HandlerList.unregisterAll(this);
    }
}
