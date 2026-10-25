package fr.cliker.clikerv2.hunting;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.economy.CurrencyType;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Journal idempotent : argent et XP sont enregistrés avant leur affichage. */
public final class RewardLedger implements Listener {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final NamespacedKey xpReceipt;
    private final Set<UUID> pendingXP = new HashSet<>();
    private final Set<UUID> dirtyXP = new HashSet<>();
    private volatile boolean stopped;
    private final long[] starting = new long[3];

    public RewardLedger(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        xpReceipt = new NamespacedKey(plugin, "earned_xp_applied");
        for (var currency : CurrencyType.values())
            starting[currency.ordinal()] =
                    Math.max(0, plugin.getConfig().getLong("starting-balances." + currency.key()));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public DatabaseManager database() {
        return database;
    }

    public boolean grant(Connection c, String source, UUID owner, String name, Gains gains)
            throws SQLException {
        try (var s =
                c.prepareStatement(
                        "INSERT OR IGNORE INTO reward_ledger(source,owner,kind)"
                                + " VALUES(?,?,'gain')")) {
            s.setString(1, source);
            s.setString(2, owner.toString());
            if (s.executeUpdate() == 0) return false;
        }
        try (var s =
                c.prepareStatement(
                        "INSERT OR IGNORE INTO"
                                + " player_balances(uuid,player_name,ecus,gemmes_sombres,cendres)"
                                + " VALUES(?,?,?,?,?)")) {
            s.setString(1, owner.toString());
            s.setString(2, name);
            s.setLong(3, starting[0]);
            s.setLong(4, starting[1]);
            s.setLong(5, starting[2]);
            s.executeUpdate();
        }
        for (var currency : CurrencyType.values()) {
            long amount = gains.amount(currency);
            if (amount == 0) continue;
            String column = currency.key().replace('-', '_');
            try (var s =
                    c.prepareStatement(
                            "UPDATE player_balances SET "
                                    + column
                                    + "="
                                    + column
                                    + "+?,player_name=? WHERE uuid=? AND "
                                    + column
                                    + "<=?")) {
                s.setLong(1, amount);
                s.setString(2, name);
                s.setString(3, owner.toString());
                s.setLong(4, Long.MAX_VALUE - amount);
                if (s.executeUpdate() != 1) throw new SQLException("Solde maximal dépassé");
            }
        }
        if (gains.xp() > 0) {
            try (var s =
                    c.prepareStatement(
                            "INSERT OR IGNORE INTO earned_xp(owner,total) VALUES(?,0)")) {
                s.setString(1, owner.toString());
                s.executeUpdate();
            }
            try (var s =
                    c.prepareStatement(
                            "UPDATE earned_xp SET total=total+? WHERE owner=? AND total<=?")) {
                s.setLong(1, gains.xp());
                s.setString(2, owner.toString());
                s.setLong(3, Long.MAX_VALUE - gains.xp());
                if (s.executeUpdate() != 1) throw new SQLException("XP maximal dépassé");
            }
        }
        return true;
    }

    public CompletableFuture<Boolean> grant(String source, UUID owner, String name, Gains gains) {
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        boolean accepted = grant(c, source, owner, name, gains);
                        c.commit();
                        return accepted;
                    } catch (SQLException e) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public void flushXP(UUID owner) {
        if (stopped) return;
        if (!pendingXP.add(owner)) {
            dirtyXP.add(owner);
            return;
        }
        // Une lecture par joueur et par seconde, même pendant une série d'éliminations.
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () ->
                                readXP(owner)
                                        .whenComplete(
                                                (total, error) -> {
                                                    if (stopped || !plugin.isEnabled()) return;
                                                    Bukkit.getScheduler()
                                                            .runTask(
                                                                    plugin,
                                                                    () ->
                                                                            applyXP(
                                                                                    owner, total,
                                                                                    error));
                                                }),
                        20L);
    }

    private record XPState(long total, boolean resetPending) {}

    private CompletableFuture<XPState> readXP(UUID owner) {
        return database.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "SELECT COALESCE((SELECT total FROM earned_xp WHERE"
                                        + " owner=?),0),EXISTS(SELECT 1 FROM prestige_resets WHERE"
                                        + " owner=?)")) {
                        s.setString(1, owner.toString());
                        s.setString(2, owner.toString());
                        try (var rs = s.executeQuery()) {
                            return rs.next()
                                    ? new XPState(rs.getLong(1), rs.getBoolean(2))
                                    : new XPState(0, false);
                        }
                    } catch (SQLException error) {
                        throw new DatabaseManager.DatabaseException(error);
                    }
                });
    }

    private void applyXP(UUID owner, XPState state, Throwable error) {
        pendingXP.remove(owner);
        boolean repeat = dirtyXP.remove(owner);
        if (error != null) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Lecture de l'XP en attente impossible",
                            error);
            return;
        }
        Player player = Bukkit.getPlayer(owner);
        if (player == null) return;
        if (state.resetPending()) {
            flushXP(owner);
            return;
        }
        long total = state.total();
        long applied =
                player.getPersistentDataContainer()
                        .getOrDefault(xpReceipt, PersistentDataType.LONG, 0L);
        if (total <= applied) {
            if (repeat) flushXP(owner);
            return;
        }
        long delta = Math.min(total - applied, Integer.MAX_VALUE);
        player.giveExp((int) delta);
        player.getPersistentDataContainer()
                .set(xpReceipt, PersistentDataType.LONG, applied + delta);
        // Paper sauvegarde l'XP et ce reçu dans le même fichier (autosave/déconnexion).
        // Si un crash restaure une ancienne sauvegarde, le journal rejoue le delta manquant.
        if (repeat || applied + delta < total) flushXP(owner);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        flushXP(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        stopped = true;
        pendingXP.clear();
        dirtyXP.clear();
        HandlerList.unregisterAll(this);
    }
}
