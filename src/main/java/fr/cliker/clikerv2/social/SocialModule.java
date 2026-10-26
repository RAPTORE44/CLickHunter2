package fr.cliker.clikerv2.social;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.buyzone.BuyZoneModule;
import fr.cliker.clikerv2.hunting.RewardLedger;
import fr.cliker.clikerv2.outpost.WorldInventories;
import fr.cliker.clikerv2.rewards.*;
import fr.cliker.clikerv2.runtime.PlayerSessions;
import fr.cliker.clikerv2.runtime.ServerTasks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Commandes joueur synchrones ; transactions SQL et rendu de chat sans accès bloquant. */
public final class SocialModule implements CommandExecutor, TabCompleter, Listener {
    private final JavaPlugin plugin;
    private final SocialSettings settings;
    private final SocialStore store;
    private final RewardLedger ledger;
    private final ClaimService claims;
    private final OutgoingItems outgoing;
    private final BuyZoneModule zones;
    private final Clock clock;
    private final NamespacedKey ringKey;
    private final Map<UUID, Proposal> proposals = new HashMap<>();
    private final Map<UUID, Confirmation> divorces = new HashMap<>();
    private final Map<UUID, Long> actions = new HashMap<>();
    private final Map<UUID, Long> recommendAgain = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, Object> sessions = new HashMap<>();
    private final Map<UUID, Long> popularity = new ConcurrentHashMap<>();
    private BukkitTask ticker;
    private volatile boolean stopped;
    private boolean ready;

    private record Proposal(UUID from, String name, UUID target, long deadline, boolean special) {}

    private record Confirmation(String bond, long deadline) {}

    private static final class View implements InventoryHolder {
        private final UUID partner;
        private Inventory inventory;
        private int closeSlot = -1;

        View(UUID partner) {
            this.partner = partner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public SocialModule(
            JavaPlugin plugin,
            DatabaseManager db,
            RewardLedger ledger,
            ClaimService claims,
            OutgoingItems outgoing,
            BuyZoneModule zones) {
        this(plugin, db, ledger, claims, outgoing, zones, Clock.systemUTC());
    }

    SocialModule(
            JavaPlugin plugin,
            DatabaseManager db,
            RewardLedger ledger,
            ClaimService claims,
            OutgoingItems outgoing,
            BuyZoneModule zones,
            Clock clock) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.claims = claims;
        this.outgoing = outgoing;
        this.zones = zones;
        this.clock = clock;
        settings = new SocialSettings(plugin);
        store = new SocialStore(db, ledger);
        ringKey = new NamespacedKey(plugin, "marriage_ring");
    }

    public void start() {
        for (String name : List.of("marry", "rc", "echelons")) {
            Objects.requireNonNull(plugin.getCommand(name)).setExecutor(this);
            plugin.getCommand(name).setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        store.initialize()
                .whenComplete(
                        (v, error) ->
                                sync(
                                        () -> {
                                            if (error != null) {
                                                failure(error);
                                                return;
                                            }
                                            ready = true;
                                            for (Player p : Bukkit.getOnlinePlayers()) load(p);
                                        }));
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 20, 20);
    }

    private Component text(String value) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(value);
    }

    private Component line(String key, Object... replacements) {
        String message = settings.yaml.getString("messages." + key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2)
            message =
                    message.replace(
                            "{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        return text(settings.yaml.getString("messages.prefixe", "&dCommunauté &8» &r") + message);
    }

    private void msg(CommandSender p, String key, Object... values) {
        p.sendMessage(line(key, values));
    }

    private void sync(Runnable action) {
        if (!stopped)
            ServerTasks.run(
                    plugin,
                    () -> {
                        if (!stopped) action.run();
                    });
    }

    private void failure(Throwable e) {
        plugin.getLogger().log(Level.SEVERE, "Action communautaire non validée", e);
    }

    private boolean current(Player p) {
        return !stopped && PlayerSessions.isCurrent(p);
    }

    private boolean begin(Player p) {
        if (!ready) {
            msg(p, "attente");
            return false;
        }
        if (!busy.add(p.getUniqueId())) {
            msg(p, "attente");
            return false;
        }
        return true;
    }

    private void bond(Player p, Consumer<SocialStore.Bond> action) {
        if (!begin(p)) return;
        store.bond(p.getUniqueId())
                .whenComplete(
                        (bond, error) ->
                                sync(
                                        () -> {
                                            busy.remove(p.getUniqueId());
                                            if (!current(p)) return;
                                            if (error != null) {
                                                failure(error);
                                                msg(p, "erreur");
                                            } else if (bond == null) msg(p, "non-marie");
                                            else action.accept(bond);
                                        }));
    }

    private boolean available(Player p) {
        return p != null && p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR;
    }

    private boolean isolated(Player p) {
        String world = p.getWorld().getName();
        return WorldInventories.isolated(p)
                || world.startsWith("clickhunter_raid")
                || world.equals("clickhunter_boss_serveur");
    }

    private boolean playable(Player p) {
        if (isolated(p)) {
            msg(p, "isolation");
            return false;
        }
        return true;
    }

    private boolean action(Player p) {
        long now = clock.millis();
        long next = actions.getOrDefault(p.getUniqueId(), 0L);
        if (now < next) {
            msg(p, "delai", "secondes", Math.max(1, (next - now + 999) / 1000));
            return false;
        }
        actions.put(
                p.getUniqueId(),
                now
                        + Math.max(
                                        1,
                                        Math.min(
                                                60,
                                                settings.yaml.getLong(
                                                        "mariage.delai-action-secondes", 5)))
                                * 1000);
        return true;
    }

    private ItemStack ring() {
        ItemStack item = new ItemStack(settings.ringMaterial);
        var meta = item.getItemMeta();
        meta.displayName(
                text(settings.yaml.getString("mariage.objet.nom", "&dAnneau de Saint-Valentin")));
        meta.lore(
                settings.yaml.getStringList("mariage.objet.description").stream()
                        .map(this::text)
                        .toList());
        meta.getPersistentDataContainer().set(ringKey, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private boolean hasRing(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        return held.getType() == settings.ringMaterial
                && held.hasItemMeta()
                && Byte.valueOf((byte) 1)
                        .equals(
                                held.getItemMeta()
                                        .getPersistentDataContainer()
                                        .get(ringKey, PersistentDataType.BYTE));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            if (command.getName().equals("marry")
                    && args.length == 2
                    && args[0].equalsIgnoreCase("objet")) giveRing(sender, args[1]);
            else msg(sender, "joueur");
            return true;
        }
        String root = command.getName();
        String permission = root.equals("marry") ? "cliker.marry.use" : "cliker.popularite.use";
        if (!p.hasPermission(permission)) {
            msg(p, "permission");
            return true;
        }
        if (root.equals("rc") || root.equals("echelons")) {
            if (!settings.yaml.getBoolean("popularite.actif", true)) {
                msg(p, "indisponible");
                return true;
            }
            if (root.equals("echelons")) ranks(p);
            else recommend(p, args);
            return true;
        }
        if (!settings.yaml.getBoolean("mariage.actif", true)) {
            msg(p, "indisponible");
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help") || sub.equals("aide")) {
            help(p);
            return true;
        }
        if (sub.equals("objet") && args.length == 2) {
            giveRing(p, args[1]);
            return true;
        }
        if (settings.yaml.getBoolean("mariage.commandes-saisonnieres", false)
                && !settings.season(clock)
                && !hasRing(p)) {
            msg(p, "saison-commandes");
            return true;
        }
        if (!available(p)) {
            msg(p, "hors-ligne");
            return true;
        }
        switch (sub) {
            case "info" ->
                    bond(
                            p,
                            b ->
                                    p.sendMessage(
                                            text(
                                                    "&dTon partenaire : &f"
                                                            + partnerName(b.partner())
                                                            + " &7— /marry help")));
            case "demand", "demande" -> {
                if (args.length == 2) propose(p, args[1]);
                else help(p);
            }
            case "accept" -> {
                if (args.length == 2) accept(p, args[1]);
                else help(p);
            }
            case "deny", "refuser" -> {
                if (args.length == 2) deny(p, args[1]);
                else help(p);
            }
            case "divorce" -> bond(p, b -> divorce(p, b, args));
            case "tp", "s", "gift", "dback", "kiss", "inv", "ec" -> {
                if (args.length != 1) {
                    help(p);
                    break;
                }
                if ((sub.equals("tp") || sub.equals("s") || sub.equals("dback"))
                        && !p.hasPermission("cliker.marry.teleport")) {
                    msg(p, "permission");
                    break;
                }
                if ((sub.equals("inv") || sub.equals("ec"))
                        && !p.hasPermission("cliker.marry.consulter")) {
                    msg(p, "permission");
                    break;
                }
                bond(p, b -> partnerAction(p, b, sub));
            }
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage(text("&d&lMariage &7— demandes en février, ou avec un anneau spécial"));
        String[][] commands = {
            {"demand <pseudo>", "Demander en mariage (" + settings.requestSeconds + " s)."},
            {"accept <pseudo>", "Accepter une demande."},
            {"deny <pseudo>", "Refuser une demande."},
            {"divorce", "Divorcer : " + format(settings.divorceCost) + " Écus, avec confirmation."},
            {"tp", "Rejoindre ton partenaire."},
            {"s", "Faire venir ton partenaire."},
            {"gift", "Offrir toute la pile tenue en main."},
            {"dback", "Rejoindre son dernier lieu de mort."},
            {"kiss", "Envoyer un bisou."},
            {"inv", "Consulter son inventaire (lecture seule)."},
            {"ec", "Consulter son Enderchest (lecture seule)."}
        };
        for (var c : commands)
            p.sendMessage(
                    text("&d/marry " + c[0] + " &7— " + c[1])
                            .clickEvent(
                                    ClickEvent.suggestCommand(
                                            "/marry " + c[0].split(" ")[0] + " ")));
        if (p.hasPermission("cliker.marry.admin"))
            p.sendMessage(text("&c/marry objet <joueur> &7— donner un anneau spécial."));
    }

    private void giveRing(CommandSender admin, String name) {
        if (!admin.hasPermission("cliker.marry.admin")) {
            msg(admin, "permission");
            return;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            msg(admin, "hors-ligne");
            return;
        }
        if (!ready) {
            msg(admin, "attente");
            return;
        }
        claims.store()
                .enqueue(
                        target.getUniqueId(),
                        "marry-ring:" + UUID.randomUUID(),
                        ClaimService.serialize(ring()))
                .whenComplete(
                        (v, e) ->
                                sync(
                                        () -> {
                                            if (e != null) {
                                                failure(e);
                                                msg(admin, "erreur");
                                                return;
                                            }
                                            claims.notifyAndDeliver(target.getUniqueId());
                                            msg(admin, "objet-donne", "joueur", target.getName());
                                        }));
    }

    private void propose(Player p, String name) {
        if (!playable(p)) return;
        boolean special = !settings.season(clock);
        if (special && !hasRing(p)) {
            msg(p, "saison");
            return;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (!available(target) || !p.canSee(target) || !target.hasPermission("cliker.marry.use")) {
            msg(p, "hors-ligne");
            return;
        }
        if (p.equals(target)) {
            msg(p, "soi-meme");
            return;
        }
        if (isolated(target)) {
            msg(p, "isolation");
            return;
        }
        if (!action(p) || !begin(p)) return;
        store.bond(p.getUniqueId())
                .thenCombine(store.bond(target.getUniqueId()), (a, b) -> a != null || b != null)
                .whenComplete(
                        (married, error) ->
                                sync(
                                        () -> {
                                            busy.remove(p.getUniqueId());
                                            if (!current(p)) return;
                                            if (error != null) {
                                                failure(error);
                                                msg(p, "erreur");
                                                return;
                                            }
                                            if (married) {
                                                msg(p, "deja-marie");
                                                return;
                                            }
                                            if (!current(target)
                                                    || !available(target)
                                                    || isolated(target)
                                                    || isolated(p)) {
                                                msg(p, "hors-ligne");
                                                return;
                                            }
                                            if (special && !hasRing(p)) {
                                                msg(p, "saison");
                                                return;
                                            }
                                            expire();
                                            if (inProposal(p.getUniqueId())
                                                    || inProposal(target.getUniqueId())) {
                                                msg(p, "demande-occupee");
                                                return;
                                            }
                                            Proposal request =
                                                    new Proposal(
                                                            p.getUniqueId(),
                                                            p.getName(),
                                                            target.getUniqueId(),
                                                            clock.millis()
                                                                    + settings.requestSeconds
                                                                            * 1000,
                                                            special);
                                            proposals.put(target.getUniqueId(), request);
                                            msg(
                                                    p,
                                                    "demande-envoyee",
                                                    "joueur",
                                                    target.getName(),
                                                    "secondes",
                                                    settings.requestSeconds);
                                            target.sendMessage(
                                                    line("demande-recue", "joueur", p.getName())
                                                            .append(
                                                                    text(settings.yaml.getString(
                                                                                    "messages.accepter",
                                                                                    "&a[Accepter]"))
                                                                            .clickEvent(
                                                                                    ClickEvent
                                                                                            .runCommand(
                                                                                                    "/marry"
                                                                                                        + " accept"
                                                                                                        + " "
                                                                                                            + p
                                                                                                                    .getName())))
                                                            .append(Component.space())
                                                            .append(
                                                                    text(settings.yaml.getString(
                                                                                    "messages.refuser",
                                                                                    "&c[Refuser]"))
                                                                            .clickEvent(
                                                                                    ClickEvent
                                                                                            .runCommand(
                                                                                                    "/marry"
                                                                                                        + " deny"
                                                                                                        + " "
                                                                                                            + p
                                                                                                                    .getName()))));
                                        }));
    }

    private boolean inProposal(UUID owner) {
        return proposals.containsKey(owner)
                || proposals.values().stream().anyMatch(r -> r.from().equals(owner));
    }

    private Proposal proposal(Player p, String name) {
        Proposal request = proposals.get(p.getUniqueId());
        if (request == null
                || !request.name().equalsIgnoreCase(name)
                || request.deadline() <= clock.millis()) {
            msg(p, "expiree");
            return null;
        }
        return request;
    }

    private void deny(Player p, String name) {
        Proposal request = proposal(p, name);
        if (request == null) return;
        proposals.remove(p.getUniqueId());
        msg(p, "annulee");
        Player other = Bukkit.getPlayer(request.from());
        if (other != null) msg(other, "annulee");
    }

    private void accept(Player p, String name) {
        Proposal request = proposal(p, name);
        if (request == null) return;
        Player other = Bukkit.getPlayer(request.from());
        if (!available(other) || !current(other) || !other.hasPermission("cliker.marry.use")) {
            msg(p, "hors-ligne");
            return;
        }
        if (!playable(p) || !playable(other)) return;
        if ((request.special() || !settings.season(clock)) && !hasRing(other)) {
            msg(p, "saison");
            msg(other, "saison");
            return;
        }
        if (!begin(p)) return;
        proposals.remove(p.getUniqueId());
        store.marry(other.getUniqueId(), p.getUniqueId(), clock.millis())
                .whenComplete(
                        (ok, error) ->
                                sync(
                                        () -> {
                                            busy.remove(p.getUniqueId());
                                            if (error != null) failure(error);
                                            for (UUID owner :
                                                    List.of(p.getUniqueId(), other.getUniqueId())) {
                                                Player player = Bukkit.getPlayer(owner);
                                                if (player == null) continue;
                                                if (error != null) msg(player, "erreur");
                                                else if (!ok) msg(player, "deja-marie");
                                                else {
                                                    msg(
                                                            player,
                                                            "maries",
                                                            "joueur",
                                                            owner.equals(p.getUniqueId())
                                                                    ? other.getName()
                                                                    : p.getName());
                                                    player.playSound(
                                                            player.getLocation(),
                                                            Sound.ENTITY_PLAYER_LEVELUP,
                                                            0.6f,
                                                            1.4f);
                                                }
                                            }
                                        }));
    }

    private void divorce(Player p, SocialStore.Bond bond, String[] args) {
        Confirmation confirmation = divorces.get(p.getUniqueId());
        if (args.length != 2
                || !args[1].equalsIgnoreCase("confirmer")
                || confirmation == null
                || confirmation.deadline() <= clock.millis()
                || !confirmation.bond().equals(bond.id())) {
            divorces.put(p.getUniqueId(), new Confirmation(bond.id(), clock.millis() + 30000));
            p.sendMessage(
                    line("divorce-confirmer", "prix", format(settings.divorceCost))
                            .clickEvent(ClickEvent.suggestCommand("/marry divorce confirmer")));
            return;
        }
        if (!begin(p)) return;
        divorces.remove(p.getUniqueId());
        store.divorce(p.getUniqueId(), bond.id(), settings.divorceCost)
                .whenComplete(
                        (result, error) ->
                                sync(
                                        () -> {
                                            busy.remove(p.getUniqueId());
                                            if (error != null) {
                                                failure(error);
                                                if (current(p)) msg(p, "erreur");
                                                return;
                                            }
                                            Player actor = Bukkit.getPlayer(p.getUniqueId());
                                            if (actor != null)
                                                msg(
                                                        actor,
                                                        result == 2
                                                                ? "divorce"
                                                                : result == 1
                                                                        ? "divorce-fonds"
                                                                        : "non-marie",
                                                        "prix",
                                                        format(settings.divorceCost));
                                            if (result == 2) {
                                                Player partner = Bukkit.getPlayer(bond.partner());
                                                if (partner != null)
                                                    msg(
                                                            partner,
                                                            "divorce-partenaire",
                                                            "joueur",
                                                            p.getName());
                                                closeViews(p.getUniqueId(), bond.partner());
                                            }
                                        }));
    }

    private String partnerName(UUID owner) {
        Player online = Bukkit.getPlayer(owner);
        if (online != null) return online.getName();
        var cached = Bukkit.getOfflinePlayer(owner);
        return Objects.requireNonNullElse(cached.getName(), owner.toString());
    }

    private void partnerAction(Player p, SocialStore.Bond bond, String sub) {
        if (!playable(p)) return;
        if (sub.equals("dback")) {
            deathBack(p, bond);
            return;
        }
        Player other = Bukkit.getPlayer(bond.partner());
        if (!available(other) || !p.canSee(other)) {
            msg(p, "hors-ligne");
            return;
        }
        if (isolated(other)) {
            msg(p, "isolation");
            return;
        }
        switch (sub) {
            case "tp" -> {
                if (action(p)) travel(p, other.getLocation(), bond.id());
            }
            case "s" -> {
                if (!other.hasPermission("cliker.marry.teleport")) {
                    msg(p, "hors-ligne");
                    return;
                }
                if (action(p)) {
                    if (travel(other, p.getLocation(), bond.id())) msg(p, "teleportation");
                }
            }
            case "gift" -> gift(p, other, bond);
            case "inv", "ec" -> snapshot(p, other, sub.equals("ec"));
            case "kiss" -> {
                if (!action(p)) return;
                msg(p, "bisou", "joueur", other.getName());
                msg(other, "bisou-recu", "joueur", p.getName());
                for (Player target : List.of(p, other))
                    target.spawnParticle(
                            Particle.HEART,
                            target.getLocation().add(0, 1.8, 0),
                            5,
                            0.3,
                            0.2,
                            0.3,
                            0);
            }
        }
    }

    private boolean travel(Player p, Location destination, String expectedBond) {
        if (!available(p) || !playable(p)) return false;
        if (destination.getWorld() == null || !destination.getWorld().equals(p.getWorld())) {
            msg(p, "monde");
            return false;
        }
        if (zones != null && !zones.canSocialTravel(p, destination)) {
            msg(p, "zone");
            return false;
        }
        if (!Double.isFinite(destination.getX())
                || !Double.isFinite(destination.getY())
                || !Double.isFinite(destination.getZ())
                || !destination.getWorld().getWorldBorder().isInside(destination)) {
            msg(p, "dangereux");
            return false;
        }
        // Un seul chargement asynchrone par joueur, sans génération de terrain.
        if (!destination
                .getWorld()
                .isChunkLoaded(destination.getBlockX() >> 4, destination.getBlockZ() >> 4)) {
            if (!begin(p)) return false;
            Location saved = destination.clone();
            msg(p, "chargement-tp");
            destination
                    .getWorld()
                    .getChunkAtAsync(
                            destination.getBlockX() >> 4, destination.getBlockZ() >> 4, false)
                    .thenCompose(
                            chunk ->
                                    chunk == null
                                            ? java.util.concurrent.CompletableFuture
                                                    .<SocialStore.Bond>completedFuture(null)
                                            : store.bond(p.getUniqueId()))
                    .whenComplete(
                            (latest, error) ->
                                    sync(
                                            () -> {
                                                busy.remove(p.getUniqueId());
                                                if (!current(p)) return;
                                                if (error != null) {
                                                    failure(error);
                                                    msg(p, "erreur");
                                                    return;
                                                }
                                                if (latest == null
                                                        || !latest.id().equals(expectedBond)) {
                                                    msg(p, "refus-tp");
                                                    return;
                                                }
                                                if (!p.hasPermission("cliker.marry.teleport")) {
                                                    msg(p, "permission");
                                                    return;
                                                }
                                                travel(p, saved, expectedBond);
                                            }));
            return false;
        }
        for (int dy : new int[] {0, 1, -1, 2, -2}) {
            Location at = destination.clone().add(0, dy, 0);
            if (at.getY() <= at.getWorld().getMinHeight()
                    || at.getY() >= at.getWorld().getMaxHeight() - 1) continue;
            var feet = at.getBlock();
            var head = feet.getRelative(0, 1, 0);
            var floor = feet.getRelative(0, -1, 0);
            Set<Material> hazards =
                    Set.of(
                            Material.FIRE,
                            Material.SOUL_FIRE,
                            Material.LAVA,
                            Material.WATER,
                            Material.MAGMA_BLOCK,
                            Material.CACTUS,
                            Material.CAMPFIRE,
                            Material.SOUL_CAMPFIRE,
                            Material.POWDER_SNOW,
                            Material.SWEET_BERRY_BUSH,
                            Material.WITHER_ROSE,
                            Material.POINTED_DRIPSTONE);
            if (!feet.isPassable()
                    || !head.isPassable()
                    || !floor.getType().isSolid()
                    || hazards.contains(feet.getType())
                    || hazards.contains(head.getType())
                    || hazards.contains(floor.getType())) continue;
            boolean ok = p.teleport(at, PlayerTeleportEvent.TeleportCause.PLUGIN);
            msg(p, ok ? "teleportation" : "refus-tp");
            return ok;
        }
        msg(p, "dangereux");
        return false;
    }

    private void deathBack(Player p, SocialStore.Bond bond) {
        if (!action(p) || !begin(p)) return;
        store.death(bond.partner())
                .whenComplete(
                        (death, error) ->
                                sync(
                                        () -> {
                                            busy.remove(p.getUniqueId());
                                            if (!current(p)) return;
                                            if (error != null) {
                                                failure(error);
                                                msg(p, "erreur");
                                                return;
                                            }
                                            if (death == null
                                                    || Bukkit.getWorld(death.world()) == null) {
                                                msg(p, "mort-absente");
                                                return;
                                            }
                                            // Seuls le monde normal et une destination sûre sont
                                            // accessibles.
                                            travel(
                                                    p,
                                                    new Location(
                                                            Bukkit.getWorld(death.world()),
                                                            death.x(),
                                                            death.y(),
                                                            death.z(),
                                                            death.yaw(),
                                                            death.pitch()),
                                                    bond.id());
                                        }));
    }

    private static boolean fits(Player target, ItemStack item) {
        int remaining = item.getAmount();
        for (var slot : target.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir())
                remaining -=
                        Math.min(item.getMaxStackSize(), target.getInventory().getMaxStackSize());
            else if (slot.isSimilar(item))
                remaining -=
                        Math.max(
                                0,
                                Math.min(
                                                slot.getMaxStackSize(),
                                                target.getInventory().getMaxStackSize())
                                        - slot.getAmount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private void gift(Player p, Player target, SocialStore.Bond bond) {
        ItemStack item = p.getInventory().getItemInMainHand().clone();
        if (item.getType().isAir()) {
            msg(p, "cadeau-vide");
            return;
        }
        if (!fits(target, item)) {
            msg(p, "cadeau-plein");
            return;
        }
        if (claims.isDelivering(target.getUniqueId()) || !action(p)) {
            msg(p, "cadeau-traitement");
            return;
        }
        String payload = ClaimService.serialize(item);
        boolean started =
                outgoing.consumeHeld(
                        p,
                        item,
                        receipt ->
                                store.gift(
                                        p.getUniqueId(),
                                        target.getUniqueId(),
                                        bond.id(),
                                        receipt,
                                        payload),
                        error -> {
                            if (error != null) {
                                failure(error);
                                if (current(p)) msg(p, "erreur");
                                return;
                            }
                            Player sender = Bukkit.getPlayer(p.getUniqueId());
                            if (sender != null)
                                msg(sender, "cadeau-envoye", "joueur", target.getName());
                            Player receiver = Bukkit.getPlayer(target.getUniqueId());
                            if (receiver != null)
                                msg(receiver, "cadeau-recu", "joueur", p.getName());
                            claims.notifyAndDeliver(target.getUniqueId());
                        });
        if (!started) msg(p, "cadeau-traitement");
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    public static boolean isSnapshot(Inventory inventory) {
        return inventory != null
                && inventory.getHolder() instanceof View view
                && view.partner != null;
    }

    private void snapshot(Player p, Player other, boolean ender) {
        View view = new View(other.getUniqueId());
        view.inventory =
                Bukkit.createInventory(
                        view,
                        ender ? 27 : 54,
                        text((ender ? "&5Enderchest — " : "&dInventaire — ") + other.getName()));
        if (ender) {
            ItemStack[] items = other.getEnderChest().getContents();
            for (int i = 0; i < Math.min(27, items.length); i++)
                view.inventory.setItem(i, cloneItem(items[i]));
        } else {
            ItemStack[] items = other.getInventory().getStorageContents();
            for (int i = 0; i < items.length; i++) view.inventory.setItem(i, cloneItem(items[i]));
            var armor = other.getInventory().getArmorContents();
            for (int i = 0; i < armor.length; i++)
                view.inventory.setItem(45 + i, cloneItem(armor[i]));
            view.inventory.setItem(50, cloneItem(other.getInventory().getItemInOffHand()));
            view.closeSlot = 53;
            view.inventory.setItem(
                    53, icon(Material.BARRIER, "&cFermer", "&7Consultation uniquement."));
        }
        p.openInventory(view.inventory);
        msg(p, "lecture");
    }

    private ItemStack icon(Material type, String title, String... lore) {
        ItemStack item = new ItemStack(type);
        var meta = item.getItemMeta();
        meta.displayName(text(title));
        meta.lore(Arrays.stream(lore).map(this::text).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void recommend(Player p, String[] args) {
        if (args.length != 1) {
            p.sendMessage(text("&d/rc <joueur> &7— +1 recommandation, une fois par heure."));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (!available(target) || !p.canSee(target)) {
            msg(p, "hors-ligne");
            return;
        }
        if (p.equals(target)) {
            msg(p, "soi-meme");
            return;
        }
        long ticks = Math.max(0, p.getStatistic(Statistic.PLAY_ONE_MINUTE));
        if (ticks < settings.requiredTicks) {
            msg(
                    p,
                    "jeu-insuffisant",
                    "heures",
                    String.format(Locale.FRANCE, "%.1f", settings.requiredTicks / 72000d));
            return;
        }
        long wait = recommendAgain.getOrDefault(p.getUniqueId(), 0L) - clock.millis();
        if (wait > 0) {
            msg(p, "rc-delai", "temps", duration(wait));
            return;
        }
        if (!begin(p)) return;
        store.recommend(
                        p.getUniqueId(),
                        target.getUniqueId(),
                        target.getName(),
                        ticks,
                        settings.requiredTicks,
                        clock.millis(),
                        settings.recommendationCooldown,
                        settings.recommendationXP)
                .whenComplete(
                        (result, error) ->
                                sync(
                                        () -> {
                                            busy.remove(p.getUniqueId());
                                            if (error != null) {
                                                failure(error);
                                                if (current(p)) msg(p, "erreur");
                                                return;
                                            }
                                            if (Bukkit.getPlayer(p.getUniqueId()) != null)
                                                recommendAgain.put(
                                                        p.getUniqueId(),
                                                        clock.millis()
                                                                + (result.accepted()
                                                                        ? settings.recommendationCooldown
                                                                        : result.waitMillis()));
                                            if (!result.accepted()) {
                                                if (current(p))
                                                    msg(
                                                            p,
                                                            "rc-delai",
                                                            "temps",
                                                            duration(result.waitMillis()));
                                                return;
                                            }
                                            if (Bukkit.getPlayer(target.getUniqueId()) != null)
                                                popularity.put(
                                                        target.getUniqueId(), result.count());
                                            Player recommender = Bukkit.getPlayer(p.getUniqueId());
                                            if (recommender != null)
                                                msg(
                                                        recommender,
                                                        "rc-envoyee",
                                                        "joueur",
                                                        target.getName(),
                                                        "xp",
                                                        settings.recommendationXP);
                                            Player receiver =
                                                    Bukkit.getPlayer(target.getUniqueId());
                                            if (receiver != null) {
                                                msg(
                                                        receiver,
                                                        "rc-recue",
                                                        "joueur",
                                                        p.getName(),
                                                        "xp",
                                                        settings.recommendationXP);
                                                try {
                                                    receiver.playSound(
                                                            receiver.getLocation(),
                                                            Sound.valueOf(
                                                                    settings.yaml.getString(
                                                                            "popularite.son",
                                                                            "ENTITY_PLAYER_LEVELUP")),
                                                            0.7f,
                                                            1.2f);
                                                } catch (IllegalArgumentException ignored) {
                                                }
                                            }
                                            ledger.flushXP(target.getUniqueId());
                                            var rank = settings.rank(result.count());
                                            if (settings.rank(result.count() - 1).threshold()
                                                    != rank.threshold())
                                                Bukkit.broadcast(
                                                        line(
                                                                "echelon",
                                                                "joueur",
                                                                target.getName(),
                                                                "echelon",
                                                                rank.name(),
                                                                "points",
                                                                result.count()));
                                        }));
    }

    private static String format(long number) {
        return java.text.NumberFormat.getIntegerInstance(Locale.FRANCE).format(number);
    }

    private static String duration(long millis) {
        long seconds = Math.max(1, (millis + 999) / 1000);
        return (seconds / 3600 > 0 ? seconds / 3600 + " h " : "")
                + (seconds % 3600 / 60)
                + " min "
                + seconds % 60
                + " s";
    }

    public Component hover(UUID owner) {
        Long points = popularity.get(owner);
        if (!settings.yaml.getBoolean("popularite.actif", true)) return Component.empty();
        if (points == null) return text("&dPopularité &7— chargement…");
        return text(
                "&dPopularité : &f"
                        + points
                        + " recommandation(s)\n&dÉchelon : &f"
                        + settings.rank(points).name()
                        + "\n&7/rc <joueur> &8— &7/echelons");
    }

    private void ranks(Player p) {
        int size = Math.min(54, ((settings.ranks.size() + 8) / 9) * 9 + 9);
        View view = new View(null);
        view.inventory = Bukkit.createInventory(view, size, text("&dÉchelons de popularité"));
        long count = popularity.getOrDefault(p.getUniqueId(), 0L);
        for (int i = 0; i < settings.ranks.size(); i++) {
            var rank = settings.ranks.get(i);
            boolean unlocked = count >= rank.threshold();
            view.inventory.setItem(
                    i,
                    icon(
                            unlocked ? Material.EMERALD : Material.GRAY_DYE,
                            (unlocked ? "&a" : "&7") + rank.name(),
                            "&7Seuil : &f" + rank.threshold() + " recommandations",
                            "&7Ta popularité : &f" + count,
                            unlocked
                                    ? "&aÉchelon atteint"
                                    : "&7Encore "
                                            + (rank.threshold() - count)
                                            + " recommandations"));
        }
        view.inventory.setItem(
                size - 5,
                icon(
                        Material.BOOK,
                        "&dComment recommander ?",
                        "&f/rc <joueur>",
                        "&7Temps de jeu requis : "
                                + String.format(
                                        Locale.FRANCE, "%.1f", settings.requiredTicks / 72000d)
                                + " h",
                        "&7Une recommandation toutes les "
                                + duration(settings.recommendationCooldown),
                        "&7Le joueur recommandé gagne " + settings.recommendationXP + " XP."));
        view.closeSlot = size - 1;
        view.inventory.setItem(size - 1, icon(Material.BARRIER, "&cFermer"));
        p.openInventory(view.inventory);
    }

    private void load(Player p) {
        Object token = new Object();
        sessions.put(p.getUniqueId(), token);
        store.points(p.getUniqueId())
                .whenComplete(
                        (points, error) ->
                                sync(
                                        () -> {
                                            if (!current(p)
                                                    || sessions.get(p.getUniqueId()) != token)
                                                return;
                                            if (error != null) failure(error);
                                            else popularity.put(p.getUniqueId(), points);
                                        }));
    }

    private void expire() {
        long now = clock.millis();
        var iterator = proposals.values().iterator();
        while (iterator.hasNext()) {
            Proposal r = iterator.next();
            if (r.deadline() > now) continue;
            iterator.remove();
            for (UUID owner : List.of(r.from(), r.target())) {
                Player p = Bukkit.getPlayer(owner);
                if (p != null) msg(p, "expiration");
            }
        }
        divorces.values().removeIf(c -> c.deadline() <= now);
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        if (ready) load(e.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        UUID owner = e.getPlayer().getUniqueId();
        var iterator = proposals.values().iterator();
        while (iterator.hasNext()) {
            var r = iterator.next();
            if (!r.from().equals(owner) && !r.target().equals(owner)) continue;
            iterator.remove();
            Player partner = Bukkit.getPlayer(r.from().equals(owner) ? r.target() : r.from());
            if (partner != null) msg(partner, "annulee");
        }
        sessions.remove(owner);
        popularity.remove(owner);
        recommendAgain.remove(owner);
        divorces.remove(owner);
        actions.remove(owner);
        // Une transaction déjà engagée reste verrouillée jusqu’à sa réponse SQL.
        closeViews(owner, owner);
    }

    private void closeViews(UUID a, UUID b) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            var top = p.getOpenInventory().getTopInventory();
            var h = top == null ? null : top.getHolder();
            if (h instanceof View v
                    && v.partner != null
                    && (v.partner.equals(a)
                            || v.partner.equals(b)
                            || p.getUniqueId().equals(a)
                            || p.getUniqueId().equals(b))) p.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(PlayerDeathEvent e) {
        if (!ready) return;
        Location at = e.getEntity().getLocation();
        store.death(
                        e.getEntity().getUniqueId(),
                        new SocialStore.Death(
                                at.getWorld().getName(),
                                at.getX(),
                                at.getY(),
                                at.getZ(),
                                at.getYaw(),
                                at.getPitch()))
                .whenComplete(
                        (v, error) -> {
                            if (error != null) failure(error);
                        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof View view)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == e.getView().getTopInventory()
                && e.getRawSlot() == view.closeSlot
                && (e.getClick() == ClickType.LEFT || e.getClick() == ClickType.RIGHT)) {
            var top = e.getView().getTopInventory();
            var p = e.getWhoClicked();
            sync(
                    () -> {
                        if (p.getOpenInventory().getTopInventory() == top) p.closeInventory();
                    });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices = new ArrayList<>();
        String root = command.getName();
        if (root.equals("marry") && args.length == 1) {
            choices.addAll(
                    List.of(
                            "help", "demand", "accept", "deny", "divorce", "tp", "s", "gift",
                            "dback", "kiss", "inv", "ec"));
            if (sender.hasPermission("cliker.marry.admin")) choices.add("objet");
        } else if ((root.equals("rc") && args.length == 1)
                || (root.equals("marry")
                        && args.length == 2
                        && List.of("demand", "demande", "accept", "deny", "refuser", "objet")
                                .contains(args[0].toLowerCase(Locale.ROOT)))) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (!p.equals(sender) && (!(sender instanceof Player viewer) || viewer.canSee(p)))
                    choices.add(p.getName());
        } else if (root.equals("marry") && args.length == 2 && args[0].equalsIgnoreCase("divorce"))
            choices.add("confirmer");
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }

    public void shutdown() {
        stopped = true;
        if (ticker != null) ticker.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) {
            var top = p.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof View) p.closeInventory();
        }
        HandlerList.unregisterAll(this);
        proposals.clear();
        divorces.clear();
        popularity.clear();
        sessions.clear();
        actions.clear();
        recommendAgain.clear();
        busy.clear();
    }
}
