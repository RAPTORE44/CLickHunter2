package fr.cliker.clikerv2.features;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

class BoostsTest {
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private PlayerMock player;
    private Boosts boosts;

    @BeforeEach
    void setup() {
        var server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
        boosts = new Boosts(plugin);
    }

    @AfterEach
    void close() {
        try {
            boosts.shutdown();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void effect(String type, int value) {
        player.getPersistentDataContainer()
                .set(
                        new NamespacedKey(plugin, "boost_" + type + "_expiry"),
                        PersistentDataType.LONG,
                        System.currentTimeMillis() + 60000);
        player.getPersistentDataContainer()
                .set(
                        new NamespacedKey(plugin, "boost_" + type + "_value"),
                        PersistentDataType.INTEGER,
                        value);
    }

    @Test
    void milkClearsEveryCustomBonusIncludingGemmesAndCendres() {
        var types = List.of("chance", "coins", "xp", "vitesse", "degats", "gemmes", "cendres");
        for (String type : types) {
            effect(type, 2);
            assertEquals(2, Boosts.multiplier(player, type));
        }
        boosts.milk(
                new PlayerItemConsumeEvent(
                        player,
                        new ItemStack(Material.MILK_BUCKET),
                        org.bukkit.inventory.EquipmentSlot.HAND));
        for (String type : types) {
            assertEquals(1, Boosts.multiplier(player, type));
            assertFalse(
                    player.getPersistentDataContainer()
                            .has(new NamespacedKey(plugin, "boost_" + type + "_expiry")));
            assertFalse(
                    player.getPersistentDataContainer()
                            .has(new NamespacedKey(plugin, "boost_" + type + "_value")));
        }
    }

    @Test
    void malformedStoredMultipliersNeverProduceNegativeOrUnboundedGains() {
        for (int value : new int[] {Integer.MIN_VALUE, -1, 0, 1, 5, Integer.MAX_VALUE}) {
            effect("coins", value);
            assertEquals(1, Boosts.multiplier(player, "coins"));
        }
        effect("coins", 4);
        assertEquals(4, Boosts.multiplier(player, "coins"));
    }
}
