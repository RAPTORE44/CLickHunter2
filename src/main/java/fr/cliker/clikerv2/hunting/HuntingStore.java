package fr.cliker.clikerv2.hunting;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.rewards.MailStore;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class HuntingStore {
    private final RewardLedger ledger;

    public record Stats(long kills, long bosses, Gains gains) {}

    public HuntingStore(RewardLedger ledger) {
        this.ledger = ledger;
    }

    public static long count(Connection c, UUID owner, String mob, boolean boss)
            throws SQLException {
        try (var s =
                c.prepareStatement(
                        "SELECT kills FROM bestiary_stats WHERE owner=? AND mob=? AND boss=?")) {
            s.setString(1, owner.toString());
            s.setString(2, mob);
            s.setInt(3, boss ? 1 : 0);
            try (var r = s.executeQuery()) {
                return r.next() ? r.getLong(1) : 0L;
            }
        }
    }

    public CompletableFuture<Boolean> kill(
            String eventId,
            UUID owner,
            String name,
            String mob,
            boolean boss,
            Gains gains,
            List<String> items) {
        return ledger.database()
                .submit(
                        c -> {
                            try {
                                c.setAutoCommit(false);
                                if (!ledger.grant(c, "kill:" + eventId, owner, name, gains)) {
                                    c.rollback();
                                    return false;
                                }
                                if (!boss)
                                    try (var counter =
                                            c.prepareStatement(
                                                    "UPDATE server_counters SET value=value+1 WHERE"
                                                            + " id='mob_kills'")) {
                                        counter.executeUpdate();
                                    }
                                try (var s =
                                        c.prepareStatement(
                                                "INSERT INTO"
                                                    + " bestiary_stats(owner,mob,boss,kills,xp,last_ecus,last_gems,last_cendres,last_xp)"
                                                    + " VALUES(?,?,?,1,?,?,?,?,?) ON"
                                                    + " CONFLICT(owner,mob,boss) DO UPDATE SET"
                                                    + " kills=kills+1,xp=xp+excluded.xp,last_ecus=excluded.last_ecus,last_gems=excluded.last_gems,last_cendres=excluded.last_cendres,last_xp=excluded.last_xp")) {
                                    s.setString(1, owner.toString());
                                    s.setString(2, mob);
                                    s.setInt(3, boss ? 1 : 0);
                                    s.setLong(4, gains.xp());
                                    s.setLong(5, gains.amount(CurrencyType.ECUS));
                                    s.setLong(6, gains.amount(CurrencyType.GEMMES_SOMBRES));
                                    s.setLong(7, gains.amount(CurrencyType.CENDRES));
                                    s.setLong(8, gains.xp());
                                    s.executeUpdate();
                                }
                                for (var currency : CurrencyType.values()) {
                                    try (var s =
                                            c.prepareStatement(
                                                    "INSERT INTO"
                                                        + " bestiary_money(owner,mob,currency,amount)"
                                                        + " VALUES(?,?,?,?) ON"
                                                        + " CONFLICT(owner,mob,currency) DO UPDATE"
                                                        + " SET amount=amount+excluded.amount")) {
                                        s.setString(1, owner.toString());
                                        s.setString(2, mob);
                                        s.setString(3, currency.key());
                                        s.setLong(4, gains.amount(currency));
                                        s.executeUpdate();
                                    }
                                }
                                try (var s =
                                        c.prepareStatement(
                                                "INSERT INTO player_mob_kills(uuid,total)"
                                                    + " VALUES(?,1) ON CONFLICT(uuid) DO UPDATE SET"
                                                    + " total=total+1")) {
                                    s.setString(1, owner.toString());
                                    s.executeUpdate();
                                }
                                for (int i = 0; i < items.size(); i++)
                                    MailStore.insert(
                                            c, owner, "kill:" + eventId + ":" + i, items.get(i));
                                c.commit();
                                return true;
                            } catch (SQLException e) {
                                try {
                                    c.rollback();
                                } catch (SQLException ignored) {
                                }
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        });
    }

    public CompletableFuture<Map<String, Stats>> stats(UUID owner) {
        return ledger.database()
                .submit(
                        c -> {
                            Map<String, long[]> counts = new HashMap<>();
                            Map<String, Map<CurrencyType, Long>> money = new HashMap<>();
                            try {
                                try (var s =
                                        c.prepareStatement(
                                                "SELECT mob,boss,kills,xp FROM bestiary_stats WHERE"
                                                        + " owner=?")) {
                                    s.setString(1, owner.toString());
                                    try (var r = s.executeQuery()) {
                                        while (r.next()) {
                                            var a =
                                                    counts.computeIfAbsent(
                                                            r.getString(1), k -> new long[3]);
                                            a[r.getInt(2)] += r.getLong(3);
                                            a[2] += r.getLong(4);
                                        }
                                    }
                                }
                                try (var s =
                                        c.prepareStatement(
                                                "SELECT mob,currency,amount FROM bestiary_money"
                                                        + " WHERE owner=?")) {
                                    s.setString(1, owner.toString());
                                    try (var r = s.executeQuery()) {
                                        while (r.next()) {
                                            var currency = CurrencyType.parse(r.getString(2));
                                            if (currency != null)
                                                money.computeIfAbsent(
                                                                r.getString(1),
                                                                k ->
                                                                        new EnumMap<>(
                                                                                CurrencyType.class))
                                                        .put(currency, r.getLong(3));
                                        }
                                    }
                                }
                                Map<String, Stats> result = new HashMap<>();
                                counts.forEach(
                                        (mob, a) ->
                                                result.put(
                                                        mob,
                                                        new Stats(
                                                                a[0],
                                                                a[1],
                                                                new Gains(
                                                                        money.getOrDefault(
                                                                                mob, Map.of()),
                                                                        a[2]))));
                                return result;
                            } catch (SQLException e) {
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        });
    }
}
