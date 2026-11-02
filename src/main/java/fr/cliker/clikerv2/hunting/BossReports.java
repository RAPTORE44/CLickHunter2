package fr.cliker.clikerv2.hunting;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.features.Menus;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Bilans privés : même transaction et même identité que le paiement du boss. */
public final class BossReports {
    public record Report(
            String source,
            String boss,
            String label,
            long damage,
            long total,
            Gains gains,
            String items,
            long created,
            long bestDamage,
            double bestPercent,
            long fights) {
        public double percent() {
            return total <= 0 ? 0 : damage * 100.0 / total;
        }
    }

    private final JavaPlugin plugin;
    private final RewardLedger ledger;
    private final Menus menus;
    private final Map<UUID, Long> requests = new HashMap<>();
    private long sequence;
    private boolean stopped;

    public BossReports(JavaPlugin plugin, RewardLedger ledger) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.menus = new Menus(plugin);
    }

    public void start() {
        Objects.requireNonNull(plugin.getCommand("bilanboss"))
                .setExecutor(
                        (sender, cmd, label, args) -> {
                            if (sender instanceof Player player) open(player);
                            return true;
                        });
    }

    public static long total(Map<UUID, Long> damage) {
        long total = 0;
        for (long value : damage.values()) total = Math.addExact(total, Math.max(0, value));
        return total;
    }

    public static boolean record(
            Connection c,
            String source,
            UUID owner,
            String boss,
            String label,
            long damage,
            long total,
            Gains gains,
            String items)
            throws SQLException {
        if (damage <= 0 || total < damage) return false;
        try (var q =
                c.prepareStatement(
                        "INSERT OR IGNORE INTO"
                            + " boss_reports(source,owner,boss,label,damage,total,ecus,gems,cendres,xp,items,created)"
                            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
            q.setString(1, source);
            q.setString(2, owner.toString());
            q.setString(3, boss);
            q.setString(4, label);
            q.setLong(5, damage);
            q.setLong(6, total);
            q.setLong(7, gains.amount(CurrencyType.ECUS));
            q.setLong(8, gains.amount(CurrencyType.GEMMES_SOMBRES));
            q.setLong(9, gains.amount(CurrencyType.CENDRES));
            q.setLong(10, gains.xp());
            q.setString(11, items);
            q.setLong(12, System.currentTimeMillis());
            if (q.executeUpdate() == 0) return false;
        }
        try (var q =
                c.prepareStatement(
                        "INSERT INTO boss_records(owner,boss,damage,percent,fights)"
                            + " VALUES(?,?,?,?,1) ON CONFLICT(owner,boss) DO UPDATE SET"
                            + " damage=MAX(damage,excluded.damage),percent=MAX(percent,excluded.percent),fights=fights+1")) {
            q.setString(1, owner.toString());
            q.setString(2, boss);
            q.setLong(3, damage);
            q.setDouble(4, damage * 100.0 / total);
            q.executeUpdate();
        }
        return true;
    }

    public static CompletableFuture<List<Report>> history(RewardLedger ledger, UUID owner) {
        return ledger.database()
                .submit(
                        c -> {
                            try (var q =
                                    c.prepareStatement(
                                            "SELECT"
                                                + " r.source,r.boss,r.label,r.damage,r.total,r.ecus,r.gems,r.cendres,r.xp,r.items,r.created,b.damage,b.percent,b.fights"
                                                + " FROM boss_reports r JOIN boss_records b ON"
                                                + " b.owner=r.owner AND b.boss=r.boss WHERE"
                                                + " r.owner=? ORDER BY r.created DESC,r.rowid DESC"
                                                + " LIMIT 28")) {
                                q.setString(1, owner.toString());
                                List<Report> rows = new ArrayList<>();
                                try (var rs = q.executeQuery()) {
                                    while (rs.next())
                                        rows.add(
                                                new Report(
                                                        rs.getString(1),
                                                        rs.getString(2),
                                                        rs.getString(3),
                                                        rs.getLong(4),
                                                        rs.getLong(5),
                                                        Gains.of(
                                                                rs.getLong(6),
                                                                rs.getLong(7),
                                                                rs.getLong(8),
                                                                rs.getLong(9)),
                                                        rs.getString(10),
                                                        rs.getLong(11),
                                                        rs.getLong(12),
                                                        rs.getDouble(13),
                                                        rs.getLong(14)));
                                }
                                return List.copyOf(rows);
                            } catch (SQLException e) {
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        });
    }

    public static void notify(Player player) {
        player.sendMessage(
                fr.cliker.clikerv2.daily.DailySettings.color(
                                "&6⚔ Boss vaincu &8• &e[Voir mon bilan personnel]")
                        .clickEvent(
                                net.kyori.adventure.text.event.ClickEvent.runCommand("/bilanboss"))
                        .hoverEvent(
                                net.kyori.adventure.text.Component.text(
                                        "Dégâts, contribution, récompenses et records •"
                                            + " /bilanboss")));
    }

    public void open(Player player) {
        UUID id = player.getUniqueId();
        long request = ++sequence;
        requests.put(id, request);
        var previous = player.getOpenInventory().getTopInventory();
        var world = player.getWorld();
        history(ledger, id)
                .whenComplete(
                        (rows, error) -> {
                            if (stopped || !plugin.isEnabled()) return;
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        if (stopped || !Objects.equals(requests.get(id), request))
                                            return;
                                        requests.remove(id);
                                        if (!fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(
                                                        player)
                                                || player.getWorld() != world
                                                || player.getOpenInventory().getTopInventory()
                                                        != previous) return;
                                        if (error != null) {
                                            plugin.getLogger()
                                                    .log(
                                                            java.util.logging.Level.SEVERE,
                                                            "Lecture des bilans impossible",
                                                            error);
                                            player.sendMessage(
                                                    "§cImpossible de charger tes bilans. Réessaie"
                                                        + " plus tard.");
                                            return;
                                        }
                                        var view = menus.create(player, 54, "⚔ Mes bilans de boss");
                                        if (rows.isEmpty())
                                            view.put(
                                                    22,
                                                    Menus.item(
                                                            Material.PAPER,
                                                            "&7Aucun boss vaincu",
                                                            "&7Participe à un boss de zone, de raid"
                                                                + " ou au boss serveur.",
                                                            "&7Les bilans commencent avec cette"
                                                                + " mise à jour."),
                                                    null);
                                        for (int i = 0; i < rows.size(); i++) {
                                            var report = rows.get(i);
                                            List<String> lore = new ArrayList<>();
                                            lore.add(
                                                    "&7Date : &f"
                                                            + java.time.Instant.ofEpochMilli(
                                                                            report.created())
                                                                    .atZone(
                                                                            java.time.ZoneId.of(
                                                                                    "Europe/Paris"))
                                                                    .format(
                                                                            java.time.format
                                                                                    .DateTimeFormatter
                                                                                    .ofPattern(
                                                                                            "dd/MM/yyyy"
                                                                                                + " HH:mm")));
                                            lore.add(
                                                    "&c⚔ Dégâts : &f"
                                                            + Numbers.compact(report.damage()));
                                            lore.add(
                                                    "&bContribution : &f"
                                                            + String.format(
                                                                    Locale.FRANCE,
                                                                    "%.2f %%",
                                                                    report.percent()));
                                            lore.add("&8────────────────────");
                                            lore.add("&aRécompenses attribuées");
                                            lore.addAll(HuntUI.gains(report.gains()));
                                            lore.addAll(Arrays.asList(report.items().split("\n")));
                                            lore.add("&8────────────────────");
                                            lore.add("&e★ Records pour ce boss");
                                            lore.add(
                                                    "&7Dégâts : &f"
                                                            + Numbers.compact(report.bestDamage()));
                                            lore.add(
                                                    "&7Contribution : &f"
                                                            + String.format(
                                                                    Locale.FRANCE,
                                                                    "%.2f %%",
                                                                    report.bestPercent()));
                                            lore.add(
                                                    "&7Victoires avec participation : &f"
                                                            + report.fights());
                                            if (report.damage() == report.bestDamage())
                                                lore.add("&a★ Record de dégâts atteint !");
                                            int slot = 10 + i / 7 * 9 + i % 7;
                                            view.put(
                                                    slot,
                                                    Menus.item(
                                                            Material.NETHER_STAR,
                                                            "&6" + report.label(),
                                                            lore.toArray(String[]::new)),
                                                    null);
                                        }
                                        fr.cliker.clikerv2.ui.MenuNavigation.back(
                                                plugin, view.getInventory(), 45);
                                        view.show(player);
                                    });
                        });
    }

    public void shutdown() {
        stopped = true;
        requests.clear();
        org.bukkit.event.HandlerList.unregisterAll(menus);
    }
}
