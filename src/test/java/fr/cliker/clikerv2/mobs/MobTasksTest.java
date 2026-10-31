package fr.cliker.clikerv2.mobs;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.*;

class MobTasksTest {
    private BukkitTask task(CustomMobManager manager, String field) throws Exception {
        var f = CustomMobManager.class.getDeclaredField(field);
        f.setAccessible(true);
        return (BukkitTask) f.get(manager);
    }

    @Test
    void startsAreIdempotentAndShutdownCancelsAllThreeMobTasks() throws Exception {
        MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            var manager = new CustomMobManager(plugin, null, null, null, null, null, null, null);
            manager.startAnchor();
            manager.startPeriodicReset();
            var anchor = task(manager, "anchorTask");
            var recovery = task(manager, "recoveryTask");
            var reset = task(manager, "resetTask");
            assertFalse(anchor.isCancelled());
            assertFalse(recovery.isCancelled());
            assertFalse(reset.isCancelled());
            manager.startAnchor();
            manager.startPeriodicReset();
            assertSame(anchor, task(manager, "anchorTask"));
            assertSame(recovery, task(manager, "recoveryTask"));
            assertSame(reset, task(manager, "resetTask"));
            manager.shutdown();
            assertTrue(anchor.isCancelled());
            assertTrue(recovery.isCancelled());
            assertTrue(reset.isCancelled());
            manager.startAnchor();
            manager.startPeriodicReset();
            assertSame(anchor, task(manager, "anchorTask"));
            assertTrue(anchor.isCancelled());
        } finally {
            MockBukkit.unmock();
        }
    }
}
