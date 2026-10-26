package fr.cliker.clikerv2.lootbox;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.hunting.Gains;
import fr.cliker.clikerv2.hunting.RewardLedger;
import fr.cliker.clikerv2.rewards.OutgoingStore;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Le retrait sauvegardé et tous les gains sont validés ensemble avant de libérer le reçu. */
public final class LootboxStore {
    private final RewardLedger ledger;

    public LootboxStore(RewardLedger ledger) {
        this.ledger = ledger;
    }

    public CompletableFuture<Void> consume(
            UUID owner, String name, String receipt, String original, Gains gains) {
        return ledger.database()
                .submit(
                        c -> {
                            try {
                                c.setAutoCommit(false);
                                OutgoingStore.commit(c, receipt, owner, original);
                                if (!ledger.grant(c, "lootbox:" + receipt, owner, name, gains))
                                    throw new SQLException("Coffre déjà crédité");
                                c.commit();
                                return null;
                            } catch (SQLException | RuntimeException error) {
                                try {
                                    c.rollback();
                                } catch (SQLException rollback) {
                                    error.addSuppressed(rollback);
                                }
                                throw new DatabaseManager.DatabaseException(error);
                            }
                        });
    }
}
