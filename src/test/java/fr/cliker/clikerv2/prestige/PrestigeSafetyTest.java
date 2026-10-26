package fr.cliker.clikerv2.prestige;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.mobs.MobKillStore;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class PrestigeSafetyTest {
    private DatabaseManager db;
    private PrestigeStore store;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var p = MockBukkit.createMockPlugin();
        db = new DatabaseManager(p, "prestige-" + UUID.randomUUID() + ".db");
        db.initialize();
        store = new PrestigeStore(db);
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
    void extremePrestigeComputationsStayBoundedAndNormalRequirementsRemainIdentical() {
        for (int p = 5; p <= 100; p++) {
            long expected =
                    java.math.BigInteger.valueOf(150000)
                            .shiftLeft(p - 5)
                            .min(java.math.BigInteger.valueOf(Long.MAX_VALUE))
                            .longValueExact();
            assertEquals(expected, PrestigeType.requiredKills(p));
            assertEquals(p * 100, PrestigeType.requiredLevel(p));
        }
        assertEquals(Long.MAX_VALUE, PrestigeType.requiredKills(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, PrestigeType.requiredLevel(Integer.MAX_VALUE));
        assertEquals(Integer.toString(Integer.MAX_VALUE), PrestigeType.toRoman(Integer.MAX_VALUE));
        assertEquals("XCIX", PrestigeType.toRoman(99));
        assertThrows(IllegalArgumentException.class, () -> PrestigeType.requiredKills(-1));
        assertThrows(IllegalArgumentException.class, () -> PrestigeType.requiredLevel(-1));
    }

    @Test
    void corruptPrestigeIsRejectedWithoutChangingSavedValue() {
        db.submit(
                        c -> {
                            try (var s =
                                    c.prepareStatement(
                                            "INSERT INTO player_prestige(uuid,level)"
                                                    + " VALUES(?,?)")) {
                                s.setString(1, owner.toString());
                                s.setLong(2, 4294967296L);
                                s.executeUpdate();
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertThrows(
                java.util.concurrent.CompletionException.class, () -> store.getLevel(owner).join());
        assertFalse(store.advance(owner, -1, 0).join());
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> store.setLevel(owner, -1).join());
        long value =
                db.submit(
                                c -> {
                                    try (var s = c.createStatement();
                                            var rows =
                                                    s.executeQuery(
                                                            "SELECT level FROM player_prestige")) {
                                        rows.next();
                                        return rows.getLong(1);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .join();
        assertEquals(4294967296L, value);
    }

    @Test
    void concurrentRequestsCannotGrantSamePrestigeTwiceAndResetPersists() {
        new MobKillStore(db).add(owner, 10000).join();
        var a = store.advance(owner, 0, 100);
        var b = store.advance(owner, 0, 100);
        assertTrue(a.join());
        assertFalse(b.join());
        assertEquals(1, store.getLevel(owner).join());
        var reset = new PrestigeStore(db).pendingReset(owner).join();
        assertEquals(1, reset.level());
        store.acknowledgeReset(owner, 2).join();
        assertNotNull(store.pendingReset(owner).join());
        store.acknowledgeReset(owner, 1).join();
        assertNull(store.pendingReset(owner).join());
    }

    @Test
    void nextPrestigeCannotOverwriteAnUnacknowledgedExperienceReset() {
        new MobKillStore(db).add(owner, 10000).join();
        assertTrue(store.advance(owner, 0, 2500).join());
        var reset = store.pendingReset(owner).join();
        assertNotNull(reset);
        assertEquals(1, reset.level());

        // Le cache du manager peut déjà être à 1 alors que le joueur a encore son ancien XP.
        // La reprise après redémarrage doit garder la même protection SQL.
        var restarted = new PrestigeStore(db);
        assertFalse(restarted.advance(owner, 1, 10000).join());
        assertEquals(1, restarted.getLevel(owner).join());
        assertEquals(reset, restarted.pendingReset(owner).join());

        restarted.acknowledgeReset(owner, 2).join();
        assertFalse(restarted.advance(owner, 1, 10000).join());
        restarted.acknowledgeReset(owner, 1).join();
        assertTrue(restarted.advance(owner, 1, 10000).join());
        assertEquals(2, restarted.pendingReset(owner).join().level());
    }

    @Test
    void missingKillsAndMaximumPrestigeNeverAdvance() {
        assertFalse(store.advance(owner, 0, 100).join());
        assertNull(store.pendingReset(owner).join());
        store.setLevel(owner, 100).join();
        new MobKillStore(db).add(owner, Long.MAX_VALUE).join();
        assertFalse(store.advance(owner, 100, 1).join());
        assertEquals(100, store.getLevel(owner).join());
    }
}
