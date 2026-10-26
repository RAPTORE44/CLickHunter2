package fr.cliker.clikerv2.liveauction;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.*;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;

import java.util.*;

class LiveAuctionMenuTest {
    private ServerMock server;
    private DatabaseManager db;
    private LiveAuctions module;
    private LiveAuctionStore store;
    private ClaimService claims;
    private PlayerMock player;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        ConfigurationSerialization.registerClass(ItemStackMock.class);
        db = new DatabaseManager(plugin, "live-menu-" + UUID.randomUUID() + ".db");
        db.initialize();
        store = new LiveAuctionStore(db);
        store.initialize().join();
        var economy = new EconomyService(db, Map.of());
        claims = ClaimTestFactory.create(plugin, db);
        player = server.addPlayer();
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.ECUS, 10000).join();
        String item = ClaimService.serialize(new ItemStack(Material.DIAMOND_SWORD));
        store.create("event", List.of(item, item, item), 180000).join();
        var config = new fr.cliker.clikerv2.features.Settings(plugin, "activites/encheres.yml");
        config.yaml.set("entree-ecus", 0);
        fr.cliker.clikerv2.config.YamlFiles.location(config.yaml, "salle", player.getLocation());
        config.save();
        module = new LiveAuctions(plugin, db, economy, claims);
        set("ready", true);
        set("state", store.state("event").join());
        server.getPluginManager().registerEvents(module, plugin);
    }

    private void set(String name, Object value) throws Exception {
        var f = LiveAuctions.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(module, value);
    }

    private void drain() {
        for (int i = 0; i < 12; i++) {
            db.submit(c -> null).join();
            server.getScheduler().performOneTick();
        }
    }

    @AfterEach
    void cleanup() {
        try {
            module.shutdown();
            claims.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void leavingDuringPendingJoinNeverReopensTheMenuAndCancelsRegistration() {
        module.onCommand(player, null, "enchere", new String[] {"rejoindre"});
        module.onCommand(player, null, "enchere", new String[] {"quitter"});
        drain();
        var top = player.getOpenInventory().getTopInventory();
        assertTrue(
                top == null
                        || top.getHolder() == null
                        || !top.getHolder().getClass().getName().contains("LiveAuctions$View"));
        assertEquals(0, store.state("event").join().members());
    }

    @Test
    void shiftNumericAndDragCannotExtractItemsOrPlaceOffers() throws Exception {
        store.join("event", player.getUniqueId()).join();
        set("state", store.advance("event", 30000, true).join().state());
        module.onCommand(player, null, "enchere", new String[] {"rejoindre"});
        drain();
        var view = player.getOpenInventory();
        assertEquals(54, view.getTopInventory().getSize());
        for (ClickType type :
                List.of(
                        ClickType.SHIFT_LEFT,
                        ClickType.NUMBER_KEY,
                        ClickType.SWAP_OFFHAND,
                        ClickType.DOUBLE_CLICK)) {
            var e =
                    type == ClickType.NUMBER_KEY
                            ? new InventoryClickEvent(
                                    view,
                                    InventoryType.SlotType.CONTAINER,
                                    0,
                                    type,
                                    InventoryAction.HOTBAR_SWAP,
                                    1)
                            : new InventoryClickEvent(
                                    view,
                                    InventoryType.SlotType.CONTAINER,
                                    0,
                                    type,
                                    InventoryAction.MOVE_TO_OTHER_INVENTORY);
            server.getPluginManager().callEvent(e);
            assertTrue(e.isCancelled());
        }
        var drag =
                new InventoryDragEvent(
                        view,
                        new ItemStack(Material.DIAMOND),
                        new ItemStack(Material.DIAMOND, 2),
                        false,
                        Map.of(0, new ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(drag);
        assertTrue(drag.isCancelled());
        drain();
        assertEquals(0, store.state("event").join().price());
        assertEquals(Material.GOLD_INGOT, view.getTopInventory().getItem(0).getType());
    }

    private void leftClick(int slot) {
        server.getPluginManager()
                .callEvent(
                        new InventoryClickEvent(
                                player.getOpenInventory(),
                                InventoryType.SlotType.CONTAINER,
                                slot,
                                ClickType.LEFT,
                                InventoryAction.PICKUP_ALL));
    }

    private void runClickBeforeResultCallback() {
        server.getScheduler().performOneTick();
        db.submit(c -> null).join();
    }

    @Test
    void registrationPreviewClickCannotBecomeAnOfferWhenSaleStarts() throws Exception {
        module.onCommand(player, null, "enchere", new String[] {"rejoindre"});
        drain();
        assertTrue(
                player.getOpenInventory().getTopInventory() == null
                        || !(player.getOpenInventory().getTopInventory().getHolder() != null
                                && player.getOpenInventory()
                                        .getTopInventory()
                                        .getHolder()
                                        .getClass()
                                        .getName()
                                        .contains("LiveAuctions$View")));
        set("state", store.advance("event", 30000, true).join().state());
        runClickBeforeResultCallback();
        assertEquals(0, store.state("event").join().price());
    }

    @Test
    void queuedPriceClickCannotUseTheNextPagesHigherAmount() throws Exception {
        new EconomyService(db, Map.of())
                .set(player.getUniqueId(), player.getName(), CurrencyType.ECUS, 1_000_000_000)
                .join();
        store.join("event", player.getUniqueId()).join();
        set("state", store.advance("event", 30000, true).join().state());
        module.onCommand(player, null, "enchere", new String[] {"rejoindre"});
        drain();
        leftClick(0);
        var holder = player.getOpenInventory().getTopInventory().getHolder();
        var page = holder.getClass().getDeclaredField("page");
        page.setAccessible(true);
        page.setInt(holder, 1);
        runClickBeforeResultCallback();
        assertEquals(0, store.state("event").join().price());
    }

    @Test
    void queuedOfferForPreviousLotCannotBePlacedOnTheFollowingLot() throws Exception {
        store.join("event", player.getUniqueId()).join();
        set("state", store.advance("event", 30000, true).join().state());
        module.onCommand(player, null, "enchere", new String[] {"rejoindre"});
        drain();
        leftClick(0);
        db.submit(
                        c -> {
                            try (var stmt = c.createStatement()) {
                                stmt.executeUpdate(
                                        "UPDATE live_auctions SET deadline=0 WHERE id='event'");
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        set("state", store.advance("event", 30000, false).join().state());
        runClickBeforeResultCallback();
        assertEquals(1, store.state("event").join().lot());
        assertEquals(0, store.state("event").join().price());
    }

    @Test
    void oldContainerClickCannotBuyTheNewPagesPrice() throws Exception {
        new EconomyService(db, Map.of())
                .set(player.getUniqueId(), player.getName(), CurrencyType.ECUS, 1_000_000_000)
                .join();
        store.join("event", player.getUniqueId()).join();
        set("state", store.advance("event", 30000, true).join().state());
        module.onCommand(player, null, "enchere", new String[] {"rejoindre"});
        drain();
        var oldView = player.getOpenInventory();
        leftClick(53);
        drain();
        assertNotSame(oldView.getTopInventory(), player.getOpenInventory().getTopInventory());
        var delayed =
                new InventoryClickEvent(
                        oldView,
                        InventoryType.SlotType.CONTAINER,
                        0,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(delayed);
        runClickBeforeResultCallback();
        assertEquals(0, store.state("event").join().price());
    }
}
