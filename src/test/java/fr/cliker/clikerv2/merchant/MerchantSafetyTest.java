package fr.cliker.clikerv2.merchant;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.essence.*;
import fr.cliker.clikerv2.gear.*;

import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class MerchantSafetyTest {
    private PlayerMock player;
    private MerchantMenu menu;

    @BeforeEach
    void setup() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
        TierGear.init(plugin);
        EssenceItem.init(plugin);
        CompressorItem.init(plugin);
        CharmItem.init(plugin);
        menu = new MerchantMenu(plugin, null);
        menu.open(player, MobTier.VACHE);
        player.getInventory().setItem(0, EssenceItem.create(MobTier.VACHE, 64));
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    private void click(int slot, ClickType type) {
        var e =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        slot,
                        type,
                        InventoryAction.PICKUP_ALL);
        menu.onClick(e);
        assertTrue(e.isCancelled());
    }

    @Test
    void absentDeliveryDoesNotConsumeCurrencyOrEssences() {
        click(2, ClickType.LEFT);
        assertEquals(64, EssenceItem.count(player, MobTier.VACHE, EssenceRarity.COMMUNE));
    }

    @Test
    void numberKeyAndOffhandSwapNeverCompressResources() {
        click(15, ClickType.NUMBER_KEY);
        click(15, ClickType.SWAP_OFFHAND);
        click(15, ClickType.DROP);
        assertEquals(64, EssenceItem.count(player, MobTier.VACHE, EssenceRarity.COMMUNE));
    }
}
