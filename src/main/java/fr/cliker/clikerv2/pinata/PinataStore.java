package fr.cliker.clikerv2.pinata;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.hunting.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Crédit et récapitulatif sont validés ensemble, sans dépendre de la présence en ligne. */
public final class PinataStore {
    private final DatabaseManager db;
    private final RewardLedger ledger;

    public record Summary(String round, UUID owner, long hits, long coins, long gems, long xp) {}

    public PinataStore(DatabaseManager db, RewardLedger ledger) {
        this.db = db;
        this.ledger = ledger;
    }

    public CompletableFuture<Void> initialize() {
        return db.submit(
                c -> {
                    try (var s = c.createStatement()) {
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS pinata_rounds(id TEXT PRIMARY"
                                    + " KEY,active INTEGER NOT NULL,reward_type TEXT NOT NULL)");
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS pinata_totals(round TEXT NOT NULL,owner"
                                    + " TEXT NOT NULL,hits INTEGER NOT NULL DEFAULT 0,coins INTEGER"
                                    + " NOT NULL DEFAULT 0,gems INTEGER NOT NULL DEFAULT 0,xp"
                                    + " INTEGER NOT NULL DEFAULT 0,reported INTEGER NOT NULL"
                                    + " DEFAULT 0,PRIMARY KEY(round,owner))");
                        s.executeUpdate(
                                "CREATE INDEX IF NOT EXISTS pinata_pending ON"
                                        + " pinata_totals(owner,reported)");
                        s.executeUpdate("UPDATE pinata_rounds SET active=0");
                        return null;
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Void> begin(String id, String type) {
        if (!List.of("ecus", "xp", "gemmes").contains(type))
            throw new IllegalArgumentException("Type de piñata invalide");
        return db.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "INSERT INTO pinata_rounds(id,active,reward_type)"
                                            + " VALUES(?,1,?)")) {
                        s.setString(1, id);
                        s.setString(2, type);
                        s.executeUpdate();
                        return null;
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Boolean> hit(
            String round, String hit, UUID owner, String name, Gains gain) {
        return db.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        try (var s =
                                c.prepareStatement(
                                        "SELECT active,reward_type FROM pinata_rounds WHERE"
                                                + " id=?")) {
                            s.setString(1, round);
                            try (var r = s.executeQuery()) {
                                if (!r.next()
                                        || r.getInt(1) != 1
                                        || !matches(r.getString(2), gain)) {
                                    c.rollback();
                                    return false;
                                }
                            }
                        }
                        if (!ledger.grant(c, "pinata:" + round + ":" + hit, owner, name, gain)) {
                            c.rollback();
                            return false;
                        }
                        try (var s =
                                c.prepareStatement(
                                        "INSERT INTO pinata_totals(round,owner,hits,coins,gems,xp)"
                                            + " VALUES(?,?,1,?,?,?) ON CONFLICT(round,owner) DO"
                                            + " UPDATE SET"
                                            + " hits=hits+1,coins=coins+excluded.coins,gems=gems+excluded.gems,xp=xp+excluded.xp")) {
                            s.setString(1, round);
                            s.setString(2, owner.toString());
                            s.setLong(3, gain.amount(fr.cliker.clikerv2.economy.CurrencyType.ECUS));
                            s.setLong(
                                    4,
                                    gain.amount(
                                            fr.cliker.clikerv2.economy.CurrencyType
                                                    .GEMMES_SOMBRES));
                            s.setLong(5, gain.xp());
                            s.executeUpdate();
                        }
                        c.commit();
                        return true;
                    } catch (SQLException e) {
                        try {
                            c.rollback();
                        } catch (SQLException rollback) {
                            e.addSuppressed(rollback);
                        }
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    private static boolean matches(String type, Gains gain) {
        long coins = gain.amount(fr.cliker.clikerv2.economy.CurrencyType.ECUS);
        long gems = gain.amount(fr.cliker.clikerv2.economy.CurrencyType.GEMMES_SOMBRES);
        if (gain.amount(fr.cliker.clikerv2.economy.CurrencyType.CENDRES) != 0) return false;
        return switch (type) {
            case "ecus" -> coins > 0 && gems == 0 && gain.xp() == 0;
            case "gemmes" -> gems > 0 && coins == 0 && gain.xp() == 0;
            case "xp" -> gain.xp() > 0 && coins == 0 && gems == 0;
            default -> false;
        };
    }

    public CompletableFuture<Void> finish(String id) {
        return db.submit(
                c -> {
                    try (var s =
                            c.prepareStatement("UPDATE pinata_rounds SET active=0 WHERE id=?")) {
                        s.setString(1, id);
                        s.executeUpdate();
                        return null;
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<List<Summary>> pending(UUID owner) {
        return db.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "SELECT t.round,t.hits,t.coins,t.gems,t.xp FROM pinata_totals t"
                                        + " JOIN pinata_rounds r ON r.id=t.round WHERE t.owner=?"
                                        + " AND t.reported=0 AND r.active=0")) {
                        s.setString(1, owner.toString());
                        var list = new ArrayList<Summary>();
                        try (var r = s.executeQuery()) {
                            while (r.next())
                                list.add(
                                        new Summary(
                                                r.getString(1),
                                                owner,
                                                r.getLong(2),
                                                r.getLong(3),
                                                r.getLong(4),
                                                r.getLong(5)));
                        }
                        return list;
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public CompletableFuture<Void> reported(String round, UUID owner) {
        return db.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "UPDATE pinata_totals SET reported=1 WHERE round=? AND"
                                            + " owner=?")) {
                        s.setString(1, round);
                        s.setString(2, owner.toString());
                        s.executeUpdate();
                        return null;
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }
}
