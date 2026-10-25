package fr.cliker.clikerv2.upgrade;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.concurrent.*;

class UpgradeSafetyTest {
    private DatabaseManager db;
    private UpgradeStore store;
    private EconomyService economy;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var p = MockBukkit.createMockPlugin();
        db = new DatabaseManager(p, "upgrades-" + UUID.randomUUID() + ".db");
        db.initialize();
        store = new UpgradeStore(db);
        economy = new EconomyService(db, Map.of());
    }

    @AfterEach
    void cleanup() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void concurrentPurchasesUseActualDatabaseLevelAndChargeEachNextPrice() {
        economy.set(owner, "Joueur", CurrencyType.GEMMES_SOMBRES, 100000).join();
        var a = store.purchase(owner, UpgradeType.FORCE);
        var b = store.purchase(owner, UpgradeType.FORCE);
        assertEquals(1, a.join());
        assertEquals(2, b.join());
        assertEquals(70000, economy.getBalances(owner).join().gemmesSombres());
        assertEquals(2, store.getLevels(owner).join().get(UpgradeType.FORCE));
    }

    @Test
    void failedUpgradeDoesNotConsumeMoney() {
        economy.set(owner, "Joueur", CurrencyType.GEMMES_SOMBRES, 100000).join();
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.execute(
                                        "CREATE TRIGGER reject_upgrade BEFORE UPDATE ON"
                                                + " player_upgrades BEGIN SELECT"
                                                + " RAISE(ABORT,'failure'); END");
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertThrows(
                CompletionException.class, () -> store.purchase(owner, UpgradeType.FORCE).join());
        assertEquals(100000, economy.getBalances(owner).join().gemmesSombres());
        assertEquals(0, store.getLevels(owner).join().get(UpgradeType.FORCE));
    }

    @Test
    void maximumAndInsufficientFundsNeverIncrementLevel() {
        assertEquals(-1, store.purchase(owner, UpgradeType.FORCE).join());
        store.setLevel(owner, UpgradeType.FORCE, UpgradeType.MAX_LEVEL).join();
        assertEquals(-2, store.purchase(owner, UpgradeType.FORCE).join());
    }
}
