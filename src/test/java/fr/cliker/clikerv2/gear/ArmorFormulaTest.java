package fr.cliker.clikerv2.gear;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class ArmorFormulaTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        TierGear.init(plugin);
        ItemMods.init(plugin);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void displayedReductionUsesActualFormulaAndCapsAtEightyPercent() {
        var player = MockBukkit.getMock().addPlayer();
        assertEquals(0, GearAttributeManager.armorBonuses(player).reductionPercent());
        var pieces = new ItemStack[4];
        int n = 0;
        for (var slot : TierGear.ArmorSlot.values()) {
            var item = TierGear.createArmor(MobTier.VACHE, slot);
            ItemMods.setEnchantLevel(item, EnchantType.DEFENCE, 5);
            ItemMods.setReforge(item, ReforgeType.FERME);
            pieces[n++] = item;
        }
        player.getInventory().setArmorContents(pieces);
        assertEquals(62, GearAttributeManager.armorBonuses(player).reductionPercent());
        for (var item : pieces) ItemMods.setEnchantLevel(item, EnchantType.DEFENCE, 20);
        player.getInventory().setArmorContents(pieces);
        assertEquals(80, GearAttributeManager.armorBonuses(player).reductionPercent());
    }
}
