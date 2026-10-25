package fr.cliker.clikerv2.ui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.daily.*;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.*;

import java.time.*;

class DailyIconsRefreshTest {
    @Test
    void everySecondRefreshKeepsItemsAdderIconsImmediately() throws Exception {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        DailyModule module = null;
        MenuIcons icons = null;
        try {
            java.nio.file.Files.createDirectories(plugin.getDataFolder().toPath());
            try (var in = plugin.getResource("daily.yml")) {
                java.nio.file.Files.copy(
                        in,
                        fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "daily.yml").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            module = new DailyModule(plugin);
            icons =
                    new MenuIcons(
                            plugin,
                            id -> {
                                var i = new ItemStack(Material.PAPER);
                                var m = i.getItemMeta();
                                m.setCustomModelData(777);
                                i.setItemMeta(m);
                                return i;
                            });
            icons.start();
            var player = server.addPlayer();
            var menu = new DailyMenu(module);
            var clock = DailyModule.class.getDeclaredField("clock");
            clock.setAccessible(true);
            Instant now = Instant.parse("2026-10-25T12:00:00Z");
            clock.set(module, Clock.fixed(now, ZoneId.of("Europe/Paris")));
            menu.open(player, DailyProgress.EMPTY);
            for (int second = 1; second <= 5; second++) {
                clock.set(module, Clock.fixed(now.plusSeconds(second), ZoneId.of("Europe/Paris")));
                menu.refreshOpenMenus();
                var item = player.getOpenInventory().getTopInventory().getItem(DailyMenu.slot(2));
                assertEquals(Material.PAPER, item.getType());
                assertEquals(777, item.getItemMeta().getCustomModelData());
            }
        } finally {
            if (icons != null) icons.shutdown();
            if (module != null) module.shutdown();
            MockBukkit.unmock();
        }
    }
}
