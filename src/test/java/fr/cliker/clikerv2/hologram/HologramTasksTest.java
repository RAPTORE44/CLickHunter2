package fr.cliker.clikerv2.hologram;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.*;

class HologramTasksTest {
    private BukkitTask task(HologramManager manager) throws Exception {
        var f = HologramManager.class.getDeclaredField("refreshTask");
        f.setAccessible(true);
        return (BukkitTask) f.get(manager);
    }

    @Test
    void leaderboardRefreshIsSingleAndStopsBeforeTheSharedProvider() throws Exception {
        MockBukkit.mock();
        SharedHolograms shared = null;
        try {
            var plugin = MockBukkit.createMockPlugin();
            shared = new SharedHolograms(plugin);
            var manager = new HologramManager(plugin, null, null, null, null);
            manager.start();
            var task = task(manager);
            assertFalse(task.isCancelled());
            manager.start();
            assertSame(task, task(manager));
            manager.shutdown();
            assertTrue(task.isCancelled());
            manager.start();
            assertSame(task, task(manager));
            assertTrue(task.isCancelled());
        } finally {
            if (shared != null) shared.shutdown();
            MockBukkit.unmock();
        }
    }
}
