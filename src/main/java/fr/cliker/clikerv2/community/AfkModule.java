package fr.cliker.clikerv2.community;

import fr.cliker.clikerv2.features.*;
import fr.cliker.clikerv2.lootbox.*;
import fr.cliker.clikerv2.outpost.WorldInventories;
import fr.cliker.clikerv2.rewards.ClaimService;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Un profil AFK vide, sauvegardé avec l'inventaire clicker dans le même playerdata. */
public final class AfkModule implements Listener, CommandExecutor, TabCompleter {
    private static final NamespacedKey SNAPSHOT =
            Objects.requireNonNull(NamespacedKey.fromString("clikerv2:afk_snapshot"));
    private static final Set<UUID> MUTED = ConcurrentHashMap.newKeySet();
    private final JavaPlugin plugin;
    private final CommunityStore store;
    private final ClaimService claims;
    private final Settings settings;
    private final Menus menus;

    private record Clock(String session, long start) {}

    private final Map<UUID, Clock> clocks = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final BukkitTask task;
    private World world;
    private java.util.concurrent.CompletableFuture<World> prepared;
    private final Map<UUID, Object> preparing = new HashMap<>();

    private static final class EmptyWorld extends org.bukkit.generator.ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }

    public AfkModule(JavaPlugin plugin, CommunityStore store, ClaimService claims) {
        this.plugin = plugin;
        this.store = store;
        this.claims = claims;
        settings = new Settings(plugin, "communaute/afk.yml");
        menus = new Menus(plugin);
        plugin.getCommand("afk").setExecutor(this);
        plugin.getCommand("afk").setTabCompleter(this);
        plugin.getCommand("afkshop").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    for (var p : Bukkit.getOnlinePlayers())
                                        if (isolated(p)) {
                                            MUTED.add(p.getUniqueId());
                                            if (inWorld(p)) {
                                                accrue(p);
                                                p.sendActionBar(
                                                        net.kyori.adventure.text.Component.text(
                                                                "⌛ Zone AFK • 10 min = 1 point •"
                                                                    + " /afkshop • /afk quitter"));
                                            } else restore(p);
                                        }
                                },
                                20,
                                400);
        for (var p : Bukkit.getOnlinePlayers()) reconcile(p);
    }

    public static boolean isolated(Player p) {
        return p.getPersistentDataContainer().has(SNAPSHOT, PersistentDataType.STRING);
    }

    public static boolean muted(UUID id) {
        return MUTED.contains(id);
    }

    private boolean inWorld(Player p) {
        return p.getWorld().getName().equals(settings.yaml.getString("monde", "clickhunter_afk"));
    }

    private void sync(Runnable r) {
        if (plugin.isEnabled()) fr.cliker.clikerv2.runtime.ServerTasks.run(plugin, r);
    }

    private void notice(Player p, String text) {
        p.sendActionBar(fr.cliker.clikerv2.daily.DailySettings.color(text));
    }

    private World world() {
        if (world != null) return world;
        String name = settings.yaml.getString("monde", "clickhunter_afk");
        if (!name.matches("[A-Za-z0-9_-]+") || name.equals("world"))
            throw new IllegalStateException("Nom du monde AFK invalide");
        world = Bukkit.getWorld(name);
        if (world == null)
            world =
                    new WorldCreator(name)
                            .generator(new EmptyWorld())
                            .generateStructures(false)
                            .keepSpawnLoaded(net.kyori.adventure.util.TriState.FALSE)
                            .createWorld();
        if (world == null) throw new IllegalStateException("Monde AFK indisponible");
        world.setPVP(false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        fr.cliker.clikerv2.world.PermanentDay.apply(world);
        return world;
    }

    private java.util.concurrent.CompletableFuture<World> prepare() {
        if (prepared != null) return prepared;
        World target = world();
        var ready = new java.util.concurrent.CompletableFuture<World>();
        prepared = ready;
        java.util.List<java.util.concurrent.CompletableFuture<?>> chunks =
                new java.util.ArrayList<>();
        for (int x = -1; x <= 0; x++)
            for (int z = -1; z <= 0; z++) chunks.add(target.getChunkAtAsync(x, z, true));
        java.util.concurrent.CompletableFuture.allOf(
                        chunks.toArray(java.util.concurrent.CompletableFuture[]::new))
                .whenComplete(
                        (ignored, error) ->
                                sync(
                                        () -> {
                                            if (error != null) {
                                                prepared = null;
                                                ready.completeExceptionally(error);
                                                return;
                                            }
                                            try {
                                                var key = new NamespacedKey(plugin, "afk_sphere");
                                                if (!target.getPersistentDataContainer().has(key)) {
                                                    for (int x = -8; x <= 8; x++)
                                                        for (int y = -8; y <= 8; y++)
                                                            for (int z = -8; z <= 8; z++) {
                                                                int r = x * x + y * y + z * z;
                                                                if (r >= 49 && r <= 64)
                                                                    target.getBlockAt(x, y + 73, z)
                                                                            .setType(
                                                                                    Material.GLASS,
                                                                                    false);
                                                            }
                                                    target.getPersistentDataContainer()
                                                            .set(
                                                                    key,
                                                                    PersistentDataType.BYTE,
                                                                    (byte) 1);
                                                }
                                                prepared = null;
                                                ready.complete(target);
                                            } catch (RuntimeException failure) {
                                                prepared = null;
                                                ready.completeExceptionally(failure);
                                            }
                                        }));
        return ready;
    }

    private void enter(Player p) {
        if (isolated(p)) {
            shop(p);
            return;
        }
        if (p.isInsideVehicle()
                || p.isGliding()
                || WorldInventories.isolated(p)
                || claims.isDelivering(p.getUniqueId())
                || p.isDead()
                || p.getGameMode() == GameMode.SPECTATOR) {
            notice(p, "&cReviens au spawn et attends la fin des transferts.");
            return;
        }
        if (!fr.cliker.clikerv2.combat.PersonalCombatAudio.canIsolateChat()) {
            notice(p, "&cIsolation du chat indisponible : vérifie ZNPCsPlus 2.0.0.");
            return;
        }
        if (preparing.containsKey(p.getUniqueId())) {
            notice(p, "&bPréparation de la sphère AFK…");
            return;
        }
        Object request = new Object();
        preparing.put(p.getUniqueId(), request);
        notice(p, "&bPréparation de la sphère AFK…");
        prepare()
                .whenComplete(
                        (target, error) ->
                                sync(
                                        () -> {
                                            if (!preparing.remove(p.getUniqueId(), request)
                                                    || !fr.cliker.clikerv2.runtime.PlayerSessions
                                                            .isCurrent(p)) return;
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.WARNING,
                                                                "Préparation AFK impossible",
                                                                error);
                                                notice(
                                                        p,
                                                        "&cMonde AFK indisponible. Ton équipement"
                                                            + " est conservé.");
                                                return;
                                            }
                                            if (WorldInventories.isolated(p)
                                                    || claims.isDelivering(p.getUniqueId())
                                                    || p.isDead()
                                                    || p.isInsideVehicle()
                                                    || p.getGameMode() == GameMode.SPECTATOR) {
                                                notice(
                                                        p,
                                                        "&cEntrée annulée : ton état a changé"
                                                            + " pendant la préparation.");
                                                return;
                                            }
                                            enterReady(p, target);
                                        }));
    }

    private void enterReady(Player p, World target) {
        var data = new YamlConfiguration();
        data.set("items", Arrays.asList(p.getInventory().getContents()));
        data.set("location", p.getLocation());
        data.set("gamemode", p.getGameMode().name());
        data.set("level", p.getLevel());
        data.set("xp", p.getTotalExperience());
        data.set("progress", p.getExp());
        p.closeInventory();
        p.getPersistentDataContainer()
                .set(SNAPSHOT, PersistentDataType.STRING, data.saveToString());
        p.getInventory().clear();
        p.setLevel(0);
        p.setExp(0);
        p.setTotalExperience(0);
        p.setGameMode(GameMode.ADVENTURE);
        try {
            p.saveData();
        } catch (RuntimeException error) {
            restore(p);
            return;
        }
        MUTED.add(p.getUniqueId());
        if (!p.teleport(new Location(target, 0.5, 66, 0.5, 0, 0))) {
            restore(p);
            return;
        }
        clocks.put(p.getUniqueId(), new Clock(UUID.randomUUID().toString(), System.nanoTime()));
        notice(p, "&bZone AFK &8• &f/afkshop &8• &f/afk quitter");
    }

    private void accrue(Player p) {
        Clock clock = clocks.get(p.getUniqueId());
        if (clock == null) return;
        long seconds = (System.nanoTime() - clock.start) / 1_000_000_000L;
        if (seconds <= 0) return;
        credit(clock.session, p.getUniqueId(), seconds);
    }

    private void credit(String session, UUID owner, long seconds) {
        store.accrueSession(
                        session,
                        owner,
                        seconds,
                        Math.max(60, settings.yaml.getInt("secondes-par-point", 600)))
                .whenComplete(
                        (gain, error) ->
                                sync(
                                        () -> {
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .warning("Crédit AFK différé : " + owner);
                                                Bukkit.getScheduler()
                                                        .runTaskLater(
                                                                plugin,
                                                                () ->
                                                                        credit(
                                                                                session, owner,
                                                                                seconds),
                                                                100);
                                            } else if (gain > 0) {
                                                var p = Bukkit.getPlayer(owner);
                                                if (p != null)
                                                    notice(
                                                            p,
                                                            "&a+"
                                                                    + gain
                                                                    + " point(s) AFK &8•"
                                                                    + " &f/afkshop");
                                            }
                                        }));
    }

    private void restore(Player p) {
        String saved = p.getPersistentDataContainer().get(SNAPSHOT, PersistentDataType.STRING);
        if (saved == null) return;
        try {
            var yaml = new YamlConfiguration();
            yaml.loadFromString(saved);
            var list = yaml.getList("items");
            if (list == null || list.size() != 41)
                throw new IllegalStateException("Snapshot AFK invalide");
            var items = new ItemStack[41];
            for (int i = 0; i < 41; i++)
                items[i] = list.get(i) == null ? null : ((ItemStack) list.get(i)).clone();
            if (inWorld(p)) {
                Location at = yaml.getLocation("location");
                if (at == null || at.getWorld() == null)
                    at = Bukkit.getWorlds().getFirst().getSpawnLocation();
                if (!p.teleport(at)) {
                    notice(p, "&cSortie refusée. Réessaie /afk quitter.");
                    return;
                }
            }
            // Le marqueur reste présent jusqu'à la sauvegarde conjointe de l'inventaire rendu.
            p.getInventory().setContents(items);
            p.setLevel(yaml.getInt("level"));
            p.setExp((float) yaml.getDouble("progress"));
            p.setTotalExperience(yaml.getInt("xp"));
            p.setGameMode(GameMode.valueOf(yaml.getString("gamemode", "SURVIVAL")));
            p.getPersistentDataContainer().remove(SNAPSHOT);
            try {
                p.saveData();
            } catch (RuntimeException error) {
                p.getPersistentDataContainer().set(SNAPSHOT, PersistentDataType.STRING, saved);
                throw error;
            }
            MUTED.remove(p.getUniqueId());
            clocks.remove(p.getUniqueId());
            claims.resume(p.getUniqueId());
        } catch (Exception error) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Restauration AFK à reprendre : " + p.getUniqueId(),
                            error);
            p.kickPlayer(
                    "Inventaire protégé : restauration AFK à vérifier. Contacte un"
                            + " administrateur.");
        }
    }

    private void reconcile(Player p) {
        if (isolated(p)) {
            MUTED.add(p.getUniqueId());
            if (inWorld(p))
                clocks.put(
                        p.getUniqueId(),
                        new Clock(UUID.randomUUID().toString(), System.nanoTime()));
            else restore(p);
        }
    }

    private void exit(Player p) {
        preparing.remove(p.getUniqueId());
        if (isolated(p)) {
            accrue(p);
            restore(p);
        }
    }

    public static ItemStack talisman(int tier) {
        var i =
                Menus.item(
                        Material.NAUTILUS_SHELL,
                        "&bTalisman AFK &f" + tier,
                        "&7Actif dans un /sac ; meilleur tier uniquement.",
                        "&e+"
                                + (10 * tier)
                                + " Écus &8• &b+"
                                + (5 * tier)
                                + " XP &8• &d+"
                                + tier
                                + " Gemme(s)",
                        "&7Bonus fixes par mob de chasse éliminé.");
        var m = i.getItemMeta();
        m.getPersistentDataContainer()
                .set(
                        Objects.requireNonNull(
                                NamespacedKey.fromString("clikerv2:collection_talisman")),
                        PersistentDataType.STRING,
                        "afk:" + tier);
        i.setItemMeta(m);
        return i;
    }

    private void shop(Player p) {
        store.points(p.getUniqueId())
                .whenComplete(
                        (points, error) ->
                                sync(
                                        () -> {
                                            if (!p.isOnline()) return;
                                            if (error != null) {
                                                notice(p, "&cBoutique momentanément indisponible.");
                                                return;
                                            }
                                            var v =
                                                    menus.create(
                                                            p,
                                                            54,
                                                            "Boutique AFK • " + points + " points");
                                            v.put(
                                                    4,
                                                    Menus.item(
                                                            Material.CLOCK,
                                                            "&b" + points + " points AFK",
                                                            "&710 minutes en zone AFK = 1 point.",
                                                            "&7Achats conservés jusqu'au retour au"
                                                                    + " clicker."),
                                                    null);
                                            offer(
                                                    p,
                                                    v,
                                                    20,
                                                    LootboxItem.create(LootboxTier.RARE),
                                                    Math.max(
                                                            1,
                                                            settings.yaml.getLong("prix.rare", 1)));
                                            offer(
                                                    p,
                                                    v,
                                                    24,
                                                    LootboxItem.create(LootboxTier.DIVIN),
                                                    Math.max(
                                                            1,
                                                            settings.yaml.getLong(
                                                                    "prix.divine", 6)));
                                            for (int n = 1; n <= 4; n++)
                                                offer(
                                                        p,
                                                        v,
                                                        29 + n * 2,
                                                        talisman(n),
                                                        Math.max(
                                                                1,
                                                                settings.yaml.getLong(
                                                                        "prix.talisman-" + n,
                                                                        n * 12)));
                                            v.put(
                                                    49,
                                                    Menus.item(
                                                            Material.OAK_DOOR,
                                                            "&aQuitter la zone AFK"),
                                                    (who, right) -> {
                                                        who.closeInventory();
                                                        exit(who);
                                                    });
                                            v.show(p);
                                        }));
    }

    private void offer(Player p, Menus.View v, int slot, ItemStack item, long price) {
        var icon = item.clone();
        var m = icon.getItemMeta();
        var lore =
                m.lore() == null
                        ? new ArrayList<net.kyori.adventure.text.Component>()
                        : new ArrayList<>(m.lore());
        lore.add(fr.cliker.clikerv2.daily.DailySettings.color("&6Prix : " + price + " point(s)"));
        m.lore(lore);
        icon.setItemMeta(m);
        v.put(
                slot,
                icon,
                (who, right) -> {
                    if (!busy.add(who.getUniqueId())) return;
                    store.buy(
                                    UUID.randomUUID().toString(),
                                    who.getUniqueId(),
                                    price,
                                    ClaimService.serialize(item))
                            .whenComplete(
                                    (paid, error) ->
                                            sync(
                                                    () -> {
                                                        busy.remove(who.getUniqueId());
                                                        if (!who.isOnline()) return;
                                                        notice(
                                                                who,
                                                                error != null
                                                                        ? "&cAchat impossible."
                                                                        : paid
                                                                                ? "&aAchat"
                                                                                      + " enregistré"
                                                                                      + " ; livraison"
                                                                                      + " à la"
                                                                                      + " sortie"
                                                                                      + " AFK."
                                                                                : "&cPas assez de"
                                                                                        + " points"
                                                                                        + " AFK.");
                                                        if (Boolean.TRUE.equals(paid))
                                                            claims.resume(who.getUniqueId());
                                                        shop(who);
                                                    }));
                });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command c, String label, String[] a) {
        if (!(sender instanceof Player p)) return true;
        if (c.getName().equals("afkshop")) shop(p);
        else if (a.length > 0
                && Set.of("quitter", "sortir", "exit").contains(a[0].toLowerCase(Locale.ROOT)))
            exit(p);
        else enter(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        return a.length == 1 ? List.of("quitter") : List.of();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void join(PlayerJoinEvent e) {
        reconcile(e.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        if (isolated(e.getPlayer())) accrue(e.getPlayer());
        preparing.remove(e.getPlayer().getUniqueId());
        clocks.remove(e.getPlayer().getUniqueId());
        MUTED.remove(e.getPlayer().getUniqueId());
        busy.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent e) {
        if (!isolated(e.getPlayer())) return;
        String root = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (root.contains(":")) root = root.substring(root.indexOf(':') + 1);
        if (!Set.of("afk", "afkshop").contains(root)) {
            e.setCancelled(true);
            notice(e.getPlayer(), "&cUniquement /afkshop et /afk quitter dans cette zone.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void chat(io.papermc.paper.event.player.AsyncChatEvent e) {
        if (muted(e.getPlayer().getUniqueId())) e.setCancelled(true);
        e.viewers().removeIf(a -> a instanceof Player p && muted(p.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && isolated(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent e) {
        if (isolated(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && isolated(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p
                && isolated(p)
                && !(e.getView().getTopInventory().getHolder() instanceof Menus.View))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (isolated(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(org.bukkit.event.block.BlockBreakEvent e) {
        if (inWorld(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void placeBlock(org.bukkit.event.block.BlockPlaceEvent e) {
        if (inWorld(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && isolated(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void destination(PlayerTeleportEvent e) {
        if (e.getTo() != null
                && e.getTo()
                        .getWorld()
                        .getName()
                        .equals(settings.yaml.getString("monde", "clickhunter_afk"))
                && !isolated(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void changed(PlayerTeleportEvent e) {
        if (isolated(e.getPlayer())
                && e.getTo() != null
                && !e.getTo()
                        .getWorld()
                        .getName()
                        .equals(settings.yaml.getString("monde", "clickhunter_afk")))
            sync(
                    () -> {
                        if (isolated(e.getPlayer()) && !inWorld(e.getPlayer()))
                            restore(e.getPlayer());
                    });
    }

    public void shutdown() {
        task.cancel();
        preparing.clear();
        for (var p : Bukkit.getOnlinePlayers()) if (isolated(p)) exit(p);
        MUTED.clear();
        HandlerList.unregisterAll(this);
    }
}
