package fr.cliker.clikerv2.hunting;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Retrait et compteur validés dans la même transaction que le crédit. */
public final class AtmStore {
    private final RewardLedger ledger;

    public AtmStore(RewardLedger ledger) {
        this.ledger = ledger;
    }

    public CompletableFuture<Long> collect(
            UUID owner, String name, long playedTicks, long interval, long reward) {
        if (playedTicks < 0 || interval < 1 || reward < 1)
            throw new IllegalArgumentException("Tarif ATM invalide");
        return ledger.database()
                .submit(
                        c -> {
                            try {
                                c.setAutoCommit(false);
                                long claimed = 0;
                                try (var s =
                                        c.prepareStatement(
                                                "SELECT ticks FROM atm_claimed WHERE owner=?")) {
                                    s.setString(1, owner.toString());
                                    try (var r = s.executeQuery()) {
                                        if (r.next()) claimed = r.getLong(1);
                                    }
                                }
                                long periods = Math.max(0, playedTicks - claimed) / interval;
                                if (periods == 0) {
                                    c.rollback();
                                    return 0L;
                                }
                                long ticks =
                                        Math.addExact(
                                                claimed, Math.multiplyExact(periods, interval));
                                long amount = Math.multiplyExact(periods, reward);
                                if (!ledger.grant(
                                        c,
                                        "atm:" + owner + ":" + ticks,
                                        owner,
                                        name,
                                        Gains.of(amount, 0, 0, 0))) {
                                    c.rollback();
                                    throw new SQLException("Reçu ATM incohérent");
                                }
                                try (var s =
                                        c.prepareStatement(
                                                "INSERT INTO atm_claimed(owner,ticks) VALUES(?,?)"
                                                    + " ON CONFLICT(owner) DO UPDATE SET"
                                                    + " ticks=excluded.ticks")) {
                                    s.setString(1, owner.toString());
                                    s.setLong(2, ticks);
                                    s.executeUpdate();
                                }
                                c.commit();
                                return amount;
                            } catch (SQLException | ArithmeticException e) {
                                try {
                                    c.rollback();
                                } catch (SQLException ignored) {
                                }
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        });
    }
}
