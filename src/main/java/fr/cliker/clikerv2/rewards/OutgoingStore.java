package fr.cliker.clikerv2.rewards;

import fr.cliker.clikerv2.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Journal de sortie : intention SQL, retrait + reçu playerdata, puis transaction métier. */
public final class OutgoingStore {
    private final DatabaseManager database;

    public OutgoingStore(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Void> prepare(String id, UUID owner, String payload) {
        return database.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "INSERT INTO outgoing_items(id,owner,payload) VALUES(?,?,?)")) {
                        s.setString(1, id);
                        s.setString(2, owner.toString());
                        s.setString(3, payload);
                        s.executeUpdate();
                        return null;
                    } catch (SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    /** À appeler dans la même transaction que la publication et le paiement de ses frais. */
    public static void commit(Connection c, String id, UUID owner, String payload)
            throws SQLException {
        try (var s =
                c.prepareStatement(
                        "UPDATE outgoing_items SET state=1 WHERE id=? AND owner=? AND payload=? AND"
                            + " state=0")) {
            s.setString(1, id);
            s.setString(2, owner.toString());
            s.setString(3, payload);
            if (s.executeUpdate() != 1)
                throw new SQLException("Sortie d'objet absente ou déjà traitée : " + id);
        }
    }

    /**
     * Aucun reçu : le retrait n'a jamais été sauvegardé, donc aucun objet à rendre. Reçu +
     * intention encore en attente : remboursement unique dans /claim. Une publication déjà validée
     * ne doit jamais être remboursée.
     */
    public CompletableFuture<Void> recover(UUID owner, String receipt) {
        return database.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        if (receipt != null) {
                            try (var s =
                                    c.prepareStatement(
                                            "SELECT 1 FROM outgoing_items WHERE id=? AND"
                                                + " owner=?")) {
                                s.setString(1, receipt);
                                s.setString(2, owner.toString());
                                try (var rows = s.executeQuery()) {
                                    if (!rows.next())
                                        throw new SQLException(
                                                "Reçu de sortie inconnu : " + receipt);
                                }
                            }
                            try (var s =
                                    c.prepareStatement(
                                            "SELECT payload FROM outgoing_items WHERE id=? AND"
                                                + " owner=? AND state=0")) {
                                s.setString(1, receipt);
                                s.setString(2, owner.toString());
                                try (var rows = s.executeQuery()) {
                                    if (rows.next())
                                        MailStore.insert(
                                                c, owner, "outgoing:" + receipt, rows.getString(1));
                                }
                            }
                        }
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE outgoing_items SET state=CASE WHEN id=? THEN 2 ELSE"
                                            + " 3 END WHERE owner=? AND state=0")) {
                            s.setString(1, receipt);
                            s.setString(2, owner.toString());
                            s.executeUpdate();
                        }
                        c.commit();
                        return null;
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
}
