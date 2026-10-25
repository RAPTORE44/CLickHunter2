package fr.cliker.clikerv2.hunting;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.bag.*;
import fr.cliker.clikerv2.essence.CompressorManager;
import fr.cliker.clikerv2.mobs.*;
import fr.cliker.clikerv2.prestige.*;
import fr.cliker.clikerv2.upgrade.*;

import org.bukkit.*;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.nio.file.*;
import java.util.*;

class HuntingMenuTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private RewardLedger ledger;
    private HuntingStore store;
    private CustomMobManager mobs;
    private HuntingModule module;
    private org.mockbukkit.mockbukkit.entity.PlayerMock player;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
        Files.createDirectories(plugin.getDataFolder().toPath());
        for (String resource : List.of("chasse.yml", "boss.yml", "damage-popups.yml"))
            try (var in = getClass().getResourceAsStream("/" + resource)) {
                Files.copy(
                        in,
                        plugin.getDataFolder().toPath().resolve(resource),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        db = new DatabaseManager(plugin, "menu-" + UUID.randomUUID() + ".db");
        db.initialize();
        ledger = new RewardLedger(plugin, db);
        store = new HuntingStore(ledger);
        var economy = new EconomyService(db, Map.of());
        mobs =
                new CustomMobManager(
                        plugin,
                        new MobSpawnStore(db),
                        economy,
                        new UpgradeManager(plugin, new UpgradeStore(db), economy),
                        new MobKillStore(db),
                        new PrestigeManager(plugin, new PrestigeStore(db), new MobKillStore(db)),
                        new CompressorManager(),
                        new BagManager(plugin, new BagStore(db), p -> {}));
        mobs.setHunting(store, ledger);
        module = new HuntingModule(plugin, store, ledger, mobs);
        new HuntUI(plugin);
    }

    @AfterEach
    void cleanup() {
        try {
            module.shutdown();
            mobs.shutdown();
            ledger.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void drain() throws Exception {
        db.submit(c -> null).join();
        Thread.sleep(10);
        server.getScheduler().performOneTick();
    }

    private String allText() {
        StringBuilder s = new StringBuilder();
        for (var item : player.getOpenInventory().getTopInventory().getContents())
            if (item != null && item.hasItemMeta()) {
                s.append(item.getItemMeta().displayName());
                s.append(item.getItemMeta().lore());
            }
        return s.toString();
    }

    @Test
    void onlyNormalKillsRevealNamesAndBossCountRemainsAccurate() throws Exception {
        store.kill(
                        "boss",
                        player.getUniqueId(),
                        player.getName(),
                        "cochon",
                        true,
                        Gains.of(0, 0, 0, 0),
                        List.of())
                .join();
        module.openBestiary(player);
        drain();
        assertFalse(allText().contains("Cochon"));
        assertFalse(allText().contains("Warden"));
        store.kill(
                        "vache",
                        player.getUniqueId(),
                        player.getName(),
                        "vache",
                        false,
                        Gains.of(10, 1, 0, 1),
                        List.of())
                .join();
        module.openBestiary(player);
        drain();
        assertTrue(allText().contains("Vache"));
        assertFalse(allText().contains("Cochon"));
        assertEquals(
                Material.GRAY_DYE,
                player.getOpenInventory().getTopInventory().getItem(11).getType());
    }

    @Test
    void unknownContractsDoNotLeakMobNamesOrRewardsAndDragIsBlocked() throws Exception {
        player.addAttachment(plugin, "cliker.contrats", true);
        module.openContracts(player);
        drain();
        assertFalse(allText().contains("Vache"));
        assertFalse(allText().contains("Cochon"));
        var e =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        0,
                        ClickType.SHIFT_LEFT,
                        InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(e);
        assertTrue(e.isCancelled());
        var drag =
                new InventoryDragEvent(
                        player.getOpenInventory(),
                        new org.bukkit.inventory.ItemStack(Material.DIAMOND),
                        new org.bukkit.inventory.ItemStack(Material.DIAMOND, 2),
                        false,
                        Map.of(0, new org.bukkit.inventory.ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(drag);
        assertTrue(drag.isCancelled());
    }
}
