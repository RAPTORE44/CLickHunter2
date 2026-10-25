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

    public Talismans(JavaPlugin p, DatabaseManager db, BagManager bags, Menus menus) {
        plugin = p;
        this.db = db;
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

    private ItemStack create(String id) {
        var split = id.split(":");
        if (split[0].equals("mob")) return TalismanItem.createMobTalisman(MobTier.parse(split[1]));
        String name =
                split[0].equals("boss")
                        ? "Talisman du boss — " + MobTier.parse(split[1]).label()
                        : "Talisman de " + split[1] + " — Tier " + split[2];
        var i =
                Menus.item(
                        Material.NAUTILUS_SHELL,
                        name,
                        "Bonus passif lorsqu’il est placé dans un /sac.",
                        split[0].equals("boss")
                                ? "+"
                                        + bossPower(MobTier.parse(split[1]))
                                        + " % de dégâts contre ce boss."
                                : "Bonus : "
                                        + power(split[1], Integer.parseInt(split[2]))
                                        + (split[1].equals("vitalite") ? " PV" : " %"),
                        "Les talismans généraux identiques ne se cumulent pas : meilleur tier"
                                + " actif.");
        var m = i.getItemMeta();
        m.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id);
        i.setItemMeta(m);
        return i;
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
        if (item == null || !item.hasItemMeta()) return null;
        var tier = TalismanItem.mobTierOf(item);
        if (tier != null) return "mob:" + tier.id();
        String id =
                item.getItemMeta().getPersistentDataContainer().get(tag, PersistentDataType.STRING);
        if (id != null
                && id.matches(
                        "(boss:[a-z_]+|general:(lame|richesse|sagesse|fortune|vitalite):[1-5])"))
            return id;
        return null;
    }

    private void open(Player p, int page) {
        open(p, page, "debloques");
    }

    private void categories(Player p) {
        var view = menus.create(p, 27, "Talismans non débloqués");
        String[] kinds = {"mob", "boss", "lame", "richesse", "sagesse", "fortune", "vitalite"};
        String[] labels = {
            "Créatures", "Boss", "Lame", "Richesse", "Sagesse", "Fortune", "Vitalité"
        };
        for (int i = 0; i < kinds.length; i++) {
            String kind = kinds[i];
            view.put(10 + i, Menus.item(Material.BOOK, labels[i]), (who, r) -> open(who, 0, kind));
        }
        view.put(
                22,
                Menus.item(Material.ARROW, "Mes talismans débloqués"),
                (who, r) -> open(who, 0));
        view.show(p);
    }

    private void open(Player p, int page, String category) {
        if (!opening.add(p.getUniqueId())) return;
        var owned = new HashSet<String>();
        for (var item : p.getInventory().getContents()) {
            String id = id(item);
            if (id != null) owned.add(id);
        }
        if (bags.isLoaded(p.getUniqueId()))
            for (int i = 0; i < BagManager.MAX_CAPACITY; i++)
                for (var item : bags.contents(p.getUniqueId(), i)) {
                    String id = id(item);
                    if (id != null) owned.add(id);
                }
        var previous = p.getOpenInventory().getTopInventory();
        ready.thenCompose(
                        v ->
                                db.submit(
                                        c -> {
                                            try {
                                                for (String id : owned)
                                                    try (var s =
                                                            c.prepareStatement(
                                                                    "INSERT OR IGNORE INTO"
                                                                        + " talisman_collection(owner,id)"
                                                                        + " VALUES(?,?)")) {
                                                        s.setString(1, p.getUniqueId().toString());
                                                        s.setString(2, id);
                                                        s.executeUpdate();
                                                    }
                                                var result = new HashSet<String>();
                                                try (var s =
                                                        c.prepareStatement(
                                                                "SELECT id FROM talisman_collection"
                                                                        + " WHERE owner=?")) {
                                                    s.setString(1, p.getUniqueId().toString());
                                                    try (var r = s.executeQuery()) {
                                                        while (r.next()) result.add(r.getString(1));
                                                    }
                                                }
                                                return result;
                                            } catch (SQLException e) {
                                                throw new DatabaseManager.DatabaseException(e);
                                            }
                                        }))
                .whenComplete(
                        (unlocked, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            opening.remove(p.getUniqueId());
                                            if (stopped
                                                    || !fr.cliker.clikerv2.runtime.PlayerSessions
                                                            .isCurrent(p)
                                                    || p.getOpenInventory().getTopInventory()
                                                            != previous) return;
                                            if (error != null) {
                                                p.sendMessage("Collection indisponible.");
                                                return;
                                            }
                                            var ids = new ArrayList<String>();
                                            for (var tier : MobTier.values()) {
                                                ids.add("mob:" + tier.id());
                                                ids.add("boss:" + tier.id());
                                            }
                                            for (String kind :
                                                    List.of(
                                                            "lame",
                                                            "richesse",
                                                            "sagesse",
                                                            "fortune",
                                                            "vitalite"))
                                                for (int n = 1; n <= 5; n++)
                                                    ids.add("general:" + kind + ":" + n);
                                            ids.removeIf(
                                                    id ->
                                                            category.equals("debloques")
                                                                    ? !unlocked.contains(id)
                                                                    : unlocked.contains(id)
                                                                            || !(category.equals(
                                                                                                    "mob")
                                                                                            || category
                                                                                                    .equals(
                                                                                                            "boss")
                                                                                    ? id.startsWith(
                                                                                            category
                                                                                                    + ":")
                                                                                    : id.startsWith(
                                                                                            "general:"
                                                                                                    + category
                                                                                                    + ":")));
                                            int max = (ids.size() - 1) / 45;
                                            int selected = Math.max(0, Math.min(page, max));
                                            var v =
                                                    menus.create(
                                                            p,
                                                            54,
                                                            (category.equals("debloques")
                                                                            ? "Talismans débloqués"
                                                                                    + " — "
                                                                            : "Non débloqués — "
                                                                                    + category
                                                                                    + " — ")
                                                                    + ids.size());
                                            for (int i = 0;
                                                    i < 45 && selected * 45 + i < ids.size();
                                                    i++) {
                                                String id = ids.get(selected * 45 + i);
                                                boolean known = unlocked.contains(id);
                                                String[] parts = id.split(":");
                                                String hint =
                                                        parts[0].equals("general")
                                                                ? "Général "
                                                                        + parts[1]
                                                                        + " — Tier "
                                                                        + parts[2]
                                                                : "Zone "
                                                                        + (MobTier.parse(parts[1])
                                                                                        .ordinal()
                                                                                + 1)
                                                                        + " — "
                                                                        + (parts[0].equals("boss")
                                                                                ? "Boss"
                                                                                : "Créature");
                                                var item =
                                                        known
                                                                ? create(id)
                                                                : Menus.item(
                                                                        Material.GRAY_DYE,
                                                                        hint + " — Non découvert",
                                                                        parts[0].equals("general")
                                                                                ? "Tier accessible"
                                                                                      + " à partir"
                                                                                      + " de la"
                                                                                      + " zone "
                                                                                        + tierZone(
                                                                                                Integer
                                                                                                        .parseInt(
                                                                                                                parts[
                                                                                                                        2]))
                                                                                : "Obtiens ce"
                                                                                      + " talisman"
                                                                                      + " pour"
                                                                                      + " découvrir"
                                                                                      + " son bonus.");
                                                v.put(i, item, null);
                                            }
                                            v.put(
                                                    45,
                                                    Menus.item(Material.ARROW, "Page précédente"),
                                                    (who, r) -> open(who, selected - 1, category));
                                            v.put(
                                                    49,
                                                    Menus.item(
                                                            Material.BOOK,
                                                            "Informations",
                                                            "Collection permanente ; bonus actifs"
                                                                    + " uniquement dans tes sacs.",
                                                            "Un seul bonus par type général : le"
                                                                    + " meilleur tier.",
                                                            "Tier 5 : accessible en zone "
                                                                    + tierZone(5)
                                                                    + "."),
                                                    null);
                                            v.put(
                                                    53,
                                                    Menus.item(Material.ARROW, "Page suivante"),
                                                    (who, r) -> open(who, selected + 1, category));
                                            v.put(
                                                    47,
                                                    Menus.item(
                                                            Material.BOOK,
                                                            "Non débloqués — Catégories"),
                                                    (who, r) -> categories(who));
                                            if (!category.equals("debloques"))
                                                v.put(
                                                        51,
                                                        Menus.item(
                                                                Material.ARROW,
                                                                "Mes talismans débloqués"),
                                                        (who, r) -> open(who, 0));
                                            if (ids.isEmpty())
                                                v.put(
                                                        22,
                                                        Menus.item(
                                                                Material.GRAY_DYE,
                                                                "Aucun talisman dans cette liste"),
                                                        null);
                                            v.show(p);
                                        }));
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
