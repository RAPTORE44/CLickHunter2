package fr.cliker.clikerv2.rankings;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class RankingStore {
    public record Entry(String name, long value) {}

    public record Board(List<Entry> entries, LocalDate since) {}

    private final DatabaseManager database;

    public RankingStore(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Board> board(
            RankingMetric metric, RankingPeriod period, LocalDate date) {
        return database.submit(
                c -> {
                    try {
                        LocalDate since;
                        try (var s =
                                c.prepareStatement(
                                        "SELECT since FROM ranking_meta WHERE metric=?")) {
                            s.setString(1, metric.id);
                            try (var r = s.executeQuery()) {
                                if (!r.next()) throw new SQLException("Historique non initialisé");
                                since = LocalDate.parse(r.getString(1));
                            }
                        }
                        String name =
                                metric == RankingMetric.DONORS
                                        ? "b.name"
                                        : metric == RankingMetric.LEVELS
                                                ? "b.player_name"
                                                : metric == RankingMetric.KILLS
                                                        ? "COALESCE(b.player_name,l.player_name,'Joueur"
                                                              + " inconnu')"
                                                        : "b.player_name";
                        String table =
                                switch (metric) {
                                    case ECUS, GEMS, ASH -> "player_balances b";
                                    case LEVELS -> "player_levels b";
                                    case KILLS ->
                                            "player_mob_kills k LEFT JOIN player_balances b ON"
                                                    + " b.uuid=k.uuid LEFT JOIN player_levels l ON"
                                                    + " l.uuid=k.uuid";
                                    case DONORS -> "donor_points b";
                                };
                        String sql;
                        if (period == RankingPeriod.ALL) {
                            String value =
                                    metric == RankingMetric.KILLS
                                            ? "k.total"
                                            : "b." + metric.column;
                            sql =
                                    "SELECT "
                                            + name
                                            + " AS name,"
                                            + value
                                            + " AS total FROM "
                                            + table
                                            + " WHERE "
                                            + value
                                            + ">0 ORDER BY 2 DESC,name COLLATE NOCASE ASC LIMIT"
                                            + " 10";
                        } else {
                            String id =
                                    metric == RankingMetric.DONORS
                                            ? "b.name_key"
                                            : metric == RankingMetric.KILLS ? "k.uuid" : "b.uuid";
                            sql =
                                    "SELECT "
                                            + name
                                            + " AS name,r.total FROM (SELECT owner,SUM(value) AS"
                                            + " total FROM ranking_daily WHERE metric=? AND day>=?"
                                            + " AND day<? GROUP BY owner HAVING SUM(value)>0) r"
                                            + " JOIN "
                                            + table
                                            + " ON "
                                            + id
                                            + "=r.owner ORDER BY 2 DESC,name COLLATE NOCASE ASC"
                                            + " LIMIT 10";
                            // Les LEFT JOIN de la source kills doivent suivre le JOIN du
                            // propriétaire.
                            if (metric == RankingMetric.KILLS)
                                sql =
                                        "SELECT COALESCE(b.player_name,l.player_name,'Joueur"
                                            + " inconnu') AS name,r.total FROM (SELECT"
                                            + " owner,SUM(value) AS total FROM ranking_daily WHERE"
                                            + " metric=? AND day>=? AND day<? GROUP BY owner HAVING"
                                            + " SUM(value)>0) r JOIN player_mob_kills k ON"
                                            + " k.uuid=r.owner LEFT JOIN player_balances b ON"
                                            + " b.uuid=k.uuid LEFT JOIN player_levels l ON"
                                            + " l.uuid=k.uuid ORDER BY 2 DESC,name COLLATE"
                                            + " NOCASE ASC LIMIT 10";
                        }
                        var rows = new ArrayList<Entry>();
                        try (var s = c.prepareStatement(sql)) {
                            if (period != RankingPeriod.ALL) {
                                s.setString(1, metric.id);
                                s.setString(2, period.start(date).toString());
                                s.setString(3, period.shift(date, 1).toString());
                            }
                            try (var r = s.executeQuery()) {
                                while (r.next()) rows.add(new Entry(r.getString(1), r.getLong(2)));
                            }
                        }
                        return new Board(List.copyOf(rows), since);
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }
}
