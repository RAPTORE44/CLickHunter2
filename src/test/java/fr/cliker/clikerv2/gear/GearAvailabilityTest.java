package fr.cliker.clikerv2.gear;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.economy.CurrencyType;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class GearAvailabilityTest {
    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void absentDeliveryServiceNeverRemovesOrTransformsInput() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var player = server.addPlayer();
        var inventory = Bukkit.createInventory(null, 27);
        var original = new ItemStack(Material.DIAMOND_SWORD);
        inventory.setItem(4, original);
        GearTransactions.apply(
                plugin,
                null,
                player,
                inventory,
                4,
                CurrencyType.ECUS,
                100,
                item -> fail("No transformation without delivery"),
                () -> {});
        assertEquals(original, inventory.getItem(4));
        assertFalse(GearTransactions.busy(inventory));
    }
}
