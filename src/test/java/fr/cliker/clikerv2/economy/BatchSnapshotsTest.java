package fr.cliker.clikerv2.economy;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class BatchSnapshotsTest {
    private DatabaseManager db;
    private EconomyService economy;
    private List<UUID> owners;

    @BeforeEach
    void before() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "batch-" + UUID.randomUUID() + ".db");
        db.initialize();
        economy = new EconomyService(db, Map.of());
        owners = new ArrayList<>();
        for (int i = 0; i < 600; i++) owners.add(UUID.randomUUID());
        db.submit(
                        c -> {
                            try {
                                c.setAutoCommit(false);
                                try (var balances =
                                                c.prepareStatement(
                                                        "INSERT INTO"
                                                            + " player_balances(uuid,player_name,ecus,gemmes_sombres,cendres)"
                                                            + " VALUES(?,?,12345,185,90)");
                                        var kills =
                                                c.prepareStatement(
                                                        "INSERT INTO player_mob_kills(uuid,total)"
                                                                + " VALUES(?,42)")) {
                                    for (UUID owner : owners) {
                                        balances.setString(1, owner.toString());
                                        balances.setString(2, "Joueur");
                                        balances.addBatch();
                                        kills.setString(1, owner.toString());
                                        kills.addBatch();
                                    }
                                    balances.executeBatch();
                                    kills.executeBatch();
                                }
                                c.commit();
                                return null;
                            } catch (java.sql.SQLException error) {
                                throw new DatabaseManager.DatabaseException(error);
                            }
                        })
                .join();
    }

    @AfterEach
    void after() {
        try {
            if (db != null) db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void batchesBeyondFiveHundredKeepExactBalancesAndHandleDuplicatesAndMissingOwners() {
        UUID missing = UUID.randomUUID();
        var selected = new ArrayList<>(owners);
        selected.add(missing);
        selected.add(owners.getFirst());
        var snapshots = economy.playerSnapshots(selected).join();
        assertEquals(601, snapshots.size());
        for (UUID owner : owners) {
            assertEquals(
                    new EconomyService.Balances(12345, 185, 90), snapshots.get(owner).balances());
            assertEquals(42, snapshots.get(owner).kills());
        }
        assertEquals(EconomyService.Balances.empty(), snapshots.get(missing).balances());
        assertEquals(0, snapshots.get(missing).kills());
        assertThrows(UnsupportedOperationException.class, () -> snapshots.clear());
        assertTrue(economy.playerSnapshots(List.of()).join().isEmpty());
        long stored =
                db.submit(
                                c -> {
                                    try (var s = c.createStatement();
                                            var r =
                                                    s.executeQuery(
                                                            "SELECT COUNT(*) FROM"
                                                                    + " player_balances")) {
                                        return r.getLong(1);
                                    } catch (java.sql.SQLException e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .join();
        assertEquals(600, stored); // La lecture ne crée pas de compte et ne distribue pas de solde.
    }

    @Test
    void reportsLocalBatchCostWithoutATimingDependentAssertion() {
        var selected = owners.subList(0, 100);
        for (int i = 0; i < 5; i++) economy.playerSnapshots(selected).join();
        long start = System.nanoTime();
        for (int i = 0; i < 3; i++)
            for (UUID owner : selected)
                assertEquals(42, economy.playerSnapshot(owner).join().kills());
        long separate = System.nanoTime() - start;
        start = System.nanoTime();
        for (int i = 0; i < 3; i++) {
            var snapshots = economy.playerSnapshots(selected).join();
            assertEquals(100, snapshots.size());
            assertTrue(snapshots.values().stream().allMatch(s -> s.kills() == 42));
        }
        long batch = System.nanoTime() - start;
        System.out.printf(
                Locale.ROOT,
                "[BATCH_SNAPSHOT_BENCH] 3 cycles x 100 joueurs : individuelles=%.1f ms,"
                        + " groupees=%.1f ms; requetes/cycle 100 -> 1%n",
                separate / 1e6,
                batch / 1e6);
    }
}
