package fr.cliker.clikerv2.runtime;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.mobs.MobKillStore;
import fr.cliker.clikerv2.prestige.*;
import fr.cliker.clikerv2.upgrade.*;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.*;

class SessionCallbacksTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private DatabaseManager db;
    private PlayerMock old;
    private boolean watch;
    private boolean staleAttribute;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "sessions-" + UUID.randomUUID() + ".db");
        db.initialize();
        old = player("Reconnect", UUID.randomUUID());
        server.addPlayer(old);
        old.addAttachment(plugin, "cliker.upgrade.use", true);
        old.addAttachment(plugin, "cliker.prestige.use", true);
    }

    private PlayerMock player(String name, UUID id) {
        return new PlayerMock(server, name, id) {
            @Override
            public boolean isOnline() {
                return Bukkit.getPlayer(getUniqueId()) != null;
            }

            @Override
            public void saveData() {
                assertSame(
                        this, Bukkit.getPlayer(getUniqueId()), "Stale player must never be saved");
            }

            @Override
            public AttributeInstance getAttribute(Attribute type) {
                if (watch && Bukkit.getPlayer(getUniqueId()) != this) staleAttribute = true;
                // MockBukkit 4.36 ne modélise pas l'attribut ARMOR du joueur.
                if (type == Attribute.ARMOR) return null;
                var delegate = super.getAttribute(type);
                if (delegate == null) return null;
                // MockBukkit 4.36 ne fournit pas getModifier(NamespacedKey).
                return (AttributeInstance)
                        java.lang.reflect.Proxy.newProxyInstance(
                                AttributeInstance.class.getClassLoader(),
                                new Class<?>[] {AttributeInstance.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("getModifier")
                                            && args[0] instanceof org.bukkit.NamespacedKey key)
                                        return delegate.getModifiers().stream()
                                                .filter(m -> key.equals(m.getKey()))
                                                .findFirst()
                                                .orElse(null);
                                    try {
                                        return method.invoke(delegate, args);
                                    } catch (java.lang.reflect.InvocationTargetException e) {
                                        throw e.getCause();
                                    }
                                });
            }
        };
    }

    private void drain() {
        for (int i = 0; i < 8; i++) {
            db.submit(c -> null).join();
            server.getScheduler().performOneTick();
        }
    }

    private PlayerMock reconnect() {
        UUID id = old.getUniqueId();
        String name = old.getName();
        assertTrue(old.disconnect());
        var fresh = player(name, id);
        server.addPlayer(fresh);
        fresh.addAttachment(plugin, "cliker.upgrade.use", true);
        fresh.addAttachment(plugin, "cliker.prestige.use", true);
        watch = true;
        return fresh;
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
    void obsoleteUpgradeLoadNeverAppliesAttributesToOldPlayer() {
        var store = new UpgradeStore(db);
        var manager = new UpgradeManager(plugin, store, new EconomyService(db, Map.of()));
        server.getPluginManager().registerEvents(manager, plugin);
        manager.loadOnlinePlayers();
        db.submit(c -> null).join();
        store.setLevel(old.getUniqueId(), UpgradeType.FORCE, 5).join();
        var fresh = reconnect();
        drain();
        assertFalse(staleAttribute);
        assertEquals(5, manager.getLevel(fresh.getUniqueId(), UpgradeType.FORCE));
    }

    @Test
    void committedUpgradeAfterReconnectUpdatesFreshSessionWithoutOldGuiCallback() {
        var economy = new EconomyService(db, Map.of());
        var manager = new UpgradeManager(plugin, new UpgradeStore(db), economy);
        server.getPluginManager().registerEvents(manager, plugin);
        manager.loadOnlinePlayers();
        drain();
        economy.set(old.getUniqueId(), old.getName(), CurrencyType.GEMMES_SOMBRES, 100000).join();
        var callbacks = new java.util.concurrent.atomic.AtomicInteger();
        manager.purchase(old, UpgradeType.FORCE, callbacks::incrementAndGet);
        db.submit(c -> null).join();
        var fresh = reconnect();
        drain();
        assertEquals(0, callbacks.get());
        assertFalse(staleAttribute);
        assertEquals(1, manager.getLevel(fresh.getUniqueId(), UpgradeType.FORCE));
        assertEquals(90000, economy.getBalances(fresh.getUniqueId()).join().gemmesSombres());
    }

    @Test
    void committedPrestigeAfterReconnectResetsOnlyFreshPlayerAndPreservesOldLevel() {
        var store = new PrestigeStore(db);
        var kills = new MobKillStore(db);
        var manager = new PrestigeManager(plugin, store, kills);
        server.getPluginManager().registerEvents(manager, plugin);
        manager.loadOnlinePlayers();
        drain();
        kills.add(old.getUniqueId(), 100000).join();
        old.setLevel(200);
        var callbacks = new java.util.concurrent.atomic.AtomicInteger();
        manager.attemptPrestige(old, callbacks::incrementAndGet);
        db.submit(c -> null).join();
        var fresh = reconnect();
        fresh.setLevel(200);
        drain();
        assertEquals(0, callbacks.get());
        assertEquals(200, old.getLevel());
        assertEquals(0, fresh.getLevel());
        assertEquals(1, manager.getLevel(fresh.getUniqueId()));
        assertNull(store.pendingReset(fresh.getUniqueId()).join());
        manager.shutdown();
    }
}
