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
    private final fr.cliker.clikerv2.raid.RaidSetup setup;
    private final NamespacedKey scroll;
    private final Map<UUID, Party> memberships = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, Long> inviteTimes = new HashMap<>();
    private final Map<Integer, Party> active = new HashMap<>();
    private final Set<UUID> scrollPending = new HashSet<>();
    private RaidSession serverBoss;
    private final RaidFactory raidFactory;
    private final java.util.function.Consumer<Player> savePlayer;
    private final Map<UUID, Inventory> lobbies = new HashMap<>();
    private boolean forcing;
    private BukkitTask task;
    private boolean poll, stopped, serverActive;
    private long totalKills, cycles, bossDiscordSequence;
    private final long threshold;

    private static class Party {
        final UUID leader;
        final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        RaidSession raid;
        int slot;
        MobTier tier;
        long emptySince;
        boolean openToPublic;

        Party(UUID leader) {
            this.leader = leader;
            members.add(leader);
        }
    }

    private record Invite(UUID leader, long expires) {}

    interface RaidSession {
        World world();

        void start();

        void enter(Player p);

        void exit(Player p);

        void evacuate();

        void shutdown();

        void beginPublicBoss();

        default void observe(Player player) {}

        default void publicParty(UUID leader, Set<UUID> helpers) {}

        default Map<String, String> discordInfo() {
            return Map.of(
                    "boss",
                    "Boss serveur",
                    "pv",
                    "inconnus",
                    "pv_bruts",
                    "0",
                    "joueur",
                    "inconnu",
                    "participants",
                    "0");
        }
    }

    @FunctionalInterface
    interface RaidFactory {
        RaidSession create(String world, int difficulty, boolean publicBoss, Runnable finished);
    }

    private static RaidSession session(RaidModule raid) {
        return new RaidSession() {
            public void publicParty(UUID leader, Set<UUID> helpers) {
                raid.publicParty(leader, helpers);
            }

            public World world() {
                return raid.world();
            }

            public void start() {
                raid.start();
            }

            public void enter(Player p) {
                raid.enter(p);
            }

            public void exit(Player p) {
                raid.exit(p);
            }

            public void evacuate() {
                raid.evacuate();
            }

            public void shutdown() {
                raid.shutdown();
            }

            public void observe(Player player) {
                raid.observe(player);
            }

            public Map<String, String> discordInfo() {
                return raid.discordInfo();
            }

            public void beginPublicBoss() {
                raid.beginPublicBoss();
            }
        };
    }

    public PartyRaids(
            JavaPlugin p,
            DatabaseManager db,
            CustomMobManager mobs,
            BuyZoneModule zones,
            Menus menus) {
        this(
                p,
                db,
                mobs,
                zones,
                menus,
                (world, difficulty, publicBoss, finished) ->
                        session(
                                RaidModule.managed(
                                        p,
                                        Bukkit.getServicesManager().load(RewardLedger.class),
                                        mobs,
                                        world,
                                        difficulty,
                                        publicBoss,
                                        finished)),
                Player::saveData);
    }

    PartyRaids(
            JavaPlugin p,
            DatabaseManager db,
            CustomMobManager mobs,
            BuyZoneModule zones,
            Menus menus,
            RaidFactory raidFactory,
            java.util.function.Consumer<Player> savePlayer) {
        this.savePlayer = savePlayer;
        this.raidFactory = raidFactory;
        plugin = p;
        this.db = db;
        this.mobs = mobs;
        this.zones = zones;
        this.menus = menus;
        ledger = Bukkit.getServicesManager().load(RewardLedger.class);
        if (ledger == null) throw new IllegalStateException("Journal des récompenses absent");
        settings = new Settings(p, "activites/raids-groupes.yml");
        setup = new fr.cliker.clikerv2.raid.RaidSetup(p, this::observe);
        threshold = setup.threshold(settings.yaml.getLong("boss-serveur.seuil", 1_000_000_000L));
        if (threshold < 1 || threshold > 1_000_000_000_000_000L)
            throw new IllegalArgumentException("Seuil de boss invalide");
        int pool = Math.max(1, Math.min(4, settings.yaml.getInt("groupes.maximum-simultanes", 4)));
        // Les mondes de raid sont créés uniquement au lancement d’un groupe.
        scroll = new NamespacedKey(p, "raid_scroll");
        serverBoss =
                raidFactory.create(
                        setup.world(true, 0),
                        Math.max(
                                1,
                                Math.min(
                                        19,
                                        setup.difficulty(
                                                settings.yaml.getInt(
                                                        "boss-serveur.difficulte", 19)))),
                        true,
                        this::serverVictory);
        serverBoss.start();
        for (String command : List.of("raid", "boss")) {
            var registered = p.getCommand(command);
            if (registered != null) {
                registered.setExecutor(this);
                registered.setTabCompleter(this);
            }
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
        return fr.cliker.clikerv2.raid.RaidScrolls.create(plugin, t);
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

    @EventHandler(priority = EventPriority.HIGH)
    public void parchmentClick(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || !event.getAction().isRightClick()) return;
        var item = event.getItem();
        if (item == null
                || !item.hasItemMeta()
                || !item.getItemMeta()
                        .getPersistentDataContainer()
                        .has(scroll, PersistentDataType.STRING)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("cliker.raid")
                || fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)) return;
        fr.cliker.clikerv2.ui.MenuOpenGuard.request(plugin, player, () -> lobby(player));
    }

    private void lobby(Player p) {
        if (!p.hasPermission("cliker.raid")) return;
        var group = party(p);
        var v = menus.create(p, 27, "Groupe de raid");
        int i = 0;
        for (UUID id : group.members) v.put(i++, memberItem(group, id), null);
        v.put(
                18,
                Menus.item(Material.EMERALD, "Inviter des joueurs", "Ou /raid inviter <pseudo>"),
                (player, r) -> inviteMenu(player, 0));
        v.put(
                22,
                launchItem(group),
                (player, r) -> {
                    var current = party(player);
                    if (current.raid == null) launch(player, current);
                    else if (current.members.contains(player.getUniqueId()))
                        current.raid.enter(player);
                });
        v.put(
                20,
                Menus.item(
                        Material.BELL,
                        "&bOuvrir au public",
                        "&7Trois renforts ; chacun gagne un tiers du lot.",
                        "&7Ou /raid public"),
                (player, right) -> openPublic(player));
        v.put(
                26,
                Menus.item(Material.BARRIER, "Quitter le groupe"),
                (player, r) -> {
                    leave(player);
                    player.closeInventory();
                });
        v.put(
                24,
                Menus.item(
                        Material.CLOCK,
                        "&bActualiser la préparation",
                        "&7Vérifier le parchemin, les accès et le matériel."),
                (who, right) -> refreshLobby(group));
        v.show(p);
        lobbies.put(p.getUniqueId(), v.getInventory());
    }

    private void refreshLobby(Party group) {
        for (UUID id : group.members) {
            Player player = Bukkit.getPlayer(id);
            Inventory inventory = lobbies.get(id);
            if (player == null
                    || inventory == null
                    || player.getOpenInventory().getTopInventory() != inventory) continue;
            for (int slot = 0; slot < 8; slot++)
                inventory.setItem(slot, Menus.item(Material.BLACK_STAINED_GLASS_PANE, " "));
            int slot = 0;
            for (UUID member : group.members) inventory.setItem(slot++, memberItem(group, member));
            inventory.setItem(22, launchItem(group));
        }
    }

    private MobTier selectedTier(Party group) {
        if (group.raid != null) return group.tier;
        Player leader = Bukkit.getPlayer(group.leader);
        if (leader == null) return null;
        ItemStack held = leader.getInventory().getItemInMainHand();
        String id =
                held.hasItemMeta()
                        ? held.getItemMeta()
                                .getPersistentDataContainer()
                                .get(scroll, PersistentDataType.STRING)
                        : null;
        return id == null ? null : MobTier.parse(id);
    }

    private List<String> blockers(Party group, UUID id, MobTier tier) {
        List<String> reasons = new ArrayList<>();
        Player member = Bukkit.getPlayer(id);
        if (member == null) {
            reasons.add("hors ligne");
            return reasons;
        }
        if (member.isDead()) reasons.add("joueur mort");
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(member))
            reasons.add("hors inventaire clicker");
        if (!member.hasPermission("cliker.raid")) reasons.add("permission raid manquante");
        if (tier == null) reasons.add("parchemin du chef manquant");
        else if (zones == null || !zones.canTravel(member, tier.id()))
            reasons.add("zone non débloquée");
        var delivery = Bukkit.getServicesManager().load(ClaimService.class);
        if (delivery != null && delivery.isDelivering(id))
            reasons.add("transfert d'objets en cours");
        return reasons;
    }

    private ItemStack memberItem(Party group, UUID id) {
        Player member = Bukkit.getPlayer(id);
        MobTier tier = selectedTier(group);
        List<String> reasons = blockers(group, id, tier);
        List<String> lore = new ArrayList<>();
        lore.add(reasons.isEmpty() ? "&a✔ Prêt" : "&c✘ Non prêt");
        boolean unlocked =
                member != null
                        && tier != null
                        && zones != null
                        && zones.canTravel(member, tier.id());
        lore.add(
                tier == null
                        ? "&7Zone : &eparchemin du chef à choisir"
                        : "&7Zone : " + (unlocked ? "&a✔ débloquée" : "&c✘ verrouillée"));
        lore.add(
                id.equals(group.leader)
                        ? "&7Parchemin : "
                                + (tier == null ? "&c✘ absent ou invalide" : "&a✔ valide en main")
                        : "&7Parchemin : &bcelui du chef suffit");
        lore.add("&8────────────────────");
        lore.add("&eÉquipement conseillé (non bloquant)");
        lore.add(
                unlocked
                        ? "&7Épée et set : &f" + tier.label() + " &7ou mieux."
                        : "&7Épée et armures de la zone du raid.");
        if (member != null && unlocked) {
            int armor = 0;
            for (ItemStack piece : member.getInventory().getArmorContents()) {
                if (piece == null || !piece.hasItemMeta()) continue;
                String tag =
                        piece.getItemMeta()
                                .getPersistentDataContainer()
                                .get(
                                        new NamespacedKey(plugin, "cliker_tier_armor"),
                                        PersistentDataType.STRING);
                MobTier pieceTier = tag == null ? null : MobTier.parse(tag);
                if (pieceTier != null && pieceTier.ordinal() >= tier.ordinal()) armor++;
            }
            lore.add(
                    (armor == 4 ? "&a✔ " : "&e⚠ ")
                            + "Armure de niveau conseillé : "
                            + armor
                            + "/4 pièces");
        }
        if (member != null)
            lore.add(
                    fr.cliker.clikerv2.combat.CombatReach.sword(
                                    member.getInventory().getItemInMainHand())
                            ? "&a✔ Épée en main"
                            : "&e⚠ Aucune épée en main");
        for (String reason : reasons) lore.add("&c• " + reason);
        ItemStack icon =
                Menus.item(
                        Material.PLAYER_HEAD,
                        (reasons.isEmpty() ? "&a" : "&c")
                                + (id.equals(group.leader) ? "Chef : " : "Membre : ")
                                + Objects.toString(Bukkit.getOfflinePlayer(id).getName(), "Joueur"),
                        lore.toArray(String[]::new));
        return icon;
    }

    private ItemStack launchItem(Party group) {
        if (group.raid != null) return Menus.item(Material.ENDER_PEARL, "&aRejoindre le raid");
        MobTier tier = selectedTier(group);
        List<String> lore = new ArrayList<>();
        for (UUID id : group.members) {
            List<String> reasons = blockers(group, id, tier);
            if (!reasons.isEmpty())
                lore.add(
                        "&c"
                                + Objects.toString(Bukkit.getOfflinePlayer(id).getName(), "Joueur")
                                + " : "
                                + String.join(", ", reasons));
        }
        int limit = Math.max(1, Math.min(4, settings.yaml.getInt("groupes.maximum-simultanes", 4)));
        if (active.size() >= limit) lore.add("&cToutes les arènes sont occupées.");
        boolean ready = lore.isEmpty();
        if (ready) lore.add("&a✔ Tous les membres sont prêts.");
        lore.add("&7Seul le chef lance le raid.");
        lore.add("&7L'équipement conseillé ne bloque pas le départ.");
        return Menus.item(
                ready ? Material.LIME_DYE : Material.BARRIER,
                ready ? "&aLancer le raid" : "&cDépart bloqué",
                lore.toArray(String[]::new));
    }

    @EventHandler(ignoreCancelled = true)
    public void preparationHeld(PlayerItemHeldEvent event) {
        Party group = memberships.get(event.getPlayer().getUniqueId());
        if (group != null)
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (!stopped) refreshLobby(group);
                            });
    }

    @EventHandler
    public void closeLobby(org.bukkit.event.inventory.InventoryCloseEvent event) {
        lobbies.remove(event.getPlayer().getUniqueId(), event.getInventory());
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
                || group.members.size() >= (group.openToPublic ? 4 : 8)) {
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
        var joined = memberships.get(p.getUniqueId());
        if (leader != null
                && joined != null
                && joined.leader.equals(leader.getUniqueId())
                && joined.members.contains(p.getUniqueId())) {
            p.sendMessage("Tu es déjà dans ce groupe de raid.");
            refreshLobby(joined);
            if (p.getOpenInventory().getTopInventory() != lobbies.get(p.getUniqueId())) lobby(p);
            return;
        }
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
                || group.members.size() >= (group.openToPublic ? 4 : 8)) {
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
        p.sendMessage("Invitation acceptée : tu as rejoint le groupe de " + leader.getName() + ".");
        leader.sendMessage(
                p.getName() + " a rejoint ton groupe de raid (" + group.members.size() + "/8).");
        refreshLobby(group);
        if (p.getOpenInventory().getTopInventory() != lobbies.get(p.getUniqueId())) lobby(p);
    }

    private void openPublic(Player p) {
        var group = party(p);
        if (!group.leader.equals(p.getUniqueId())
                || group.raid != null
                || group.members.size() > 1) {
            p.sendMessage("§cPrépare un groupe seul avant de l’ouvrir au public.");
            return;
        }
        if (group.openToPublic) {
            p.sendMessage("§7Ton raid est déjà ouvert au public.");
            return;
        }
        var held = p.getInventory().getItemInMainHand();
        String id =
                held.hasItemMeta()
                        ? held.getItemMeta()
                                .getPersistentDataContainer()
                                .get(scroll, PersistentDataType.STRING)
                        : null;
        MobTier tier = id == null ? null : MobTier.parse(id);
        if (tier == null
                || zones == null
                || !zones.canTravel(p, tier.id())
                || fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)) {
            p.sendMessage("§cTiens un parchemin d’une zone débloquée au spawn.");
            return;
        }
        group.tier = tier;
        group.openToPublic = true;
        fr.cliker.clikerv2.discord.DiscordNotifications.emit(
                "raid-public", UUID.randomUUID().toString(), Map.of("joueur", p.getName()));
        Bukkit.broadcast(
                fr.cliker.clikerv2.daily.DailySettings.color(
                                "&d⚔ &f"
                                        + p.getName()
                                        + " &7ouvre son raid à trois renforts. &e[Participer]")
                        .clickEvent(
                                net.kyori.adventure.text.event.ClickEvent.runCommand(
                                        "/raid participer " + p.getName())));
        lobby(p);
    }

    private void joinPublic(Player p, String name) {
        Player leader = Bukkit.getPlayerExact(name);
        Party group = leader == null ? null : memberships.get(leader.getUniqueId());
        if (group == null
                || !group.openToPublic
                || group.raid != null
                || group.members.size() >= 4
                || group.leader.equals(p.getUniqueId())) {
            p.sendMessage("§cCe raid public n’a plus de place disponible.");
            return;
        }
        if (group.tier == null
                || zones == null
                || !zones.canTravel(p, group.tier.id())
                || fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)
                || p.isDead()) {
            p.sendMessage("§cTu dois avoir débloqué cette zone et être disponible au clicker.");
            return;
        }
        Party existing = memberships.get(p.getUniqueId());
        if (existing != null && (existing.raid != null || existing.members.size() > 1)) {
            p.sendMessage("§cQuitte d’abord ton groupe de raid.");
            return;
        }
        if (existing != null) memberships.remove(p.getUniqueId(), existing);
        group.members.add(p.getUniqueId());
        memberships.put(p.getUniqueId(), group);
        invites.remove(p.getUniqueId());
        refreshLobby(group);
        lobby(p);
        leader.sendMessage(
                "§a"
                        + p.getName()
                        + " rejoint ton raid ("
                        + (group.members.size() - 1)
                        + "/3 renforts).");
    }

    private void launch(Player p, Party group) {
        var deliveries = Bukkit.getServicesManager().load(ClaimService.class);
        if (deliveries != null && deliveries.isDelivering(p.getUniqueId())) {
            p.sendMessage("§eAttends la fin des transferts d’objets.");
            return;
        }
        if (!group.leader.equals(p.getUniqueId())) {
            p.sendMessage("Seul le chef du groupe peut lancer le raid.");
            return;
        }
        if (group.raid != null) {
            p.sendMessage("Ce raid est déjà lancé. /raid rejoindre pour y aller.");
            return;
        }
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
                    || !player.hasPermission("cliker.raid")
                    || deliveries != null && deliveries.isDelivering(member)) {
                p.sendMessage(
                        "Impossible de lancer : "
                                + (player == null ? member : player.getName())
                                + " doit être en ligne, hors avant-poste, autorisé au raid et avoir"
                                + " débloqué la zone et terminé ses transferts d'objets.");
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
                    raidFactory.create(
                            setup.world(false, slot),
                            tier.ordinal() + 1,
                            false,
                            () -> finish(group));
            if (group.openToPublic) {
                var helpers = new HashSet<>(group.members);
                helpers.remove(group.leader);
                raid.publicParty(group.leader, helpers);
            }
            group.raid = raid;
            group.tier = tier;
            group.slot = slot;
            active.put(slot, group);
            raid.start();
            item.setAmount(item.getAmount() - 1);
            p.getInventory().setItemInMainHand(item.getAmount() == 0 ? null : item);
            savePlayer.accept(p);
            for (UUID member : group.members) {
                var player = Bukkit.getPlayer(member);
                if (player != null) {
                    player.closeInventory();
                    raid.enter(player);
                    if (player.getWorld() != raid.world()) {
                        player.sendMessage(
                                "Téléportation refusée. Tu restes dans le groupe ; /raid rejoindre"
                                        + " pour réessayer.");
                        p.sendMessage(
                                "Téléportation refusée pour "
                                        + player.getName()
                                        + " ; il reste membre du raid.");
                    }
                }
            }
            if (raid.world().getPlayers().isEmpty()) {
                finish(group);
                p.getInventory().setItemInMainHand(copy);
                savePlayer.accept(p);
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
        if (group == null) {
            for (var activity : active.values())
                if (activity.raid != null && activity.raid.world() == p.getWorld()) {
                    activity.raid.exit(p);
                    return;
                }
            return;
        }
        if (group.raid != null) {
            group.raid.exit(p);
            if (p.getWorld() == group.raid.world()) {
                p.sendMessage(
                        "§eLa sortie est bloquée. Ton appartenance au groupe est conservée ;"
                                + " réessaie /raid quitter.");
                return;
            }
        }
        if (group.leader.equals(p.getUniqueId()) && group.raid == null) {
            for (UUID id : group.members) memberships.remove(id, group);
        } else {
            group.members.remove(p.getUniqueId());
            memberships.remove(p.getUniqueId());
            if (group.members.isEmpty()) finish(group);
        }
        p.sendMessage("Tu as quitté le groupe.");
        lobbies.remove(p.getUniqueId());
        if (!group.leader.equals(p.getUniqueId())) refreshLobby(group);
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
                                    try (var q = c.createStatement()) {
                                        q.executeUpdate(
                                                "UPDATE server_counters SET value=value+1 WHERE"
                                                    + " id='boss_discord_sequence'");
                                    }
                                    cycles++;
                                    running = 1;
                                }
                                c.commit();
                                return new long[] {
                                    kills,
                                    cycles,
                                    running,
                                    start ? 1 : 0,
                                    value(c, "boss_discord_sequence")
                                };
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
                                            bossDiscordSequence = state[4];
                                            if (state[2] == 1) activateServerBoss();
                                        }));
    }

    private boolean activateServerBoss() {
        if (serverActive) return true;
        try {
            serverBoss.beginPublicBoss();
            serverActive = true;
            fr.cliker.clikerv2.discord.DiscordNotifications.emit(
                    "boss-serveur-spawn", "boss-" + bossDiscordSequence, serverBoss.discordInfo());
            Bukkit.broadcast(
                    net.kyori.adventure.text.Component.text(
                                    "⚔ Le boss du serveur est apparu ! Tout le monde peut rejoindre"
                                            + " avec /boss.")
                            .clickEvent(
                                    net.kyori.adventure.text.event.ClickEvent.runCommand("/boss")));
            return true;
        } catch (RuntimeException error) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Apparition du boss serveur à réessayer",
                            error);
            return false;
        }
    }

    private void forceServerBoss(CommandSender sender) {
        if (!sender.hasPermission("cliker.boss.admin")) {
            sender.sendMessage("Permission administrateur requise.");
            return;
        }
        if (forcing) {
            sender.sendMessage("Une apparition forcée est déjà en cours.");
            return;
        }
        forcing = true;
        db.submit(
                        c -> {
                            try {
                                c.setAutoCommit(false);
                                try (var s = c.createStatement()) {
                                    s.executeUpdate(
                                            "INSERT OR IGNORE INTO server_counters(id,value)"
                                                    + " VALUES('boss_active',0)");
                                }
                                boolean created = value(c, "boss_active") == 0;
                                try (var s = c.createStatement()) {
                                    s.executeUpdate(
                                            "UPDATE server_counters SET value=1 WHERE"
                                                    + " id='boss_active'");
                                }
                                if (created)
                                    try (var q = c.createStatement()) {
                                        q.executeUpdate(
                                                "UPDATE server_counters SET value=value+1 WHERE"
                                                    + " id='boss_discord_sequence'");
                                    }
                                long sequence = value(c, "boss_discord_sequence");
                                c.commit();
                                return new long[] {created ? 1 : 0, sequence};
                            } catch (SQLException error) {
                                try {
                                    c.rollback();
                                } catch (SQLException ignored) {
                                }
                                throw new DatabaseManager.DatabaseException(error);
                            }
                        })
                .whenComplete(
                        (created, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            forcing = false;
                                            if (stopped) return;
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Commande boss serveur impossible",
                                                                error);
                                                sender.sendMessage(
                                                        "Apparition non confirmée. Consulte les"
                                                                + " journaux.");
                                                return;
                                            }
                                            bossDiscordSequence = created[1];
                                            if (!activateServerBoss()) {
                                                sender.sendMessage(
                                                        "Apparition en attente : vérifie les"
                                                                + " journaux puis réessaie.");
                                                return;
                                            }
                                            sender.sendMessage(
                                                    created[0] == 1
                                                            ? "Boss serveur apparu. Le compteur du"
                                                                    + " prochain boss naturel est"
                                                                    + " conservé."
                                                            : "Le boss serveur est déjà actif :"
                                                                    + " aucun doublon créé.");
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
                                            fr.cliker.clikerv2.discord.DiscordNotifications.emit(
                                                    "boss-serveur-mort",
                                                    "boss-" + bossDiscordSequence,
                                                    serverBoss.discordInfo());
                                            Bukkit.broadcast(
                                                    net.kyori.adventure.text.Component.text(
                                                            "Le boss du serveur a été vaincu !"
                                                                + " Récompenses réparties selon les"
                                                                + " dégâts."));
                                        }));
    }

    private boolean privateWorld(World world) {
        for (int i = 0; i < 4; i++) if (world.getName().equals(setup.world(false, i))) return true;
        return false;
    }

    private void observe(Player player, boolean publicBoss) {
        if (!player.hasPermission(publicBoss ? "cliker.boss.admin" : "cliker.raid.admin")) {
            player.sendMessage("§cObservation réservée au staff.");
            return;
        }
        if (publicBoss) {
            serverBoss.observe(player);
            return;
        }
        var own = memberships.get(player.getUniqueId());
        var group =
                own != null && own.raid != null
                        ? own
                        : active.values().stream()
                                .filter(v -> v.raid != null)
                                .findFirst()
                                .orElse(null);
        if (group == null) player.sendMessage("§7Aucun raid actif à observer.");
        else group.raid.observe(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getPlayer().hasPermission("cliker.raid.admin")) return;
        if (!privateWorld(e.getTo().getWorld())) return;
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
        if (privateWorld(p.getWorld())
                && !p.hasPermission("cliker.raid.admin")
                && !memberships.containsKey(p.getUniqueId()))
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(p)
                                        && privateWorld(p.getWorld())
                                        && !memberships.containsKey(p.getUniqueId())
                                        && !p.hasPermission("cliker.raid.admin"))
                                    p.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
                            });
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        lobbies.remove(e.getPlayer().getUniqueId());
        invites.remove(e.getPlayer().getUniqueId());
        inviteTimes.remove(e.getPlayer().getUniqueId());
        var group = memberships.get(e.getPlayer().getUniqueId());
        if (group != null && group.raid == null) leave(e.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (command.getName().equals("boss") && a.length == 1 && a[0].equalsIgnoreCase("spawn")) {
            forceServerBoss(s);
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Commande réservée aux joueurs, sauf /boss spawn pour la console.");
            return true;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("admin"))
            return setup.command(p, command.getName().equals("boss"), a);
        if (a.length > 0 && a[0].equalsIgnoreCase("observer")) {
            if (command.getName().equals("boss")) observe(p, true);
            else if (p.hasPermission("cliker.raid.admin") && a.length == 2) {
                var leader = Bukkit.getPlayerExact(a[1]);
                var group = leader == null ? null : memberships.get(leader.getUniqueId());
                if (group == null || group.raid == null)
                    p.sendMessage("§7Ce joueur n’est pas dans un raid actif.");
                else group.raid.observe(p);
            } else observe(p, false);
            return true;
        }
        if (command.getName().equals("boss")) {
            if (a.length > 1 || a.length == 1 && !a[0].equalsIgnoreCase("quitter")) {
                p.sendMessage(
                        "/boss ou /boss quitter"
                                + (p.hasPermission("cliker.boss.admin") ? " · /boss spawn" : ""));
                return true;
            }
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
        if (!p.hasPermission("cliker.raid") && !p.hasPermission("cliker.raid.admin")) return true;
        if (a.length == 0) {
            lobby(p);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "public" -> openPublic(p);
            case "participer" -> {
                if (a.length == 2) joinPublic(p, a[1]);
                else p.sendMessage("§e/raid participer <chef>");
            }
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
                if (group == null || group.raid == null)
                    p.sendMessage("§7Tu n’as pas de raid actif. /raid pour préparer un groupe.");
                else if (group.tier == null
                        || !p.hasPermission("cliker.raid")
                        || p.isDead()
                        || fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)
                        || zones == null
                        || !zones.canTravel(p, group.tier.id()))
                    p.sendMessage(
                            "§cImpossible de rejoindre : vérifie ton accès à la zone et reviens au"
                                    + " spawn si tu es dans ton avant-poste.");
                else group.raid.enter(p);
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
                                ? (s.hasPermission("cliker.boss.admin")
                                        ? List.of("quitter", "spawn")
                                        : List.of("quitter"))
                                : s.hasPermission("cliker.services.admin")
                                        ? List.of(
                                                "inviter",
                                                "public",
                                                "participer",
                                                "accepter",
                                                "lancer",
                                                "rejoindre",
                                                "quitter",
                                                "parchemin")
                                        : List.of(
                                                "inviter",
                                                "public",
                                                "participer",
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
        if (a.length == 1
                && s.hasPermission(
                        c.getName().equals("boss") ? "cliker.boss.admin" : "cliker.raid.admin")) {
            choices = new ArrayList<>(choices);
            choices.addAll(List.of("admin", "observer"));
        }
        if (a.length >= 2 && a[0].equalsIgnoreCase("admin")) {
            if (!s.hasPermission(
                    c.getName().equals("boss") ? "cliker.boss.admin" : "cliker.raid.admin"))
                return List.of();
            choices =
                    a.length == 2
                            ? (c.getName().equals("boss")
                                    ? List.of(
                                            "info", "map", "set", "rayon", "pnj", "param", "seuil",
                                            "zone")
                                    : List.of("info", "map", "set", "rayon", "pnj", "param"))
                            : a[1].equalsIgnoreCase("param") && a.length == 3
                                    ? new ArrayList<>(
                                            fr.cliker.clikerv2.raid.RaidSetup.parameters().keySet())
                                    : a[1].equalsIgnoreCase("pnj")
                                            ? (a.length == 3
                                                    ? List.of("entree", "sortie", "observation")
                                                    : List.of("spawn", "remove"))
                                            : a.length == 3 && a[1].equalsIgnoreCase("zone")
                                                    ? new ArrayList<>(MobTier.byId().keySet())
                                                    : a.length == 3 && !c.getName().equals("boss")
                                                            ? List.of("1", "2", "3", "4")
                                                            : a[1].equalsIgnoreCase("set")
                                                                    ? List.of(
                                                                            "entree",
                                                                            "centre",
                                                                            "observation",
                                                                            "sortie")
                                                                    : a[1].equalsIgnoreCase("map")
                                                                            ? Bukkit.getWorlds()
                                                                                    .stream()
                                                                                    .map(
                                                                                            World
                                                                                                    ::getName)
                                                                                    .toList()
                                                                            : List.of();
        }
        String prefix = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    public void shutdown() {
        stopped = true;
        lobbies.clear();
        if (task != null) task.cancel();
        for (var g : new ArrayList<>(active.values())) finish(g);
        if (serverBoss != null) {
            serverBoss.evacuate();
            serverBoss.shutdown();
        }
        setup.shutdown();
        HandlerList.unregisterAll(this);
    }
}
