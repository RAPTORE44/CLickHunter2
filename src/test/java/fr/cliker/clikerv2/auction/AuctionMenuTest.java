package fr.cliker.clikerv2.auction;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.*;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;

import java.util.*;

class AuctionMenuTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private AuctionStore store;
    private AuctionManager manager;
    private AuctionMenu menu;
    private ClaimService claims;
    private PlayerMock seller, buyer;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        ConfigurationSerialization.registerClass(ItemStackMock.class);
        db = new DatabaseManager(plugin, "auction-ui-" + UUID.randomUUID() + ".db");
        db.initialize();
        store = new AuctionStore(db);
        claims = ClaimTestFactory.create(plugin, db);
        Bukkit.getServicesManager()
                .register(ClaimService.class, claims, plugin, ServicePriority.Normal);
        manager = new AuctionManager(plugin, store, new EconomyService(db, Map.of()));
        menu = new AuctionMenu(plugin, manager);
        server.getPluginManager().registerEvents(menu, plugin);
        seller = server.addPlayer();
        buyer = server.addPlayer();
        seller.addAttachment(plugin, "cliker.ah.use", true);
        buyer.addAttachment(plugin, "cliker.ah.use", true);
    }

    @AfterEach
    void cleanup() {
        try {
            claims.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void searchIgnoresAccentsAndFiltersUseRealItemCategories() {
        var sword = new ItemStack(Material.DIAMOND_SWORD);
        var meta = sword.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Épée Étoilée"));
        sword.setItemMeta(meta);
        var listing =
                new AuctionListing(
                        "search",
                        seller.getUniqueId(),
                        seller.getName(),
                        AuctionManager.serialize(sword),
                        100,
                        1);
        assertTrue(AuctionMenu.matches(listing, "epee etoilee", "epees"));
        assertFalse(AuctionMenu.matches(listing, "etoilee", "armures"));
        assertFalse(AuctionMenu.matches(listing, "casque", "tous"));
    }

    @Test
    void sortingUsesPricesDatesNamesAndActualRarityWithoutRenamedItemSpoofing() {
        var a = new ItemStack(Material.STONE);
        var meta = a.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Mythique A"));
        a.setItemMeta(meta);
        var b = new ItemStack(Material.DIAMOND);
        var x =
                new AuctionListing(
                        "x",
                        seller.getUniqueId(),
                        seller.getName(),
                        AuctionManager.serialize(a),
                        100,
                        1);
        var y =
                new AuctionListing(
                        "y",
                        seller.getUniqueId(),
                        seller.getName(),
                        AuctionManager.serialize(b),
                        10,
                        2);
        assertTrue(AuctionMenu.comparator(0).compare(x, y) > 0);
        assertTrue(AuctionMenu.comparator(1).compare(x, y) < 0);
        assertTrue(AuctionMenu.comparator(2).compare(x, y) > 0);
        assertTrue(AuctionMenu.comparator(3).compare(x, y) < 0);
        assertTrue(AuctionMenu.comparator(4).compare(x, y) > 0);
        assertTrue(AuctionMenu.comparator(5).compare(x, y) < 0);
        assertEquals(0, AuctionMenu.rarity(x), "A renamed item cannot fake a custom rarity");
        meta = b.getItemMeta();
        meta.getPersistentDataContainer()
                .set(
                        new NamespacedKey(plugin, "cliker_essence_rarity"),
                        org.bukkit.persistence.PersistentDataType.STRING,
                        "RARE");
        b.setItemMeta(meta);
        y =
                new AuctionListing(
                        "y",
                        seller.getUniqueId(),
                        seller.getName(),
                        AuctionManager.serialize(b),
                        10,
                        2);
        assertTrue(AuctionMenu.comparator(6).compare(x, y) > 0);
        assertTrue(AuctionMenu.comparator(7).compare(x, y) < 0);
    }

    private void listing(String id, Material item, long offset) {
        store.save(
                        new AuctionListing(
                                id,
                                seller.getUniqueId(),
                                seller.getName(),
                                AuctionManager.serialize(new ItemStack(item)),
                                100 + offset,
                                System.currentTimeMillis() + offset))
                .join();
    }

    private void drain() {
        db.submit(c -> null).join();
        server.getScheduler().performTicks(2);
    }

    private void load() {
        manager.load();
        drain();
    }

    private void click(PlayerMock p, int slot) {
        var e =
                new InventoryClickEvent(
                        p.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        slot,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(e);
        assertTrue(e.isCancelled());
        server.getScheduler().performOneTick();
    }

    @Test
    void disappearanceOfEarlierListingDoesNotChangeClickedPurchase() {
        listing("a", Material.DIAMOND, 0);
        listing("b", Material.EMERALD, 1);
        listing("c", Material.GOLD_INGOT, 2);
        load();
        menu.openBrowse(buyer);
        assertEquals(
                Material.EMERALD, buyer.getOpenInventory().getTopInventory().getItem(1).getType());
        assertTrue(manager.cancel(seller, "a", false));
        drain();
        click(buyer, 1);
        assertEquals(
                Material.EMERALD, buyer.getOpenInventory().getTopInventory().getItem(13).getType());
    }

    @Test
    void disappearanceOfEarlierOwnListingCannotCancelAnotherObject() {
        listing("a", Material.DIAMOND, 0);
        listing("b", Material.EMERALD, 1);
        listing("c", Material.GOLD_INGOT, 2);
        load();
        menu.openMyListings(seller);
        assertTrue(manager.cancel(seller, "a", false));
        drain();
        click(seller, 1);
        drain();
        assertNull(manager.get("b"));
        assertNotNull(manager.get("c"));
    }

    @Test
    void allListingsRemainReachableBeyondFirstFortyFive() {
        for (int i = 0; i < 46; i++)
            listing("id" + i, i == 45 ? Material.NETHER_STAR : Material.PAPER, i);
        load();
        menu.openBrowse(buyer);
        assertEquals(
                Material.ARROW, buyer.getOpenInventory().getTopInventory().getItem(53).getType());
        click(buyer, 53);
        assertEquals(
                Material.PAPER, buyer.getOpenInventory().getTopInventory().getItem(0).getType());
        click(buyer, 46);
        assertEquals(
                Material.NETHER_STAR,
                buyer.getOpenInventory().getTopInventory().getItem(0).getType());
    }

    @Test
    void maximumPriceTaxUsesExactIntegerArithmetic() {
        assertEquals(92233720368547758L, AuctionListing.tax(Long.MAX_VALUE));
        assertEquals(3, AuctionListing.tax(250));
        assertEquals(2, AuctionListing.tax(249));
    }
}
