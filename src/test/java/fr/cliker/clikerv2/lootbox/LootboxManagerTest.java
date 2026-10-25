package fr.cliker.clikerv2.lootbox;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.hunting.RewardLedger;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class LootboxManagerTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private RewardLedger ledger;
    private ClaimService claims;
    private LootboxManager boxes;
    private EconomyService economy;
    private org.mockbukkit.mockbukkit.entity.PlayerMock player;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "boxes-" + UUID.randomUUID() + ".db");
        db.initialize();
        ledger = new RewardLedger(plugin, db);
        claims = ClaimTestFactory.create(plugin, db);
        economy = new EconomyService(db, Map.of());
        LootboxItem.init(plugin);
        LootboxTier.load(plugin);
        boxes = new LootboxManager(plugin, ledger, claims);
        player = server.addPlayer();
    }

    @AfterEach
    void cleanup() {
        try {
            claims.shutdown();
            ledger.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void open() {
        boxes.onInteract(
                new PlayerInteractEvent(
                        player,
                        Action.RIGHT_CLICK_AIR,
                        player.getInventory().getItemInMainHand(),
                        null,
                        org.bukkit.block.BlockFace.SELF,
                        EquipmentSlot.HAND));
    }

    private void drain() {
        db.submit(c -> null).join();
        server.getScheduler().performTicks(2);
    }

    @Test
    void openingStackIsOneTransactionAndOneNotification() {
        ItemStack stack = LootboxItem.create(LootboxTier.COMMUN);
        stack.setAmount(64);
        player.getInventory().setItemInMainHand(stack);
        player.setSneaking(true);
        open();
        drain();
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());
        assertEquals(
                64 * LootboxTier.COMMUN.ecus(),
                economy.getBalances(player.getUniqueId()).join().ecus());
        long receipts =
                db.submit(
                                c -> {
                                    try (var s = c.createStatement();
                                            var r =
                                                    s.executeQuery(
                                                            "SELECT COUNT(*) FROM reward_ledger")) {
                                        r.next();
                                        return r.getLong(1);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .join();
        assertEquals(1, receipts);
        assertNotNull(player.nextMessage());
        assertNull(player.nextMessage());
    }

    @Test
    void failedCurrencyCreditRollsBackAllGainsAndRestoresBox() {
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.CENDRES, Long.MAX_VALUE)
                .join();
        player.getInventory().setItemInMainHand(LootboxItem.create(LootboxTier.COMMUN));
        open();
        drain();
        assertEquals(0, economy.getBalances(player.getUniqueId()).join().ecus());
        assertEquals(0, economy.getBalances(player.getUniqueId()).join().gemmesSombres());
        assertTrue(
                LootboxItem.tierOf(player.getInventory().getItemInMainHand()) == LootboxTier.COMMUN
                        || claims.store().page(player.getUniqueId(), 0).join().total() == 1);
    }

    @Test
    void aPendingClaimCannotBeConsumedAsALootbox() {
        player.getInventory().setItemInMainHand(LootboxItem.create(LootboxTier.COMMUN));
        var entry =
                claims.store()
                        .enqueue(
                                player.getUniqueId(),
                                "test",
                                ClaimService.serialize(new ItemStack(Material.DIAMOND)))
                        .join();
        claims.deliver(player, entry.id(), false);
        assertTrue(claims.isDelivering(player.getUniqueId()));
        open();
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(0, economy.getBalances(player.getUniqueId()).join().ecus());
    }
}
