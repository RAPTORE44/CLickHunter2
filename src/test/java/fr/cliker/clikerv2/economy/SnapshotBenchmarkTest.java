package fr.cliker.clikerv2.economy;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.mobs.MobKillStore;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

/**
 * Mesure locale reproductible, sans seuil dépendant de la machine. Ce n'est pas un test de charge
 * Paper.
 */
class SnapshotBenchmarkTest {
    @Test
    void combinedReadMatchesPreviousTwoReadsAndReportsLocalCost() throws Exception {
        MockBukkit.mock();
        DatabaseManager db = null;
        try {
            var plugin = MockBukkit.createMockPlugin();
            db = new DatabaseManager(plugin, "snapshot-bench-" + UUID.randomUUID() + ".db");
            db.initialize();
            var economy = new EconomyService(db, Map.of());
            var kills = new MobKillStore(db);
            UUID owner = UUID.randomUUID();
            economy.set(owner, "Mesure", CurrencyType.ECUS, 12345).join();
            kills.add(owner, 123).join();
            int count = 300;
            for (int i = 0; i < 20; i++) economy.playerSnapshot(owner).join();
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                assertEquals(12345, economy.getBalances(owner).join().ecus());
                assertEquals(123, kills.getTotal(owner).join());
            }
            long split = System.nanoTime() - start;
            start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                var snapshot = economy.playerSnapshot(owner).join();
                assertEquals(12345, snapshot.balances().ecus());
                assertEquals(123, snapshot.kills());
            }
            long combined = System.nanoTime() - start;
            System.out.printf(
                    Locale.ROOT,
                    "[SNAPSHOT_BENCH] %d lectures: separees=%.1f ms, combinees=%.1f ms;"
                            + " requetes/lecture: 2 -> 1%n",
                    count,
                    split / 1e6,
                    combined / 1e6);
        } finally {
            if (db != null) db.close();
            MockBukkit.unmock();
        }
    }
}
