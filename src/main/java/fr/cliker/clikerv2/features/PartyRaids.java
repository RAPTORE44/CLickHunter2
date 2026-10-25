package fr.cliker.clikerv2.features;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.buyzone.*;
import fr.cliker.clikerv2.hunting.*;
import fr.cliker.clikerv2.mobs.*;
import fr.cliker.clikerv2.raid.RaidModule;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.*;

public final class PartyRaids implements CommandExecutor, TabCompleter, Listener {
    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final RewardLedger ledger;
    private final CustomMobManager mobs;
    private final BuyZoneModule zones;
    private final Menus menus;
    private final Settings settings;
    private final NamespacedKey scroll;
    private final Map<UUID, Party> memberships = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, Long> inviteTimes = new HashMap<>();
    private final Map<Integer, Party> active = new HashMap<>();
    private final Set<UUID> scrollPending = new HashSet<>();
    private RaidModule serverBoss;
    private BukkitTask task;
    private boolean poll, stopped, serverActive;
    private long totalKills, cycles;
    private final long threshold;

    private static class Party {
        final UUID leader;
        final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        RaidModule raid;
        int slot;
        long emptySince;

        Party(UUID leader) {
            this.leader = leader;
            members.add(leader);
        }
    }

    private record Invite(UUID leader, long expires) {}

    public PartyRaids(
            JavaPlugin p,
            DatabaseManager db,
            CustomMobManager mobs,
            BuyZoneModule zones,
            Menus menus) {
        plugin = p;
        this.db = db;
        this.mobs = mobs;
        this.zones = zones;
        this.menus = menus;
        ledger = Bukkit.getServicesManager().load(RewardLedger.class);
        if (ledger == null) throw new IllegalStateException("Journal des récompenses absent");
        settings = new Settings(p, "activites/raids-groupes.yml");
        threshold = settings.yaml.getLong("boss-serveur.seuil", 1_000_000_000L);
        if (threshold < 1 || threshold > 1_000_000_000_000_000L)
            throw new IllegalArgumentException("Seuil de boss invalide");
        int pool = Math.max(1, Math.min(4, settings.yaml.getInt("groupes.maximum-simultanes", 4)));
        for (int slot = 0; slot < pool; slot++)
            fr.cliker.clikerv2.world.ActivityWorlds.create("clickhunter_raid_groupe_" + slot, true);
        scroll = new NamespacedKey(p, "raid_scroll");
        serverBoss =
                RaidModule.managed(
                        p,
                        ledger,
                        mobs,
                        "clickhunter_boss_serveur",
                        Math.max(
                                1,
                                Math.min(19, settings.yaml.getInt("boss-serveur.difficulte", 19))),
                        true,
                        this::serverVictory);
        serverBoss.start();
        for (String command : List.of("raid", "boss")) {
            p.getCommand(command).setExecutor(this);
            p.getCommand(command).setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, p);
        task = Bukkit.getScheduler().runTaskTimer(p, this::tick, 20, 100);
        db.submit(
                c -> {
                    try (var s = c.createStatement()) {
                        s.execute(
                                "INSERT OR IGNORE INTO server_counters(id,value)"
                                        + " VALUES('boss_cycles',0),('boss_active',0)");
                        return null;
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    private ItemStack parchment(MobTier t) {
        var i =
                Menus.item(
                        Material.PAPER,
                        "Parchemin de raid — " + t.label(),
                        "Difficulté : zone " + (t.ordinal() + 1),
                        "Le chef le tient en main pour lancer le raid.",
                        "Un parchemin consommé par lancement ; groupe de 1 à 8 joueurs.");
        var meta = i.getItemMeta();
        meta.getPersistentDataContainer().set(scroll, PersistentDataType.STRING, t.id());
        i.setItemMeta(meta);
        return i;
    }

    @EventHandler
    public void drop(HuntKillEvent e) {
        if (e.boss) return;
        double chance = settings.yaml.getDouble("parchemins.chance-pourcent", 0.1);
        if (chance < 0 || chance > 100 || !Double.isFinite(chance)) chance = 0.1;
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble(100) >= chance) return;
        var claims = Bukkit.getServicesManager().load(ClaimService.class);
        claims.give(e.owner, e.name, parchment(e.tier));
    }

    private Party party(Player p) {
        return memberships.computeIfAbsent(p.getUniqueId(), id -> new Party(id));
    }

    private void lobby(Player p) {
        if (!p.hasPermission("cliker.raid")) return;
        var group = party(p);
        var v = menus.create(p, 27, "Groupe de raid");
        int i = 0;
        for (UUID id : group.members) {
            var member = Bukkit.getOfflinePlayer(id);
            v.put(
                    i++,
                    Menus.item(
                            Material.PLAYER_HEAD,
                            (id.equals(group.leader) ? "Chef : " : "Membre : ") + member.getName()),
                    null);
        }
        v.put(
                18,
                Menus.item(Material.EMERALD, "Inviter des joueurs", "Ou /raid inviter <pseudo>"),
                (player, r) -> inviteMenu(player, 0));
        v.put(
                22,
                Menus.item(
                        group.raid == null ? Material.PAPER : Material.ENDER_PEARL,
                        group.raid == null ? "Lancer le raid" : "Rejoindre le raid",
                        "Chef : tiens un parchemin de zone en main."),
                (player, r) -> {
                    if (group.raid == null) launch(player, group);
                    else if (group.members.contains(player.getUniqueId())) group.raid.enter(player);
                });
        v.put(
                26,
                Menus.item(Material.BARRIER, "Quitter le groupe"),
                (player, r) -> {
                    leave(player);
                    player.closeInventory();
                });
        v.show(p);
    }

    private void inviteMenu(Player p, int requested) {
        var players = Bukkit.getOnlinePlayers().stream().filter(other -> !other.equals(p)).toList();
        int page = Math.max(0, Math.min(requested, Math.max(0, (players.size() - 1) / 45)));
        var v = menus.create(p, 54, "Inviter — " + (page + 1));
        for (int i = 0; i < 45 && page * 45 + i < players.size(); i++) {
            var other = players.get(page * 45 + i);
            v.put(
                    i,
                    Menus.item(Material.PLAYER_HEAD, other.getName()),
                    (who, r) -> invite(who, other));
        }
        v.put(45, Menus.item(Material.ARROW, "Précédent"), (who, r) -> inviteMenu(who, page - 1));
        v.put(49, Menus.item(Material.ARROW, "Retour au groupe"), (who, r) -> lobby(who));
        v.put(53, Menus.item(Material.ARROW, "Suivant"), (who, r) -> inviteMenu(who, page + 1));
        v.show(p);
    }

    private void invite(Player leader, Player other) {
        var group = party(leader);
        if (!group.leader.equals(leader.getUniqueId())
                || group.raid != null
                || group.members.size() >= 8) {
            leader.sendMessage("Seul le chef peut inviter, avant le lancement (8 places maximum).");
            return;
        }
        if (other.equals(leader)
                || memberships.containsKey(other.getUniqueId())
                        && memberships.get(other.getUniqueId()).members.size() > 1) {
            leader.sendMessage("Ce joueur est déjà dans un groupe.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - inviteTimes.getOrDefault(leader.getUniqueId(), 0L) < 3000) {
            leader.sendMessage("Patiente trois secondes entre deux invitations.");
            return;
        }
        var previous = invites.get(other.getUniqueId());
        if (previous != null && previous.leader.equals(group.leader) && previous.expires > now) {
            leader.sendMessage("Ce joueur a déjà une invitation en attente.");
            return;
        }
        inviteTimes.put(leader.getUniqueId(), now);
        invites.put(
                other.getUniqueId(), new Invite(group.leader, System.currentTimeMillis() + 60000));
        other.sendMessage(
                net.kyori.adventure.text.Component.text(
                                leader.getName()
                                        + " t’invite en raid. Clique ici ou /raid accepter "
                                        + leader.getName())
                        .clickEvent(
                                net.kyori.adventure.text.event.ClickEvent.runCommand(
                                        "/raid accepter " + leader.getName())));
        leader.sendMessage("Invitation envoyée à " + other.getName() + " (60 secondes).");
    }

    private void accept(Player p, String name) {
        var inv = invites.get(p.getUniqueId());
        var leader = Bukkit.getPlayerExact(name);
        if (inv == null
                || leader == null
                || !leader.getUniqueId().equals(inv.leader)
                || inv.expires < System.currentTimeMillis()) {
            p.sendMessage("Invitation absente ou expirée.");
            return;
        }
        var group = memberships.get(inv.leader);
        if (group == null
                || !group.leader.equals(inv.leader)
                || group.raid != null
                || group.members.size() >= 8) {
            p.sendMessage("Ce groupe n’est plus disponible.");
            return;
        }
        var current = memberships.get(p.getUniqueId());
        if (current != null && (current.raid != null || current.members.size() > 1)) {
            p.sendMessage("Quitte d’abord ton groupe actuel.");
            return;
        }
        memberships.put(p.getUniqueId(), group);
        group.members.add(p.getUniqueId());
        invites.remove(p.getUniqueId());
        p.sendMessage("Invitation acceptée.");
        lobby(p);
    }

    private void launch(Player p, Party group) {
        if (!group.leader.equals(p.getUniqueId()) || group.raid != null) return;
        var item = p.getInventory().getItemInMainHand();
        String id =
                item.hasItemMeta()
                        ? item.getItemMeta()
                                .getPersistentDataContainer()
                                .get(scroll, PersistentDataType.STRING)
                        : null;
        MobTier tier = id == null ? null : MobTier.parse(id);
        if (tier == null) {
            p.sendMessage("Tiens un parchemin de raid en main.");
            return;
        }
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)
                || zones == null
                || !zones.canTravel(p, tier.id())) {
            p.sendMessage("La zone de ce parchemin n’est pas débloquée.");
            return;
        }
        for (UUID member : group.members) {
            var player = Bukkit.getPlayer(member);
            if (player == null
                    || player.isDead()
                    || fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)
                    || !zones.canTravel(player, tier.id())
                    || !player.hasPermission("cliker.raid")) {
                p.sendMessage(
                        "Chaque membre doit être en ligne, hors avant-poste et avoir débloqué la"
                                + " zone.");
                return;
            }
        }
        int limit = Math.max(1, Math.min(4, settings.yaml.getInt("groupes.maximum-simultanes", 4))),
                slot = 0;
        while (slot < limit && active.containsKey(slot)) slot++;
        if (slot >= limit) {
            p.sendMessage("Toutes les arènes sont occupées. Réessaie plus tard.");
            return;
        }
        var copy = item.clone();
        int chosen = slot;
        try {
            var raid =
                    RaidModule.managed(
                            plugin,
                            ledger,
                            mobs,
                            "clickhunter_raid_groupe_" + slot,
                            tier.ordinal() + 1,
                            false,
                            () -> finish(group));
            group.raid = raid;
            group.slot = slot;
            active.put(slot, group);
            raid.start();
            item.setAmount(item.getAmount() - 1);
            p.getInventory().setItemInMainHand(item.getAmount() == 0 ? null : item);
            p.saveData();
            for (UUID member : group.members) {
                var player = Bukkit.getPlayer(member);
                if (player != null) raid.enter(player);
            }
            if (raid.world().getPlayers().isEmpty()) {
                finish(group);
                p.getInventory().setItemInMainHand(copy);
                p.saveData();
                p.sendMessage("Téléportations refusées : parchemin conservé.");
            } else
                p.sendMessage(
                        "Raid lancé : parchemin consommé, difficulté "
                                + (tier.ordinal() + 1)
                                + ".");
        } catch (RuntimeException error) {
            if (group.raid != null) finish(group);
            p.getInventory().setItemInMainHand(copy);
            plugin.getLogger()
                    .log(java.util.logging.Level.SEVERE, "Lancement raid impossible", error);
            p.sendMessage("Lancement non confirmé : parchemin conservé.");
        }
    }

    private void finish(Party group) {
        if (group.raid == null) return;
        var raid = group.raid;
        raid.evacuate();
        if (!stopped && !raid.world().getPlayers().isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> finish(group), 10);
            return;
        }
        raid.shutdown();
        group.raid = null;
        active.remove(group.slot);
        for (UUID id : new ArrayList<>(group.members)) memberships.remove(id, group);
    }

    private void leave(Player p) {
        var group = memberships.get(p.getUniqueId());
        if (group == null) return;
        if (group.raid != null) group.raid.exit(p);
        if (group.leader.equals(p.getUniqueId()) && group.raid == null) {
            for (UUID id : group.members) memberships.remove(id, group);
        } else {
            group.members.remove(p.getUniqueId());
            memberships.remove(p.getUniqueId());
            if (group.members.isEmpty()) finish(group);
        }
        p.sendMessage("Tu as quitté le groupe.");
    }

    private void tick() {
        if (stopped) return;
        invites.values().removeIf(i -> i.expires < System.currentTimeMillis());
        for (var group : new ArrayList<>(active.values())) {
            if (group.raid.world().getPlayers().isEmpty()) {
                if (group.emptySince == 0) group.emptySince = System.currentTimeMillis();
                else if (System.currentTimeMillis() - group.emptySince > 60000) finish(group);
            } else group.emptySince = 0;
        }
        if (poll) return;
        poll = true;
        db.submit(
                        c -> {
                            try {
                                c.setAutoCommit(false);
                                long kills = value(c, "mob_kills"),
                                        cycles = value(c, "boss_cycles"),
                                        running = value(c, "boss_active");
                                boolean start = running == 0 && kills / threshold > cycles;
                                if (start) {
                                    try (var s = c.createStatement()) {
                                        s.executeUpdate(
                                                "UPDATE server_counters SET value=value+1 WHERE"
                                                        + " id='boss_cycles'");
                                        s.executeUpdate(
                                                "UPDATE server_counters SET value=1 WHERE"
                                                        + " id='boss_active'");
                                    }
                                    cycles++;
                                    running = 1;
                                }
                                c.commit();
                                return new long[] {kills, cycles, running, start ? 1 : 0};
                            } catch (SQLException ex) {
                                try {
                                    c.rollback();
                                } catch (SQLException ignored) {
                                }
                                throw new DatabaseManager.DatabaseException(ex);
                            }
                        })
                .whenComplete(
                        (state, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            poll = false;
                                            if (stopped) return;
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Compteur boss indisponible",
                                                                error);
                                                return;
                                            }
                                            totalKills = state[0];
                                            cycles = state[1];
                                            if (state[2] == 1 && !serverActive) {
                                                serverActive = true;
                                                serverBoss.beginPublicBoss();
                                                Bukkit.broadcast(
                                                        net.kyori.adventure.text.Component.text(
                                                                        "⚔ Le boss du serveur est"
                                                                            + " apparu ! Tout le"
                                                                            + " monde peut"
                                                                            + " rejoindre avec"
                                                                            + " /boss.")
                                                                .clickEvent(
                                                                        net.kyori.adventure.text
                                                                                .event.ClickEvent
                                                                                .runCommand(
                                                                                        "/boss")));
                                            }
                                        }));
    }

    private static long value(java.sql.Connection c, String id) throws SQLException {
        try (var s = c.prepareStatement("SELECT value FROM server_counters WHERE id=?")) {
            s.setString(1, id);
            try (var r = s.executeQuery()) {
                return r.next() ? r.getLong(1) : 0;
            }
        }
    }

    private void serverVictory() {
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.executeUpdate(
                                        "UPDATE server_counters SET value=0 WHERE"
                                                + " id='boss_active'");
                                return true;
                            } catch (SQLException e) {
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        })
                .whenComplete(
                        (v, e) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (stopped) return;
                                            if (e != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Fin de boss à reprendre",
                                                                e);
                                                Bukkit.getScheduler()
                                                        .runTaskLater(
                                                                plugin, this::serverVictory, 100);
                                                return;
                                            }
                                            serverActive = false;
                                            Bukkit.broadcast(
                                                    net.kyori.adventure.text.Component.text(
                                                            "Le boss du serveur a été vaincu !"
                                                                + " Récompenses réparties selon les"
                                                                + " dégâts."));
                                        }));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent e) {
        if (e.getTo() == null) return;
        String world = e.getTo().getWorld().getName();
        if (!world.startsWith("clickhunter_raid_groupe_")) return;
        Party group = memberships.get(e.getPlayer().getUniqueId());
        if (group == null || group.raid == null || group.raid.world() != e.getTo().getWorld())
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void portal(PlayerPortalEvent e) {
        teleport(e);
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        var p = e.getPlayer();
        if (p.getWorld().getName().startsWith("clickhunter_raid_groupe_")
                && !memberships.containsKey(p.getUniqueId()))
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (p.isOnline())
                                    p.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
                            });
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        invites.remove(e.getPlayer().getUniqueId());
        inviteTimes.remove(e.getPlayer().getUniqueId());
        var group = memberships.get(e.getPlayer().getUniqueId());
        if (group != null && group.raid == null) leave(e.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (!(s instanceof Player p)) return true;
        if (command.getName().equals("boss")) {
            if (a.length == 1 && a[0].equalsIgnoreCase("quitter")) {
                serverBoss.exit(p);
                return true;
            }
            if (!p.hasPermission("cliker.navigation.use")) return true;
            serverBoss.enter(p);
            if (!serverActive)
                p.sendMessage(
                        "Prochain boss : "
                                + (totalKills % threshold)
                                + " / "
                                + threshold
                                + " créatures éliminées. Aucun boss actif pour le moment.");
            return true;
        }
        if (!p.hasPermission("cliker.raid")) return true;
        if (a.length == 0) {
            lobby(p);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "inviter" -> {
                if (a.length == 2) {
                    var other = Bukkit.getPlayerExact(a[1]);
                    if (other != null) invite(p, other);
                    else p.sendMessage("Joueur hors ligne.");
                }
            }
            case "accepter" -> {
                if (a.length == 2) accept(p, a[1]);
            }
            case "lancer" -> launch(p, party(p));
            case "rejoindre" -> {
                var group = memberships.get(p.getUniqueId());
                if (group != null && group.raid != null) group.raid.enter(p);
            }
            case "quitter", "sortir" -> leave(p);
            case "parchemin" -> {
                if (!p.hasPermission("cliker.services.admin")) break;
                if (a.length >= 2 && MobTier.parse(a[1]) != null) {
                    var target = a.length == 3 ? Bukkit.getPlayerExact(a[2]) : p;
                    if (target != null) ItemDelivery.give(target, parchment(MobTier.parse(a[1])));
                }
            }
            default ->
                    p.sendMessage(
                            "/raid inviter <pseudo>, accepter <pseudo>, lancer, rejoindre,"
                                    + " quitter");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> choices =
                a.length == 1
                        ? (c.getName().equals("boss")
                                ? List.of("quitter")
                                : s.hasPermission("cliker.services.admin")
                                        ? List.of(
                                                "inviter",
                                                "accepter",
                                                "lancer",
                                                "rejoindre",
                                                "quitter",
                                                "parchemin")
                                        : List.of(
                                                "inviter",
                                                "accepter",
                                                "lancer",
                                                "rejoindre",
                                                "quitter"))
                        : a.length == 2
                                        && a[0].equalsIgnoreCase("parchemin")
                                        && s.hasPermission("cliker.services.admin")
                                ? new ArrayList<>(MobTier.byId().keySet())
                                : a.length == 2
                                        ? Bukkit.getOnlinePlayers().stream()
                                                .map(Player::getName)
                                                .toList()
                                        : List.of();
        return choices.stream()
                .filter(
                        v ->
                                v.toLowerCase(Locale.ROOT)
                                        .startsWith(a[a.length - 1].toLowerCase(Locale.ROOT)))
                .toList();
    }

    public void shutdown() {
        stopped = true;
        if (task != null) task.cancel();
        for (var g : new ArrayList<>(active.values())) finish(g);
        if (serverBoss != null) {
            serverBoss.evacuate();
            serverBoss.shutdown();
        }
        HandlerList.unregisterAll(this);
    }
}
