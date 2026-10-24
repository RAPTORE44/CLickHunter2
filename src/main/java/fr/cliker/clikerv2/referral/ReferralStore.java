package fr.cliker.clikerv2.referral;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.rewards.MailStore;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Inscription, les deux récompenses et notifications sont validées dans une seule transaction. */
public final class ReferralStore {
    private final DatabaseManager database;
    private final Map<CurrencyType, Long> initial;
    public record PlayerState(long deadline, boolean registered) {}
    public record Reward(Map<CurrencyType, Long> money, List<String> items) { public Reward { money = Map.copyOf(money); items = List.copyOf(items); } }
    public enum Result { ACCEPTED, EXPIRED, ALREADY_REGISTERED, SELF, UNKNOWN, CYCLE }
    public record Notice(String id, String message) {}
    public ReferralStore(DatabaseManager database, Map<CurrencyType, Long> initial) { this.database = database; this.initial = Map.copyOf(initial); }
    public CompletableFuture<PlayerState> join(UUID uuid, String name, long firstPlayed, long now, long window) {
        return database.submit(c -> {
            try {
                long first = firstPlayed > 0 ? Math.min(firstPlayed, now) : now;
                try (var s = c.prepareStatement("INSERT OR IGNORE INTO referral_players(uuid,name,first_join,deadline,last_seen) VALUES(?,?,?,?,?)")) {
                    s.setString(1, uuid.toString()); s.setString(2, name); s.setLong(3, first); s.setLong(4, Math.addExact(first, window)); s.setLong(5, now); s.executeUpdate();
                }
                try (var s = c.prepareStatement("UPDATE referral_players SET name=?,last_seen=? WHERE uuid=?")) { s.setString(1, name); s.setLong(2, now); s.setString(3, uuid.toString()); s.executeUpdate(); }
                try (var s = c.prepareStatement("SELECT deadline, EXISTS(SELECT 1 FROM referrals WHERE recruit=?) FROM referral_players WHERE uuid=?")) {
                    s.setString(1, uuid.toString()); s.setString(2, uuid.toString()); try (var r = s.executeQuery()) { if (!r.next()) throw new SQLException("Joueur non enregistré"); return new PlayerState(r.getLong(1), r.getBoolean(2)); }
                }
            } catch (SQLException e) { throw new DatabaseManager.DatabaseException(e); }
        });
    }
    public CompletableFuture<Result> register(UUID recruit, String recruitName, UUID sponsor, String sponsorName, long now,
                                               Reward recruitReward, Reward sponsorReward, String recruitNotice, String sponsorNotice) {
        return register(recruit, recruitName, sponsor, sponsorName, () -> now, recruitReward, sponsorReward, recruitNotice, sponsorNotice);
    }
    public CompletableFuture<Result> register(UUID recruit, String recruitName, UUID sponsor, String sponsorName, java.util.function.LongSupplier time,
                                               Reward recruitReward, Reward sponsorReward, String recruitNotice, String sponsorNotice) {
        return database.submit(c -> {
            try {
                c.setAutoCommit(false);
                long now = time.getAsLong();
                if (recruit.equals(sponsor)) { c.rollback(); return Result.SELF; }
                try (var s = c.prepareStatement("SELECT 1 FROM referrals WHERE recruit=?")) {
                    s.setString(1, recruit.toString()); try (var r = s.executeQuery()) { if (r.next()) { c.rollback(); return Result.ALREADY_REGISTERED; } }
                }
                try (var s = c.prepareStatement("SELECT deadline FROM referral_players WHERE uuid=?")) {
                    s.setString(1, recruit.toString()); try (var r = s.executeQuery()) { if (!r.next()) { c.rollback(); return Result.UNKNOWN; } if (now >= r.getLong(1)) { c.rollback(); return Result.EXPIRED; } }
                }
                Set<String> seen = new HashSet<>(); String ancestor = sponsor.toString();
                while (seen.add(ancestor)) {
                    if (ancestor.equals(recruit.toString())) { c.rollback(); return Result.CYCLE; }
                    try (var s = c.prepareStatement("SELECT sponsor FROM referrals WHERE recruit=?")) {
                        s.setString(1, ancestor); try (var r = s.executeQuery()) { if (!r.next()) break; ancestor = r.getString(1); }
                    }
                }
                try (var s = c.prepareStatement("INSERT INTO referrals(recruit,sponsor,created_at) VALUES(?,?,?)")) {
                    s.setString(1, recruit.toString()); s.setString(2, sponsor.toString()); s.setLong(3, now); s.executeUpdate();
                }
                credit(c, recruit, recruitName, recruitReward, "referral:recruit:" + recruit);
                credit(c, sponsor, sponsorName, sponsorReward, "referral:sponsor:" + recruit);
                notice(c, recruit, recruitNotice); notice(c, sponsor, sponsorNotice);
                c.commit(); return Result.ACCEPTED;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw new DatabaseManager.DatabaseException(e);
            }
        });
    }
    private void credit(Connection c, UUID uuid, String name, Reward reward, String source) throws SQLException {
        try (var s = c.prepareStatement("INSERT OR IGNORE INTO player_balances(uuid,player_name,ecus,gemmes_sombres,cendres) VALUES(?,?,?,?,?)")) {
            s.setString(1, uuid.toString()); s.setString(2, name); s.setLong(3, initial.getOrDefault(CurrencyType.ECUS, 0L)); s.setLong(4, initial.getOrDefault(CurrencyType.GEMMES_SOMBRES, 0L)); s.setLong(5, initial.getOrDefault(CurrencyType.CENDRES, 0L)); s.executeUpdate();
        }
        for (var entry : reward.money().entrySet()) {
            long amount = entry.getValue(); if (amount < 0) throw new SQLException("Récompense négative interdite");
            String column = entry.getKey().key().replace('-', '_');
            try (var s = c.prepareStatement("UPDATE player_balances SET " + column + "=" + column + "+? WHERE uuid=? AND " + column + "<=?")) {
                s.setLong(1, amount); s.setString(2, uuid.toString()); s.setLong(3, Long.MAX_VALUE - amount); if (s.executeUpdate() != 1) throw new SQLException("Solde maximal dépassé");
            }
        }
        for (int i = 0; i < reward.items().size(); i++) MailStore.insert(c, uuid, source + ":" + i, reward.items().get(i));
    }
    private void notice(Connection c, UUID owner, String message) throws SQLException {
        try (var s = c.prepareStatement("INSERT INTO private_notices(id,owner,message) VALUES(?,?,?)")) {
            s.setString(1, UUID.randomUUID().toString()); s.setString(2, owner.toString()); s.setString(3, message); s.executeUpdate();
        }
    }
    public CompletableFuture<List<Notice>> notices(UUID owner) {
        return database.submit(c -> {
            List<Notice> result = new ArrayList<>();
            try (var s = c.prepareStatement("SELECT id,message FROM private_notices WHERE owner=? AND delivered=0 ORDER BY rowid LIMIT 100")) {
                s.setString(1, owner.toString()); try (var r = s.executeQuery()) { while (r.next()) result.add(new Notice(r.getString(1), r.getString(2))); }
            } catch (SQLException e) { throw new DatabaseManager.DatabaseException(e); }
            return result;
        });
    }
    public CompletableFuture<Void> readNotice(UUID owner, String id) {
        return database.submit(c -> { try (var s = c.prepareStatement("UPDATE private_notices SET delivered=1 WHERE owner=? AND id=?")) { s.setString(1, owner.toString()); s.setString(2, id); s.executeUpdate(); return null; } catch (SQLException e) { throw new DatabaseManager.DatabaseException(e); } });
    }
}
