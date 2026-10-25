package fr.cliker.clikerv2.storage;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.config.StoredLocation;
import fr.cliker.clikerv2.hologram.HologramStore;
import fr.cliker.clikerv2.merchant.MerchantStore;
import fr.cliker.clikerv2.mobs.MobSpawnStore;
import fr.cliker.clikerv2.spawn.SpawnStore;
import fr.cliker.clikerv2.station.*;

import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

class PositionStoreTest {
    private ServerMock server;
    private DatabaseManager db;
    private World world;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "positions-" + UUID.randomUUID() + ".db");
        db.initialize();
        world = server.addSimpleWorld("positions");
    }

    @AfterEach
    void cleanup() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void await(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
            server.getScheduler().performOneTick();
        }
        assertTrue(ready.getAsBoolean());
    }

    @Test
    void mutableLocationsAreCapturedBeforeQueueingSql() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        db.submit(
                c -> {
                    entered.countDown();
                    try {
                        release.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        Location location = new Location(world, 1.5, 65, 2.5, 90, 0);
        var store = new MobSpawnStore(db);
        var save = store.save("id", "vache", location, false);
        location.setX(999);
        release.countDown();
        save.join();
        var loaded = store.loadAll();
        await(loaded::isDone);
        assertEquals(1.5, loaded.join().getFirst().location().getX());
    }

    @Test
    void everyLocationStoreResolvesOnMainAndPreservesUnknownWorldRows() throws Exception {
        Location location = new Location(world, 3, 65, 7, 45, 10);
        var mob = new MobSpawnStore(db);
        var merchant = new MerchantStore(db);
        var holo = new HologramStore(db);
        var station = new StationStore(db);
        var spawn = new SpawnStore(db);
        mob.save("mob", "vache", location, true).join();
        merchant.save("merchant", "vache", location).join();
        holo.save("holo", "kills", location).join();
        station.save("station", StationType.ENCHANT, location.getBlock()).join();
        spawn.save(location).join();
        var futures =
                List.of(
                        mob.loadAll(),
                        merchant.loadAll(),
                        holo.loadAll(),
                        station.loadAll(),
                        spawn.load());
        var main = new java.util.concurrent.atomic.AtomicBoolean();
        var all =
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .thenRun(() -> main.set(Bukkit.isPrimaryThread()));
        await(all::isDone);
        all.join();
        assertTrue(main.get());
        assertEquals(10, ((Location) futures.getLast().join()).getPitch());
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.executeUpdate("UPDATE mob_spawns SET world='not-loaded'");
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        var missing = mob.loadAll();
        await(missing::isDone);
        assertTrue(missing.join().isEmpty());
        assertEquals(
                1,
                db.submit(
                                c -> {
                                    try (var s = c.createStatement();
                                            var r =
                                                    s.executeQuery(
                                                            "SELECT COUNT(*) FROM mob_spawns")) {
                                        r.next();
                                        return r.getInt(1);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .join());
    }

    @Test
    void worldResolutionIsRejectedOnWorkerThreads() {
        var saved = StoredLocation.capture(new Location(world, 0, 65, 0));
        assertThrows(
                CompletionException.class, () -> CompletableFuture.runAsync(saved::resolve).join());
    }

    @Test
    void corruptCoordinatesDoNotLoadAtZeroOrHideOtherRows() throws Exception {
        var store = new MobSpawnStore(db);
        var location = new Location(world, 1.5, 65, 2.5);
        store.save("good", "vache", location, false).join();
        store.save("bad", "cochon", location, false).join();
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.executeUpdate(
                                        "UPDATE mob_spawns SET x='not-a-number' WHERE id='bad'");
                                return null;
                            } catch (Exception error) {
                                throw new RuntimeException(error);
                            }
                        })
                .join();
        var loaded = store.loadAll();
        await(loaded::isDone);
        assertEquals(1, loaded.join().size());
        assertEquals("good", loaded.join().getFirst().id());
        assertEquals(
                2,
                db.submit(
                                c -> {
                                    try (var s = c.createStatement();
                                            var rs =
                                                    s.executeQuery(
                                                            "SELECT COUNT(*) FROM mob_spawns")) {
                                        rs.next();
                                        return rs.getInt(1);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .join());
        assertThrows(
                IllegalArgumentException.class,
                () -> StoredLocation.capture(new Location(world, Double.NaN, 65, 0)));
    }
}
