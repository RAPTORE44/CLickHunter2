package fr.cliker.clikerv2.rankings;

import org.sqlite.Function;

import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Agrégats journaliers transactionnels. Les écritures ne repassent pas par Bukkit. */
public final class RankingHistory {
    private RankingHistory() {}

    public static void register(Connection connection, ZoneId zone) throws SQLException {
        register(connection, zone, Clock.systemUTC());
    }

    public static void register(Connection connection, ZoneId zone, Clock clock)
            throws SQLException {
        // Une opération SQLite entière, même autour de minuit, appartient à une seule journée.
        String day = LocalDate.now(clock.withZone(zone)).toString();
        Function.create(
                connection,
                "clickhunter_ranking_day",
                new Function() {
                    @Override
                    protected void xFunc() throws SQLException {
                        result(day);
                    }
                });
    }

    public static void install(Connection c, ZoneId zone) throws SQLException {
        c.setAutoCommit(false);
        try {
            try (var s = c.createStatement()) {
                s.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS ranking_daily(metric TEXT NOT NULL,day TEXT NOT"
                            + " NULL,owner TEXT NOT NULL,value INTEGER NOT NULL,PRIMARY"
                            + " KEY(metric,day,owner))");
                s.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS ranking_meta(metric TEXT PRIMARY KEY,since TEXT"
                            + " NOT NULL)");
            }
            boolean installed;
            try (var s = c.prepareStatement("SELECT 1 FROM ranking_meta WHERE metric='donateurs'");
                    var r = s.executeQuery()) {
                installed = r.next();
            }
            String today = LocalDate.now(zone).toString();
            if (!installed) {
                String first = today;
                var dates = new TreeMap<String, Map<String, Long>>();
                try (var s = c.createStatement();
                        var r =
                                s.executeQuery(
                                        "SELECT name_key,delta,created_at FROM donor_point_events"
                                            + " ORDER BY created_at,receipt")) {
                    while (r.next()) {
                        String day =
                                LocalDateTime.parse(
                                                r.getString(3),
                                                DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"))
                                        .toInstant(ZoneOffset.UTC)
                                        .atZone(zone)
                                        .toLocalDate()
                                        .toString();
                        if (day.compareTo(first) < 0) first = day;
                        dates.computeIfAbsent(day, k -> new HashMap<>())
                                .merge(r.getString(1), r.getLong(2), Math::addExact);
                    }
                }
                try (var s =
                        c.prepareStatement(
                                "INSERT INTO ranking_daily(metric,day,owner,value)"
                                    + " VALUES('donateurs',?,?,?)")) {
                    for (var date : dates.entrySet())
                        for (var entry : date.getValue().entrySet()) {
                            s.setString(1, date.getKey());
                            s.setString(2, entry.getKey());
                            s.setLong(3, entry.getValue());
                            s.addBatch();
                        }
                    s.executeBatch();
                }
                try (var s =
                        c.prepareStatement("INSERT INTO ranking_meta(metric,since) VALUES(?,?)")) {
                    for (var metric : RankingMetric.values()) {
                        s.setString(1, metric.id);
                        s.setString(2, metric == RankingMetric.DONORS ? first : today);
                        s.addBatch();
                    }
                    s.executeBatch();
                }
            }
            for (var metric : RankingMetric.values()) {
                String table =
                        switch (metric) {
                            case ECUS, GEMS, ASH -> "player_balances";
                            case KILLS -> "player_mob_kills";
                            case LEVELS -> "player_levels";
                            case DONORS -> "donor_points";
                        };
                String key = metric == RankingMetric.DONORS ? "name_key" : "uuid";
                if (metric != RankingMetric.LEVELS) trigger(c, metric, table, key, true);
                trigger(c, metric, table, key, false);
            }
            c.commit();
        } catch (SQLException | RuntimeException error) {
            c.rollback();
            throw error;
        } finally {
            c.setAutoCommit(true);
        }
    }

    private static void trigger(
            Connection c, RankingMetric metric, String table, String key, boolean insert)
            throws SQLException {
        String amount =
                insert ? "NEW." + metric.column : "NEW." + metric.column + "-OLD." + metric.column;
        String condition =
                insert
                        ? "NEW." + metric.column + "!=0"
                        : metric == RankingMetric.LEVELS
                                ? "NEW.level>OLD.level"
                                : "NEW." + metric.column + "!=OLD." + metric.column;
        String sql =
                "CREATE TRIGGER IF NOT EXISTS ranking_"
                        + metric.id
                        + (insert ? "_insert" : "_update")
                        + " AFTER "
                        + (insert ? "INSERT" : "UPDATE OF " + metric.column)
                        + " ON "
                        + table
                        + " WHEN "
                        + condition
                        + " BEGIN INSERT INTO ranking_daily(metric,day,owner,value) VALUES('"
                        + metric.id
                        + "',clickhunter_ranking_day(),NEW."
                        + key
                        + ","
                        + amount
                        + ") ON CONFLICT(metric,day,owner) DO UPDATE SET"
                        + " value=value+excluded.value; END";
        try (var s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }
}
