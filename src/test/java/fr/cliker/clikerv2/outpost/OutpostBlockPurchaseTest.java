package fr.cliker.clikerv2.outpost;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class OutpostBlockPurchaseTest {
    DatabaseManager db;
    EconomyService economy;
    OutpostBlocks shop;
    UUID owner = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "blockshop-" + UUID.randomUUID() + ".db");
        db.initialize();
        economy = new EconomyService(db, Map.of());
        economy.set(owner, "Builder", CurrencyType.ECUS, 1000).join();
        shop =
                new OutpostBlocks(
                        plugin, db, new YamlConfiguration(), server.addSimpleWorld("plots"));
        shop.start();
        db.submit(c -> null).join();
        server.getScheduler().performOneTick();
    }

    @AfterEach
    void cleanup() {
        if (shop != null) shop.shutdown();
        if (db != null) db.close();
        MockBukkit.unmock();
    }

    long count() {
        return db.submit(
                        c -> {
                            try (var s = c.createStatement();
                                    var r =
                                            s.executeQuery(
                                                    "SELECT COUNT(*) FROM"
                                                            + " outpost_block_deliveries")) {
                                r.next();
                                return r.getLong(1);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
    }

    @Test
    void purchaseDebitsAndStoresDeliveryAtomically() {
        assertTrue(shop.purchase(owner, Material.STONE, 64, 640).join());
        assertEquals(360, economy.getBalances(owner).join().ecus());
        assertEquals(1, count());
        assertFalse(shop.purchase(owner, Material.STONE, 64, 640).join());
        assertEquals(1, count());
    }

    @Test
    void simultaneousPurchasesNeverOverspend() {
        var a = shop.purchase(owner, Material.STONE, 64, 640);
        var b = shop.purchase(owner, Material.STONE, 64, 640);
        assertTrue(a.join());
        assertFalse(b.join());
        assertEquals(360, economy.getBalances(owner).join().ecus());
        assertEquals(1, count());
    }

    @Test
    void failedDeliveryInsertRollsBackDebit() {
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.execute(
                                        "CREATE TRIGGER reject_block BEFORE INSERT ON"
                                                + " outpost_block_deliveries BEGIN SELECT"
                                                + " RAISE(ABORT,'injected');END");
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> shop.purchase(owner, Material.STONE, 1, 10).join());
        assertEquals(1000, economy.getBalances(owner).join().ecus());
        assertEquals(0, count());
    }
}
