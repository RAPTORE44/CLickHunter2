package fr.cliker.clikerv2.features;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.bag.*;
import fr.cliker.clikerv2.buyzone.*;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public final class Talismans implements Listener, CommandExecutor {
    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final BagManager bags;
    private final Menus menus;
    private final Settings settings;
    private final NamespacedKey tag, health;
    private final CompletableFuture<?> ready;
    private BukkitTask task;
    private boolean stopped;
    private final Set<UUID> opening = new HashSet<>();
    private final Set<UUID> activating = new HashSet<>();
    private final TalismanRegistry registry;

    public Talismans(JavaPlugin p, DatabaseManager db, BagManager bags, Menus menus) {
        plugin = p;
        this.db = db;
        registry = new TalismanRegistry(db);
        this.bags = bags;
        this.menus = menus;
        settings = new Settings(p, "progression/talismans.yml");
        migrateTierDefaults(settings);
        tag = new NamespacedKey(p, "collection_talisman");
        health = new NamespacedKey(p, "talisman_vitalite");
        ready =
                db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.execute(
                                        "CREATE TABLE IF NOT EXISTS talisman_collection(owner TEXT"
                                            + " NOT NULL,id TEXT NOT NULL,PRIMARY KEY(owner,id))");
                                return null;
                            } catch (SQLException e) {
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        });
        Bukkit.getServicesManager()
                .register(Talismans.class, this, p, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(this, p);
        p.getCommand("tallisement").setExecutor(this);
        task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                p, () -> Bukkit.getOnlinePlayers().forEach(this::health), 20, 20);
    }

    static void migrateTierDefaults(Settings settings) {
        if (settings.yaml.getInt("schema-version", 1) >= 2) return;
        if (settings.yaml.getInt("tiers.5", MobTier.values().length) == 40)
            settings.yaml.set("tiers.5", MobTier.values().length);
        settings.yaml.set("schema-version", 2);
        settings.save();
    }

    private int tierZone(int tier) {
        return settings.yaml.getInt(
                "tiers." + tier, new int[] {1, 5, 10, 15, MobTier.values().length}[tier - 1]);
    }

    public static double power(String kind, int tier) {
        var service = Bukkit.getServicesManager().load(Talismans.class);
        if (service == null || tier <= 0) return 0;
        double value =
                service.settings.yaml.getDouble(
                        "generaux." + kind + ".bonus-par-tier", kind.equals("vitalite") ? 2 : 5);
        return Double.isFinite(value) && value >= 0 ? Math.min(1000, value * Math.min(5, tier)) : 0;
    }

    public static double globalBonus(Player p, String type) {
        var service = Bukkit.getServicesManager().load(Talismans.class);
        return service == null ? 0 : power(type, service.bags.generalTier(p.getUniqueId(), type));
    }

    public static double bossPower(MobTier t) {
        var service = Bukkit.getServicesManager().load(Talismans.class);
        double value =
                service == null ? 10 : service.settings.yaml.getDouble("boss.bonus-pourcent", 10);
        return Double.isFinite(value) && value >= 0 ? Math.min(1000, value) : 10;
    }

    private void health(Player p) {
        var a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a == null || !bags.isLoaded(p.getUniqueId())) return;
        double amount =
                fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)
                        ? 0
                        : power("vitalite", bags.generalTier(p.getUniqueId(), "vitalite"));
        var old =
                a.getModifiers().stream()
                        .filter(m -> m.getKey().equals(health))
                        .findFirst()
                        .orElse(null);
        if (old != null && old.getAmount() == amount) return;
        if (old != null) a.removeModifier(old);
        if (amount > 0)
            a.addModifier(
                    new AttributeModifier(health, amount, AttributeModifier.Operation.ADD_NUMBER));
        if (p.getHealth() > a.getValue()) p.setHealth(a.getValue());
    }

    private static final List<Integer> CELLS =
            java.util.stream.IntStream.range(0, 28)
                    .map(i -> 10 + i / 7 * 9 + i % 7)
                    .boxed()
                    .toList();

    private static String family(String kind) {
        return switch (kind) {
            case "lame" -> "Lame";
            case "richesse" -> "Richesse";
            case "sagesse" -> "Sagesse";
            case "fortune" -> "Fortune";
            case "vitalite" -> "Vitalité";
            case "afk" -> "AFK";
            default -> kind;
        };
    }

    private ItemStack create(String id) {
        if (id.equals("treasure")) return TalismanItem.createTreasureTalisman();
        var split = id.split(":");
        if (split[0].equals("mob")) return TalismanItem.createMobTalisman(MobTier.parse(split[1]));
        if (split[0].equals("afk"))
            return fr.cliker.clikerv2.community.AfkModule.talisman(Integer.parseInt(split[1]));
        String name =
                split[0].equals("boss")
                        ? "&dTalisman du boss — " + MobTier.parse(split[1]).label()
                        : "&bTalisman de " + family(split[1]) + " &f• Tier " + split[2];
        var item =
                Menus.item(
                        Material.NAUTILUS_SHELL,
                        name,
                        "&7Clic droit avec l'objet en main pour l'ajouter.",
                        split[0].equals("boss")
                                ? "&c+"
                                        + bossPower(MobTier.parse(split[1]))
                                        + " % de dégâts contre ce boss."
                                : "&aBonus : +"
                                        + power(split[1], Integer.parseInt(split[2]))
                                        + (split[1].equals("vitalite") ? " PV" : " %"),
                        "&7Ajout définitif : aucun retrait possible.",
                        "&8Un seul bonus par famille : le meilleur tier.");
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private String activeStatus(Player player, String id) {
        if (!bags.registeredTalismans(player.getUniqueId()).contains(id))
            return "&eÀ enregistrer : clic droit avec l'objet en main.";
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(player))
            return "&eEnregistré ; bonus suspendu dans l'avant-poste.";
        String[] parts = id.split(":");
        if (parts[0].equals("general")
                && bags.generalTier(player.getUniqueId(), parts[1]) > Integer.parseInt(parts[2]))
            return "&aAjout permanent ; un tier supérieur est prioritaire.";
        if (parts[0].equals("afk")
                && bags.generalTier(player.getUniqueId(), "afk") > Integer.parseInt(parts[1]))
            return "&aAjout permanent ; un tier AFK supérieur est prioritaire.";
        return "&aACTIF • bonus permanent, sans retrait possible.";
    }

    private void activeBonuses(Player player) {
        var view = menus.create(player, 54, "Talismans • Mes bonus");
        int index = 0;
        view.put(
                4,
                Menus.item(
                        Material.NETHER_STAR,
                        "&a&lMES BONUS",
                        "&7Les ajouts permanents sont actifs automatiquement.",
                        "&7Les anciens talismans des sacs restent compatibles.",
                        "&8Suspendus dans l'avant-poste ; familles non cumulées."),
                null);
        for (String kind : List.of("lame", "richesse", "sagesse", "fortune", "vitalite")) {
            int tier = bags.generalTier(player.getUniqueId(), kind);
            if (tier > 0)
                view.put(
                        CELLS.get(index++),
                        Menus.item(
                                Material.NAUTILUS_SHELL,
                                "&b" + family(kind) + " &f• Tier " + tier,
                                "&a+"
                                        + power(kind, tier)
                                        + (kind.equals("vitalite") ? " PV" : " %"),
                                "&7Le meilleur tier de la famille est retenu."),
                        null);
        }
        for (MobTier mob : MobTier.values()) {
            double bonus = bags.mobDamageBonusPercent(player.getUniqueId(), mob);
            boolean boss = bags.hasBossTalisman(player.getUniqueId(), mob);
            if (bonus > 0 || boss)
                view.put(
                        CELLS.get(index++),
                        Menus.item(
                                Material.NAUTILUS_SHELL,
                                "&e" + mob.label(),
                                "&7Créature : &a+" + Math.round(bonus) + " % de dégâts",
                                "&7Boss : &d+" + (boss ? bossPower(mob) : 0) + " % de dégâts"),
                        null);
        }
        if (bags.hasTreasureTalisman(player.getUniqueId()))
            view.put(
                    CELLS.get(index++),
                    Menus.item(
                            Material.EMERALD,
                            "&6Trésor",
                            "&e+100 Écus &8• &d+1 Gemme &8• &b+25 XP par élimination"),
                    null);
        int afk = bags.generalTier(player.getUniqueId(), "afk");
        if (afk > 0)
            view.put(
                    CELLS.get(index++),
                    Menus.item(
                            Material.CLOCK,
                            "&bAFK • Tier " + afk,
                            "&e+"
                                    + (afk * 10)
                                    + " Écus &8• &b+"
                                    + (afk * 5)
                                    + " XP &8• &d+"
                                    + afk
                                    + " Gemme(s)"),
                    null);
        if (index == 0)
            view.put(
                    22,
                    Menus.item(
                            Material.GRAY_DYE,
                            "&7Aucun bonus pour le moment",
                            "&eAjoute un talisman avec un clic droit en main."),
                    null);
        view.put(49, Menus.item(Material.ARROW, "&eRetour à ma collection"), (p, r) -> open(p, 0));
        view.show(player);
    }

    private double chance(String path, double fallback) {
        double value = settings.yaml.getDouble(path, fallback);
        return Double.isFinite(value) && value >= 0 && value <= 100 ? value : fallback;
    }

    @EventHandler
    public void kill(HuntKillEvent e) {
        var p = Bukkit.getPlayer(e.owner);
        double mult =
                p == null
                        ? 1
                        : Boosts.multiplier(p, "chance")
                                * (1
                                        + power("fortune", bags.generalTier(e.owner, "fortune"))
                                                / 100);
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble(100)
                < chance(
                                e.boss ? "boss.chance-pourcent" : "mobs.chance-pourcent",
                                e.boss ? .02 : .0002)
                        * mult) unlock(e.owner, e.name, (e.boss ? "boss:" : "mob:") + e.tier.id());
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble(100)
                < chance("generaux.chance-pourcent", .002) * mult) {
            int tier = 1;
            for (int n = 1; n <= 5; n++) {
                int zone = tierZone(n);
                if (e.tier.ordinal() + 1 >= zone) tier = n;
            }
            String kind =
                    List.of("lame", "richesse", "sagesse", "fortune", "vitalite")
                            .get(java.util.concurrent.ThreadLocalRandom.current().nextInt(5));
            unlock(e.owner, e.name, "general:" + kind + ":" + tier);
        }
    }

    private void unlock(UUID owner, String name, String id) {
        var item = create(id);
        String payload = ClaimService.serialize(item);
        ready.thenCompose(
                        v ->
                                db.submit(
                                        c -> {
                                            try {
                                                c.setAutoCommit(false);
                                                try (var s =
                                                        c.prepareStatement(
                                                                "INSERT OR IGNORE INTO"
                                                                    + " talisman_collection(owner,id)"
                                                                    + " VALUES(?,?)")) {
                                                    s.setString(1, owner.toString());
                                                    s.setString(2, id);
                                                    if (s.executeUpdate() == 0) {
                                                        c.rollback();
                                                        return false;
                                                    }
                                                }
                                                MailStore.insert(
                                                        c, owner, "talisman:" + id, payload);
                                                c.commit();
                                                return true;
                                            } catch (SQLException error) {
                                                try {
                                                    c.rollback();
                                                } catch (SQLException ignored) {
                                                }
                                                throw new DatabaseManager.DatabaseException(error);
                                            }
                                        }))
                .whenComplete(
                        (ok, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (stopped) return;
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Talisman non attribué",
                                                                error);
                                                return;
                                            }
                                            if (Boolean.TRUE.equals(ok)) {
                                                var p = Bukkit.getPlayer(owner);
                                                if (p != null)
                                                    p.sendMessage(
                                                            "Nouveau talisman débloqué !"
                                                                    + " /tallisement");
                                                Bukkit.getServicesManager()
                                                        .load(ClaimService.class)
                                                        .notifyAndDeliver(owner);
                                            }
                                        }));
    }

    private String id(ItemStack item) {
        return TalismanRegistry.id(item, tag);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void activate(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                        && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK))
            return;
        Player player = event.getPlayer();
        String id = id(player.getInventory().getItemInMainHand());
        if (id == null) return;
        event.setCancelled(true);
        if (stopped || !bags.isLoaded(player.getUniqueId())) {
            player.sendMessage("§eChargement de tes talismans, réessaie dans un instant.");
            return;
        }
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)) {
            player.sendMessage("§eRetourne au /spawn pour enregistrer ce talisman.");
            return;
        }
        if (bags.registeredTalismans(player.getUniqueId()).contains(id)) {
            player.sendMessage("§eCe talisman est déjà enregistré. Ton exemplaire est conservé.");
            return;
        }
        var outgoing = Bukkit.getServicesManager().load(OutgoingItems.class);
        if (outgoing == null) {
            player.sendMessage("§cEnregistrement indisponible. Ton talisman est conservé.");
            return;
        }
        UUID owner = player.getUniqueId();
        if (!activating.add(owner)) return;
        ItemStack item = player.getInventory().getItemInMainHand().clone();
        item.setAmount(1);
        String payload = ClaimService.serialize(item);
        boolean started =
                outgoing.consumeHeld(
                        player,
                        item,
                        receipt ->
                                ready.thenCompose(
                                        v -> registry.register(owner, id, receipt, payload)),
                        error -> {
                            activating.remove(owner);
                            if (stopped
                                    || !fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(player))
                                return;
                            if (error != null) {
                                player.sendMessage(
                                        "§cAjout non confirmé : l'objet est conservé ou récupérable"
                                            + " dans /claim.");
                                return;
                            }
                            bags.registerTalisman(owner, id);
                            health(player);
                            player.sendMessage(
                                    "§aTalisman ajouté définitivement à ta collection !"
                                        + " §7/tallisement");
                            fr.cliker.clikerv2.community.ToastNotifications.show(
                                    player,
                                    Material.NAUTILUS_SHELL,
                                    "Talisman enregistré",
                                    "Bonus permanent • aucun retrait possible");
                        });
        if (!started) {
            activating.remove(owner);
            player.sendMessage(
                    "§eAttends la fin du transfert en cours avant d'ajouter ton talisman.");
        }
    }

    @EventHandler
    public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
        opening.remove(event.getPlayer().getUniqueId());
        activating.remove(event.getPlayer().getUniqueId());
    }

    public CompletableFuture<Set<String>> discoveries(Player player) {
        var owned = new HashSet<String>();
        for (var item : player.getInventory().getContents()) {
            String value = id(item);
            if (value != null) owned.add(value);
        }
        if (bags.isLoaded(player.getUniqueId()))
            for (int bag = 0; bag < BagManager.MAX_CAPACITY; bag++)
                for (var item : bags.contents(player.getUniqueId(), bag)) {
                    String value = id(item);
                    if (value != null) owned.add(value);
                }
        UUID owner = player.getUniqueId();
        return ready.thenCompose(
                ignored ->
                        db.submit(
                                connection -> {
                                    try (var statement =
                                            connection.prepareStatement(
                                                    "SELECT id FROM talisman_collection WHERE"
                                                            + " owner=?")) {
                                        statement.setString(1, owner.toString());
                                        try (var result = statement.executeQuery()) {
                                            while (result.next()) owned.add(result.getString(1));
                                        }
                                        return Set.copyOf(owned);
                                    } catch (SQLException error) {
                                        throw new DatabaseManager.DatabaseException(error);
                                    }
                                }));
    }

    private void open(Player p, int page) {
        open(p, page, "debloques");
    }

    private void categories(Player player) {
        var view = menus.create(player, 54, "Talismans • À découvrir");
        String[] kinds = {
            "mob", "boss", "lame", "richesse", "sagesse", "fortune", "vitalite", "afk", "treasure"
        };
        String[] labels = {
            "Créatures",
            "Boss",
            "Lame",
            "Richesse",
            "Sagesse",
            "Fortune",
            "Vitalité",
            "AFK",
            "Trésor"
        };
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34, 40};
        view.put(
                4,
                Menus.item(
                        Material.BOOK,
                        "&d&lÀ DÉCOUVRIR",
                        "&7Choisis une famille pour consulter les talismans manquants."),
                null);
        for (int i = 0; i < kinds.length; i++) {
            String kind = kinds[i];
            view.put(
                    slots[i],
                    Menus.item(
                            Material.BOOK,
                            "&e" + labels[i],
                            "&7Voir les talismans non découverts.",
                            "&eClique pour consulter."),
                    (p, r) -> open(p, 0, kind));
        }
        view.put(49, Menus.item(Material.ARROW, "&eRetour à ma collection"), (p, r) -> open(p, 0));
        view.show(player);
    }

    private static List<String> catalog() {
        List<String> ids = new ArrayList<>();
        for (var mob : MobTier.values()) {
            ids.add("mob:" + mob.id());
            ids.add("boss:" + mob.id());
        }
        for (String kind : List.of("lame", "richesse", "sagesse", "fortune", "vitalite"))
            for (int tier = 1; tier <= 5; tier++) ids.add("general:" + kind + ":" + tier);
        for (int tier = 1; tier <= 4; tier++) ids.add("afk:" + tier);
        ids.add("treasure");
        return ids;
    }

    private void open(Player player, int page, String category) {
        UUID owner = player.getUniqueId();
        if (!bags.isLoaded(owner)) {
            player.sendMessage("§eChargement de tes talismans, réessaie dans un instant.");
            return;
        }
        if (!opening.add(owner)) return;
        var previous = player.getOpenInventory().getTopInventory();
        discoveries(player)
                .whenComplete(
                        (known, error) -> {
                            if (stopped || !plugin.isEnabled()) return;
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        opening.remove(owner);
                                        if (stopped
                                                || !fr.cliker.clikerv2.runtime.PlayerSessions
                                                        .isCurrent(player)
                                                || player.getOpenInventory().getTopInventory()
                                                        != previous) return;
                                        if (error != null) {
                                            player.sendMessage(
                                                    "§cCollection indisponible. Réessaie plus"
                                                        + " tard.");
                                            return;
                                        }
                                        Set<String> registered = bags.registeredTalismans(owner);
                                        Set<String> unlocked = new HashSet<>(known);
                                        unlocked.addAll(registered);
                                        List<String> ids = new ArrayList<>(catalog());
                                        ids.removeIf(
                                                id ->
                                                        category.equals("debloques")
                                                                ? !unlocked.contains(id)
                                                                : unlocked.contains(id)
                                                                        || !(category.equals("mob")
                                                                                        || category
                                                                                                .equals(
                                                                                                        "boss")
                                                                                        || category
                                                                                                .equals(
                                                                                                        "afk")
                                                                                ? id.startsWith(
                                                                                        category
                                                                                                + ":")
                                                                                : category.equals(
                                                                                                "treasure")
                                                                                        ? id.equals(
                                                                                                "treasure")
                                                                                        : id
                                                                                                .startsWith(
                                                                                                        "general:"
                                                                                                                + category
                                                                                                                + ":")));
                                        ids.sort(
                                                Comparator.comparingInt(
                                                        id -> registered.contains(id) ? 0 : 1));
                                        int pages = Math.max(1, (ids.size() + 27) / 28),
                                                selected = Math.max(0, Math.min(page, pages - 1));
                                        var view =
                                                menus.create(
                                                        player,
                                                        54,
                                                        (category.equals("debloques")
                                                                        ? "Mes talismans"
                                                                        : "Talismans • À découvrir")
                                                                + " • "
                                                                + (selected + 1)
                                                                + "/"
                                                                + pages);
                                        view.put(
                                                4,
                                                Menus.item(
                                                        Material.NETHER_STAR,
                                                        "&d&lMA COLLECTION",
                                                        "&a"
                                                                + registered.size()
                                                                + " talisman(s) enregistré(s) &7/ "
                                                                + catalog().size(),
                                                        "&7Clic droit avec le talisman en main :"
                                                            + " ajout définitif.",
                                                        "&cAucun retrait possible ; un doublon"
                                                            + " n'est pas consommé."),
                                                null);
                                        for (int i = 0;
                                                i < 28 && selected * 28 + i < ids.size();
                                                i++) {
                                            String id = ids.get(selected * 28 + i);
                                            ItemStack item;
                                            if (unlocked.contains(id)) {
                                                item = create(id);
                                                var meta = item.getItemMeta();
                                                var lore =
                                                        new ArrayList<
                                                                net.kyori.adventure.text.Component>(
                                                                meta.lore() == null
                                                                        ? List.of()
                                                                        : meta.lore());
                                                lore.add(
                                                        fr.cliker.clikerv2.daily.DailySettings
                                                                .color("&8────────────────────"));
                                                lore.add(
                                                        fr.cliker.clikerv2.daily.DailySettings
                                                                .color(activeStatus(player, id)));
                                                meta.lore(lore);
                                                item.setItemMeta(meta);
                                            } else {
                                                String[] parts = id.split(":");
                                                String hint =
                                                        parts[0].equals("general")
                                                                ? family(parts[1])
                                                                        + " • Tier "
                                                                        + parts[2]
                                                                : parts[0].equals("afk")
                                                                        ? "AFK • Tier " + parts[1]
                                                                        : id.equals("treasure")
                                                                                ? "Trésor"
                                                                                : "Zone "
                                                                                        + (MobTier
                                                                                                        .parse(
                                                                                                                parts[
                                                                                                                        1])
                                                                                                        .ordinal()
                                                                                                + 1);
                                                item =
                                                        Menus.item(
                                                                Material.GRAY_DYE,
                                                                "&8??? &7• " + hint,
                                                                "&7Talisman non découvert.",
                                                                "&eObtiens l'objet pour découvrir"
                                                                    + " son bonus.");
                                            }
                                            view.put(CELLS.get(i), item, null);
                                        }
                                        view.put(
                                                45,
                                                Menus.item(
                                                        selected > 0
                                                                ? Material.ARROW
                                                                : Material.GRAY_DYE,
                                                        "&ePage précédente"),
                                                selected > 0
                                                        ? (p, r) -> open(p, selected - 1, category)
                                                        : null);
                                        view.put(
                                                47,
                                                Menus.item(
                                                        Material.BOOK,
                                                        "&dTalismans non débloqués",
                                                        "&7Parcourir les familles et leurs"
                                                            + " découvertes."),
                                                (p, r) -> categories(p));
                                        view.put(
                                                48,
                                                Menus.item(
                                                        Material.PAPER,
                                                        "&bComment les utiliser ?",
                                                        "&71. Tiens le talisman dans ta main"
                                                            + " principale.",
                                                        "&72. Fais un clic droit : un exemplaire"
                                                            + " est consommé.",
                                                        "&73. Le bonus reste enregistré"
                                                            + " définitivement.",
                                                        "&7Le meilleur tier d'une famille"
                                                            + " s'applique.",
                                                        "&8Les anciens exemplaires dans les sacs"
                                                            + " restent compatibles."),
                                                null);
                                        view.put(
                                                49,
                                                Menus.item(
                                                        Material.ARROW,
                                                        "&eRetour",
                                                        category.equals("debloques")
                                                                ? "&7Retour au menu principal."
                                                                : "&7Retour à ma collection."),
                                                (p, r) -> {
                                                    if (category.equals("debloques"))
                                                        p.performCommand("menu");
                                                    else open(p, 0);
                                                });
                                        view.put(
                                                51,
                                                Menus.item(
                                                        Material.NAUTILUS_SHELL,
                                                        "&aMes bonus actifs",
                                                        "&7Voir les effets actuellement"
                                                            + " appliqués."),
                                                (p, r) -> activeBonuses(p));
                                        view.put(
                                                53,
                                                Menus.item(
                                                        selected + 1 < pages
                                                                ? Material.ARROW
                                                                : Material.GRAY_DYE,
                                                        "&ePage suivante"),
                                                selected + 1 < pages
                                                        ? (p, r) -> open(p, selected + 1, category)
                                                        : null);
                                        if (ids.isEmpty())
                                            view.put(
                                                    22,
                                                    Menus.item(
                                                            Material.GRAY_DYE,
                                                            "&7Aucun talisman dans cette liste",
                                                            "&eAjoute tes premiers talismans par"
                                                                + " clic droit en main."),
                                                    null);
                                        view.show(player);
                                    });
                        });
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p) open(p, 0);
        return true;
    }

    public void shutdown() {
        stopped = true;
        task.cancel();
        for (var p : Bukkit.getOnlinePlayers()) {
            var a = p.getAttribute(Attribute.MAX_HEALTH);
            if (a != null
                    && a.getModifiers().stream()
                                    .filter(m -> m.getKey().equals(health))
                                    .findFirst()
                                    .orElse(null)
                            != null) a.removeModifier(health);
        }
        Bukkit.getServicesManager().unregister(Talismans.class, this);
        HandlerList.unregisterAll(this);
    }
}
