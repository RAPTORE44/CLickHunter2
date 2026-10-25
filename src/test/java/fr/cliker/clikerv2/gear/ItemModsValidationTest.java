package fr.cliker.clikerv2.gear;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class ItemModsValidationTest {
    @Test
    void legacyLevelsArePreservedButCorruptLevelsCannotCreateUnboundedBonuses() {
        MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            ItemMods.init(plugin);
            var item = new ItemStack(Material.DIAMOND_SWORD);
            ItemMods.setEnchantLevel(item, EnchantType.STRENGTH, 20);
            assertEquals(20, ItemMods.getEnchantLevel(item, EnchantType.STRENGTH));
            var meta = item.getItemMeta();
            meta.getPersistentDataContainer()
                    .set(
                            new NamespacedKey(plugin, "cliker_enchants"),
                            PersistentDataType.STRING,
                            "STRENGTH:2147483647");
            item.setItemMeta(meta);
            assertEquals(0, ItemMods.getEnchantLevel(item, EnchantType.STRENGTH));
            assertEquals(
                    "STRENGTH:2147483647",
                    item.getItemMeta()
                            .getPersistentDataContainer()
                            .get(
                                    new NamespacedKey(plugin, "cliker_enchants"),
                                    PersistentDataType.STRING));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ItemMods.setRuneLevel(item, RuneType.XP, -1));
        } finally {
            MockBukkit.unmock();
        }
    }
}
