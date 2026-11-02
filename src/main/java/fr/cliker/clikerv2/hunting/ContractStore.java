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

    /** Un emplacement accepté ou réclamé reste figé jusqu'au prochain jour. */
    public CompletableFuture<List<Contract>> offers(
            UUID owner, String cycle, List<Contract> desired) {
        return ledger.database()
                .submit(
                        c -> {
                            try {
                                c.setAutoCommit(false);
                                List<Contract> result = new ArrayList<>();
                                for (int slot = 0; slot < desired.size(); slot++) {
                                    Contract def = desired.get(slot);
                                    boolean accepted = read(c, owner, def, cycle).accepted();
                                    if (!accepted) {
                                        try (var q =
                                                c.prepareStatement(
                                                        "INSERT INTO"
                                                            + " contract_offers(owner,cycle,slot,mob,target,ecus,gems,cendres,xp)"
                                                            + " VALUES(?,?,?,?,?,?,?,?,?) ON"
                                                            + " CONFLICT(owner,cycle,slot) DO"
                                                            + " UPDATE SET"
                                                            + " mob=excluded.mob,target=excluded.target,ecus=excluded.ecus,gems=excluded.gems,cendres=excluded.cendres,xp=excluded.xp")) {
                                            q.setString(1, owner.toString());
                                            q.setString(2, cycle);
                                            q.setInt(3, slot);
                                            q.setString(4, def.mob());
                                            q.setLong(5, def.target());
                                            q.setLong(
                                                    6,
                                                    def.reward()
                                                            .amount(
                                                                    fr.cliker.clikerv2.economy
                                                                            .CurrencyType.ECUS));
                                            q.setLong(
                                                    7,
                                                    def.reward()
                                                            .amount(
                                                                    fr.cliker.clikerv2.economy
                                                                            .CurrencyType
                                                                            .GEMMES_SOMBRES));
                                            q.setLong(
                                                    8,
                                                    def.reward()
                                                            .amount(
                                                                    fr.cliker.clikerv2.economy
                                                                            .CurrencyType.CENDRES));
                                            q.setLong(9, def.reward().xp());
                                            q.executeUpdate();
                                        }
                                    }
                                    try (var q =
                                            c.prepareStatement(
                                                    "SELECT mob,target,ecus,gems,cendres,xp FROM"
                                                        + " contract_offers WHERE owner=? AND"
                                                        + " cycle=? AND slot=?")) {
                                        q.setString(1, owner.toString());
                                        q.setString(2, cycle);
                                        q.setInt(3, slot);
                                        try (var rows = q.executeQuery()) {
                                            if (!rows.next())
                                                throw new SQLException("Offre absente");
                                            result.add(
                                                    new Contract(
                                                            def.id(),
                                                            rows.getString(1),
                                                            false,
                                                            rows.getLong(2),
                                                            Gains.of(
                                                                    rows.getLong(3),
                                                                    rows.getLong(4),
                                                                    rows.getLong(5),
                                                                    rows.getLong(6))));
                                        }
                                    }
                                }
                                c.commit();
                                return List.copyOf(result);
                            } catch (SQLException e) {
                                try {
                                    c.rollback();
                                } catch (SQLException ignored) {
                                }
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        });
    }

    public CompletableFuture<List<Contract>> savedOffers(UUID owner, String cycle) {
        return ledger.database()
                .submit(
                        c -> {
                            try {
                                List<Contract> result = new ArrayList<>();
                                for (int i = 0; i < 3; i++) {
                                    Contract offer = savedOffer(c, owner, cycle, "adaptatif-" + i);
                                    if (offer != null) result.add(offer);
                                }
                                return List.copyOf(result);
                            } catch (SQLException ex) {
                                throw new DatabaseManager.DatabaseException(ex);
                            }
                        });
    }

    private Contract savedOffer(Connection c, UUID owner, String cycle, String id)
            throws SQLException {
        if (!id.matches("adaptatif-[0-2]")) return null;
        try (var q =
                c.prepareStatement(
                        "SELECT mob,target,ecus,gems,cendres,xp FROM contract_offers WHERE owner=?"
                            + " AND cycle=? AND slot=?")) {
            q.setString(1, owner.toString());
            q.setString(2, cycle);
            q.setInt(3, id.charAt(id.length() - 1) - '0');
            try (var row = q.executeQuery()) {
                return row.next()
                        ? new Contract(
                                id,
                                row.getString(1),
                                false,
                                row.getLong(2),
                                Gains.of(
                                        row.getLong(3),
                                        row.getLong(4),
                                        row.getLong(5),
                                        row.getLong(6)))
                        : null;
            }
        }
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
                                if (contract.id().startsWith("adaptatif-")
                                        && !contract.equals(
                                                savedOffer(c, owner, cycle, contract.id()))) {
                                    c.rollback();
                                    return "actualise";
                                }
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
