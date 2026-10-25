package fr.cliker.clikerv2.features;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.*;
import fr.cliker.clikerv2.essence.*;

import org.bukkit.*;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class SellClickTest {
    ServerMock server;
    org.mockbukkit.mockbukkit.entity.PlayerMock p;
    org.bukkit.plugin.java.JavaPlugin plugin;
    DatabaseManager db;
    EconomyService economy;
    SellMenu menu;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        p = server.addPlayer();
        EssenceItem.init(plugin);
        db = new DatabaseManager(plugin, "sell-" + UUID.randomUUID() + ".db");
        db.initialize();
        economy = new EconomyService(db, Map.of());
        economy.set(p.getUniqueId(), p.getName(), CurrencyType.ECUS, 0).join();
        menu = new SellMenu(plugin, economy);
        server.getPluginManager().registerEvents(menu, plugin);
    }

    @AfterEach
    void cleanup() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    void click(ClickType type) {
        // MockBukkit ne convertit pas le slot de getCurrentItem dans une vue de coffre de neuf
        // cases.
        var event =
                new InventoryClickEvent(
                        p.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        java.util.stream.IntStream.range(9, 45)
                                .filter(slot -> p.getOpenInventory().convertSlot(slot) == 0)
                                .findFirst()
                                .orElseThrow(),
                        type,
                        InventoryAction.PICKUP_ALL) {
                    @Override
                    public org.bukkit.inventory.ItemStack getCurrentItem() {
                        return p.getInventory().getItem(0);
                    }
                };
        menu.onClick(event);
        db.submit(c -> null).join();
        server.getScheduler().performOneTick();
    }

    @Test
    void leftSellsOneAndRightOnlyTheClickedRemainingStack() {
        p.getInventory().setItem(0, EssenceItem.create(MobTier.VACHE, 10));
        p.getInventory().setItem(1, EssenceItem.create(MobTier.VACHE, 20));
        menu.open(p);
        click(ClickType.LEFT);
        assertEquals(9, p.getInventory().getItem(0).getAmount());
        assertEquals(20, p.getInventory().getItem(1).getAmount());
        assertEquals(50, economy.getBalances(p.getUniqueId()).join().ecus());
        click(ClickType.RIGHT);
        assertTrue(
                p.getInventory().getItem(0) == null
                        || p.getInventory().getItem(0).getType().isAir());
        assertEquals(20, p.getInventory().getItem(1).getAmount());
        assertEquals(500, economy.getBalances(p.getUniqueId()).join().ecus());
    }

    @Test
    void shiftAndNumberKeysNeverSell() {
        p.getInventory().setItem(0, EssenceItem.create(MobTier.VACHE, 10));
        menu.open(p);
        click(ClickType.SHIFT_LEFT);
        assertEquals(10, p.getInventory().getItem(0).getAmount());
        assertEquals(0, economy.getBalances(p.getUniqueId()).join().ecus());
    }

    @Test
    void nonSellableItemsCannotBeShiftTransferredIntoTheEmptySellMenu() {
        p.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.DIAMOND, 10));
        menu.open(p);
        var event =
                new InventoryClickEvent(
                        p.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        36,
                        ClickType.SHIFT_LEFT,
                        InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    @Override
                    public org.bukkit.inventory.ItemStack getCurrentItem() {
                        return p.getInventory().getItem(0);
                    }
                };
        menu.onClick(event);
        assertTrue(event.isCancelled());
        assertEquals(10, p.getInventory().getItem(0).getAmount());
        assertTrue(
                java.util.Arrays.stream(p.getOpenInventory().getTopInventory().getContents())
                        .allMatch(i -> i == null || i.getType().isAir()));
    }
}
