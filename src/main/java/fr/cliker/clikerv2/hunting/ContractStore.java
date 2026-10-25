package fr.cliker.clikerv2.hunting;

import fr.cliker.clikerv2.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ContractStore {
    public record Contract(String id, String mob, boolean boss, long target, Gains reward) {}

    public record Progress(boolean accepted, boolean claimed, long done) {}

    private final RewardLedger ledger;

    public ContractStore(RewardLedger ledger) {
        this.ledger = ledger;
    }

    public CompletableFuture<Progress> progress(UUID owner, Contract contract, String cycle) {
        return ledger.database()
                .submit(
                        c -> {
                            try {
                                return read(c, owner, contract, cycle);
                            } catch (SQLException e) {
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        });
    }

    public CompletableFuture<Map<String, Progress>> progress(
            UUID owner, List<Contract> definitions, String cycle) {
        return ledger.database()
                .submit(
                        c -> {
                            try {
                                Map<String, long[]> baselines = new HashMap<>(),
                                        counts = new HashMap<>();
                                try (var s =
                                        c.prepareStatement(
                                                "SELECT contract,baseline,claimed FROM"
                                                        + " hunting_contracts WHERE owner=? AND"
                                                        + " cycle=?")) {
                                    s.setString(1, owner.toString());
                                    s.setString(2, cycle);
                                    try (var rs = s.executeQuery()) {
                                        while (rs.next())
                                            baselines.put(
                                                    rs.getString(1),
                                                    new long[] {rs.getLong(2), rs.getLong(3)});
                                    }
                                }
                                try (var s =
                                        c.prepareStatement(
                                                "SELECT mob,boss,kills FROM bestiary_stats WHERE"
                                                        + " owner=?")) {
                                    s.setString(1, owner.toString());
                                    try (var rs = s.executeQuery()) {
                                        while (rs.next())
                                            counts.computeIfAbsent(
                                                                    rs.getString(1),
                                                                    k -> new long[2])[
                                                            rs.getInt(2)] =
                                                    rs.getLong(3);
                                    }
                                }
                                Map<String, Progress> result = new HashMap<>();
                                for (var def : definitions) {
                                    var baseline = baselines.get(def.id());
                                    result.put(
                                            def.id(),
                                            baseline == null
                                                    ? new Progress(false, false, 0)
                                                    : new Progress(
                                                            true,
                                                            baseline[1] != 0,
                                                            Math.max(
                                                                    0,
                                                                    counts.getOrDefault(
                                                                                            def
                                                                                                    .mob(),
                                                                                            new long
                                                                                                    [2])[
                                                                                    def.boss()
                                                                                            ? 1
                                                                                            : 0]
                                                                            - baseline[0])));
                                }
                                return result;
                            } catch (SQLException e) {
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        });
    }

    private Progress read(Connection c, UUID owner, Contract contract, String cycle)
            throws SQLException {
        try (var s =
                c.prepareStatement(
                        "SELECT baseline,claimed FROM hunting_contracts WHERE owner=? AND"
                                + " contract=? AND cycle=?")) {
            s.setString(1, owner.toString());
            s.setString(2, contract.id());
            s.setString(3, cycle);
            try (var r = s.executeQuery()) {
                if (!r.next()) return new Progress(false, false, 0);
                return new Progress(
                        true,
                        r.getBoolean(2),
                        Math.max(
                                0,
                                HuntingStore.count(c, owner, contract.mob(), contract.boss())
                                        - r.getLong(1)));
            }
        }
    }

    /** Le premier clic accepte ; seul un clic ultérieur après l'objectif verse les gains. */
    public CompletableFuture<String> interact(
            UUID owner, String name, Contract contract, String cycle) {
        return ledger.database()
                .submit(
                        c -> {
                            try {
                                c.setAutoCommit(false);
                                Progress progress = read(c, owner, contract, cycle);
                                // Un boss seul ne révèle jamais le mob normal.
                                if (HuntingStore.count(c, owner, contract.mob(), false) == 0) {
                                    c.rollback();
                                    return "inconnu";
                                }
                                if (!progress.accepted()) {
                                    try (var s =
                                            c.prepareStatement(
                                                    "INSERT INTO"
                                                        + " hunting_contracts(owner,contract,cycle,baseline)"
                                                        + " VALUES(?,?,?,?)")) {
                                        s.setString(1, owner.toString());
                                        s.setString(2, contract.id());
                                        s.setString(3, cycle);
                                        s.setLong(
                                                4,
                                                HuntingStore.count(
                                                        c, owner, contract.mob(), contract.boss()));
                                        s.executeUpdate();
                                    }
                                    c.commit();
                                    return "accepte";
                                }
                                if (progress.claimed()) {
                                    c.rollback();
                                    return "deja";
                                }
                                if (progress.done() < contract.target()) {
                                    c.rollback();
                                    return "incomplet";
                                }
                                boolean paid =
                                        ledger.grant(
                                                c,
                                                "contract:"
                                                        + owner
                                                        + ":"
                                                        + contract.id()
                                                        + ":"
                                                        + cycle,
                                                owner,
                                                name,
                                                contract.reward());
                                try (var s =
                                        c.prepareStatement(
                                                "UPDATE hunting_contracts SET claimed=1 WHERE"
                                                        + " owner=? AND contract=? AND cycle=?")) {
                                    s.setString(1, owner.toString());
                                    s.setString(2, contract.id());
                                    s.setString(3, cycle);
                                    s.executeUpdate();
                                }
                                c.commit();
                                return paid ? "recompense" : "deja";
                            } catch (SQLException e) {
                                try {
                                    c.rollback();
                                } catch (SQLException ignored) {
                                }
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        });
    }
}
