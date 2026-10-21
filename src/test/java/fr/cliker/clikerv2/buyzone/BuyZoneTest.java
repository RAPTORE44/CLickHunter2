package fr.cliker.clikerv2.buyzone;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockbukkit.mockbukkit.world.Coordinate;
import org.mockbukkit.mockbukkit.block.BlockMock;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class BuyZoneTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private WorldMock world;
    private PlayerMock admin;
    private PlayerMock player;
    private DatabaseManager database;
    private EconomyService economy;
    private BuyZoneModule module;
    private Location center;
    private String databaseFile;
    private static final Command COMMAND = new Command("buyzone") {
        @Override public boolean execute(CommandSender sender, String label, String[] args) { return false; }
    };
    @BeforeEach void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        // MockBukkit 4.36 n'implémente pas isPassable : simulation limitée au sol plat AIR/STONE des tests.
        world = new WorldMock(new WorldCreator("buyzone-world")) {
            private final Map<Coordinate, BlockMock> simulatedBlocks = new HashMap<>();
            @Override public BlockMock createBlock(Coordinate coordinate) {
                return simulatedBlocks.computeIfAbsent(coordinate, key -> new BlockMock(Material.AIR, new Location(this, key.x, key.y, key.z)) {
                    @Override public boolean isPassable() { return getType().isAir(); }
                    @Override public boolean isLiquid() { return getType() == Material.WATER || getType() == Material.LAVA; }
                });
            }
        };
        server.addWorld(world);
        center = new Location(world, 0.5, 65, 0.5);
        // Un sol plat, y compris pour le retour à Z=-4.5.
        for (int x = -22; x <= 22; x++) for (int z = -10; z <= 10; z++) world.getBlockAt(x, 64, z).setType(Material.STONE);
        admin = server.addPlayer();
        admin.addAttachment(plugin, BuyZoneModule.ADMIN, true);
        admin.addAttachment(plugin, BuyZoneModule.BYPASS, true);
        admin.teleport(center);
        player = server.addPlayer();
        player.addAttachment(plugin, BuyZoneModule.BYPASS, false);
        player.teleport(center.clone().add(0, 0, -2));
        Files.createDirectories(plugin.getDataFolder().toPath());
        try (var input = getClass().getResourceAsStream("/buyzone.yml")) {
            Files.copy(input, plugin.getDataFolder().toPath().resolve("buyzone.yml"), StandardCopyOption.REPLACE_EXISTING);
        }
        var file = plugin.getDataFolder().toPath().resolve("buyzone.yml").toFile();
        var config = YamlConfiguration.loadConfiguration(file);
        config.set("zones.cochon.prix", 100);
        config.save(file);
        databaseFile = "test-" + UUID.randomUUID() + ".db";
        database = new DatabaseManager(plugin, databaseFile);
        database.initialize();
        economy = new EconomyService(database, Map.of(CurrencyType.ECUS, 0L));
        module = new BuyZoneModule(plugin, economy, database);
        server.getPluginManager().registerEvents(module, plugin);
        var load = BuyZoneModule.class.getDeclaredMethod("load", Player.class);
        load.setAccessible(true);
        load.invoke(module, player);
        database.submit(c -> null).join();
        server.getScheduler().performTicks(2);
    }
    @AfterEach void cleanup() {
        try {
            if (module != null) module.shutdown();
            if (database != null) database.close();
        } finally { MockBukkit.unmock(); }
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10); server.getScheduler().performOneTick(); }
        assertTrue(condition.getAsBoolean());
    }
    private void spawn() {
        module.onCommand(admin, COMMAND, "buyzone", new String[]{"spawn", "cochon"});
        assertTrue(YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("buyzone.yml").toFile()).contains("portails.cochon"), () -> admin.nextMessage());
    }
    private void enter() throws Exception {
        var event = new PlayerMoveEvent(player, player.getLocation(), center.clone().add(0, 0, 2));
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        await(() -> player.getOpenInventory().getTopInventory() != null && player.getOpenInventory().getTopInventory().getType() == InventoryType.DISPENSER);
    }
    private InventoryClickEvent click(int slot) {
        var event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }
    @Test void geometryAndNorthReturnMatchFortyWideTenHighPortal() {
        var zone = new BuyZone("cochon", "Cochon", "cliker.zone.cochon", CurrencyType.ECUS, 100, center, 20, 10, 1);
        assertTrue(zone.contains(center));
        assertTrue(zone.contains(center.clone().add(20, 9.9, 0)));
        assertFalse(zone.contains(center.clone().add(20.1, 0, 0)));
        assertFalse(zone.contains(center.clone().add(0, 10, 0)));
        assertFalse(zone.contains(center.clone().add(0, 0, 0.6)));
        assertTrue(zone.crosses(center.clone().add(0, 0, -10), center.clone().add(0, 0, 10)));
        assertTrue(zone.crosses(center.clone().add(20, 0, -10), center.clone().add(20, 0, 10)));
        assertFalse(zone.crosses(center.clone().add(21, 0, -10), center.clone().add(21, 0, 10)));
        assertEquals(center.getX(), zone.northReturn().getX());
        assertEquals(center.getZ() - 5, zone.northReturn().getZ());
        assertFalse(zone.contains(zone.northReturn()));
    }
    @Test void dispenserHasBlackGlassAndNamedPricedStarAndCloseRetreats() throws Exception {
        spawn(); enter();
        var top = player.getOpenInventory().getTopInventory();
        assertEquals(9, top.getSize());
        for (int slot = 0; slot < 9; slot++) {
            assertEquals(slot == 4 ? Material.NETHER_STAR : Material.BLACK_STAINED_GLASS_PANE, top.getItem(slot).getType());
            if (slot != 4) assertEquals(" ", PlainTextComponentSerializer.plainText().serialize(top.getItem(slot).getItemMeta().displayName()));
        }
        String lore = top.getItem(4).getItemMeta().lore().toString();
        assertTrue(lore.contains("100")); assertTrue(lore.contains("Cochon"));
        assertTrue(click(0).isCancelled());
        var drag = new InventoryDragEvent(player.getOpenInventory(), new ItemStack(Material.DIAMOND), new ItemStack(Material.DIAMOND, 2), false, Map.of(4, new ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(drag); assertTrue(drag.isCancelled());
        player.closeInventory(); server.getScheduler().performOneTick();
        assertEquals(center.getX(), player.getLocation().getX());
        assertEquals(center.getZ() - 5, player.getLocation().getZ());
        assertFalse(player.hasPermission("cliker.zone.cochon"));
    }
    @Test void insufficientMoneyClosesWithoutUnlockOrDebit() throws Exception {
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.ECUS, 50).join();
        spawn(); enter(); click(4);
        database.submit(c -> null).join(); server.getScheduler().performTicks(2);
        var top = player.getOpenInventory().getTopInventory();
        assertTrue(top == null || top.getType() != InventoryType.DISPENSER);
        assertEquals(50L, economy.getBalance(player.getUniqueId(), player.getName(), CurrencyType.ECUS).join());
        assertTrue(new BuyZoneStore(database).load(player.getUniqueId()).join().isEmpty());
        assertFalse(player.hasPermission("cliker.zone.cochon"));
        assertEquals(center.getZ() - 5, player.getLocation().getZ());
    }
    @Test void doubleClickChargesOnceAndPermissionAllowsCrossing() throws Exception {
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.ECUS, 250).join();
        spawn(); enter();
        for (int i = 0; i < 12; i++) click(4);
        await(() -> player.hasPermission("cliker.zone.cochon"));
        assertEquals(150L, economy.getBalance(player.getUniqueId(), player.getName(), CurrencyType.ECUS).join());
        assertEquals(Set.of("cochon"), new BuyZoneStore(database).load(player.getUniqueId()).join());
        var move = new PlayerMoveEvent(player, center.clone().add(0, 0, -2), center.clone().add(0, 0, 2));
        server.getPluginManager().callEvent(move);
        assertFalse(move.isCancelled());
    }
    @Test void atomicRepeatedPurchasesAndDifferentPlayersRemainIndependent() {
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.ECUS, 500).join();
        List<CompletableFuture<EconomyService.ZonePurchase>> requests = new ArrayList<>();
        for (int i = 0; i < 20; i++) requests.add(economy.purchaseZone(player.getUniqueId(), player.getName(), "cochon", "cliker.zone.cochon", CurrencyType.ECUS, 100));
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();
        assertEquals(1, requests.stream().map(CompletableFuture::join).filter(r -> r == EconomyService.ZonePurchase.PURCHASED).count());
        assertEquals(400L, economy.getBalance(player.getUniqueId(), player.getName(), CurrencyType.ECUS).join());
        assertTrue(new BuyZoneStore(database).load(UUID.randomUUID()).join().isEmpty());
    }
    @Test void unknownPricesAreNotInventedAndAdminPermissionIsRequired() {
        module.onCommand(admin, COMMAND, "buyzone", new String[]{"spawn", "vache"});
        var saved = YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("buyzone.yml").toFile());
        assertEquals(-1L, saved.getLong("zones.vache.prix"));
        assertFalse(saved.contains("portails.vache"));
        module.onCommand(player, COMMAND, "buyzone", new String[]{"spawn", "cochon"});
        assertFalse(YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("buyzone.yml").toFile()).contains("portails.cochon"));
    }
    @Test void restartRestoresPortalAndPurchasedPermissionWithoutChargingAgain() throws Exception {
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.ECUS, 250).join();
        spawn(); enter(); click(4);
        await(() -> player.hasPermission("cliker.zone.cochon"));
        module.shutdown();
        assertFalse(player.hasPermission("cliker.zone.cochon"));
        database.close();
        database = new DatabaseManager(plugin, databaseFile);
        database.initialize();
        economy = new EconomyService(database, Map.of(CurrencyType.ECUS, 0L));
        module = new BuyZoneModule(plugin, economy, database);
        server.getPluginManager().registerEvents(module, plugin);
        var load = BuyZoneModule.class.getDeclaredMethod("load", Player.class);
        load.setAccessible(true); load.invoke(module, player);
        await(() -> player.hasPermission("cliker.zone.cochon"));
        assertEquals(150L, economy.getBalance(player.getUniqueId(), player.getName(), CurrencyType.ECUS).join());
        var move = new PlayerMoveEvent(player, center.clone().add(0, 0, -2), center.clone().add(0, 0, 2));
        server.getPluginManager().callEvent(move);
        assertFalse(move.isCancelled());
        module.onCommand(admin, COMMAND, "buyzone", new String[]{"remove", "cochon"});
        assertFalse(YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("buyzone.yml").toFile()).contains("portails.cochon"));
        assertEquals(Set.of("cochon"), new BuyZoneStore(database).load(player.getUniqueId()).join());
    }
    @Test void failedUnlockInsertRollsBackMoneyDebit() {
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.ECUS, 250).join();
        database.submit(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER reject_unlock BEFORE INSERT ON buyzone_unlocks BEGIN SELECT RAISE(ABORT, 'test failure'); END");
            } catch (java.sql.SQLException e) { throw new RuntimeException(e); }
            return null;
        }).join();
        assertThrows(java.util.concurrent.CompletionException.class, () -> economy.purchaseZone(player.getUniqueId(), player.getName(), "cochon", "cliker.zone.cochon", CurrencyType.ECUS, 100).join());
        assertEquals(250L, economy.getBalance(player.getUniqueId(), player.getName(), CurrencyType.ECUS).join());
        assertTrue(new BuyZoneStore(database).load(player.getUniqueId()).join().isEmpty());
    }
}
