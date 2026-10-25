package fr.cliker.clikerv2.features;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.gear.GearGuide;

import org.bukkit.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class FeatureMenusTest {
    ServerMock server;
    org.bukkit.plugin.java.JavaPlugin plugin;
    org.mockbukkit.mockbukkit.entity.PlayerMock player;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void guidesReturnToTheirOwnTableAndExposeActualEnchantmentLevelWeights() {
        var ench = new AtomicInteger();
        var reforges = new AtomicInteger();
        var runes = new AtomicInteger();
        var guide =
                new GearGuide(
                        plugin,
                        "Enchantements",
                        p -> ench.incrementAndGet(),
                        List.of(40.0, 30.0, 20.0, 9.9, .1));
        new GearGuide(plugin, "Reforges", p -> reforges.incrementAndGet(), List.of());
        new GearGuide(plugin, "Runes", p -> runes.incrementAndGet(), List.of());
        guide.open(player);
        var inv = player.getOpenInventory().getTopInventory();
        assertTrue(inv.getItem(0).getItemMeta().lore().toString().contains("0.1"));
        var click =
                new InventoryClickEvent(
                        player.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        49,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        server.getScheduler().performOneTick();
        assertTrue(click.isCancelled());
        assertEquals(1, ench.get());
        assertEquals(0, reforges.get());
        assertEquals(0, runes.get());
    }

    @Test
    void potionPersistsMultiplierStacksEqualDurationAndMilkClearsCustomBoost() {
        var boosts = new Boosts(plugin);
        try {
            var potion = boosts.potion("coins", 2, 60);
            potion.setAmount(2);
            player.getInventory().setItemInMainHand(potion);
            assertEquals(64, potion.getItemMeta().getMaxStackSize());
            var event =
                    new PlayerInteractEvent(
                            player, Action.RIGHT_CLICK_AIR, potion, null, null, EquipmentSlot.HAND);
            boosts.use(event);
            assertTrue(event.isCancelled());
            assertEquals(2, Boosts.multiplier(player, "coins"));
            assertEquals(1, potion.getAmount());
            long expiry =
                    player.getPersistentDataContainer()
                            .get(
                                    new NamespacedKey(plugin, "boost_coins_expiry"),
                                    org.bukkit.persistence.PersistentDataType.LONG);
            boosts.use(
                    new PlayerInteractEvent(
                            player,
                            Action.RIGHT_CLICK_AIR,
                            potion,
                            null,
                            null,
                            EquipmentSlot.HAND));
            long later =
                    player.getPersistentDataContainer()
                            .get(
                                    new NamespacedKey(plugin, "boost_coins_expiry"),
                                    org.bukkit.persistence.PersistentDataType.LONG);
            assertEquals(60000, later - expiry);
            boosts.milk(
                    new PlayerItemConsumeEvent(
                            player, new ItemStack(Material.MILK_BUCKET), EquipmentSlot.HAND));
            assertEquals(1, Boosts.multiplier(player, "coins"));
        } finally {
            boosts.shutdown();
        }
    }

    @Test
    void provisionalTierFortyMigratesOnceToCurrentLastZone() {
        var settings = new Settings(plugin, "progression/talismans.yml");
        settings.yaml.set("schema-version", null);
        settings.yaml.set("tiers.5", 40);
        Talismans.migrateTierDefaults(settings);
        assertEquals(fr.cliker.clikerv2.MobTier.values().length, settings.yaml.getInt("tiers.5"));
        assertEquals(2, settings.yaml.getInt("schema-version"));
        var restored = new Settings(plugin, "progression/talismans.yml");
        assertEquals(19, restored.yaml.getInt("tiers.5"));
        restored.yaml.set("tiers.5", 40);
        Talismans.migrateTierDefaults(restored);
        assertEquals(40, restored.yaml.getInt("tiers.5"), "A later explicit edit stays intact");
    }

    @Test
    void tierMigrationPreservesOtherCustomUnlockZones() {
        var settings = new Settings(plugin, "progression/talismans.yml");
        settings.yaml.set("schema-version", null);
        settings.yaml.set("tiers.4", 12);
        settings.yaml.set("tiers.5", 17);
        Talismans.migrateTierDefaults(settings);
        assertEquals(12, settings.yaml.getInt("tiers.4"));
        assertEquals(17, settings.yaml.getInt("tiers.5"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void warpOriginsIgnoreTemporaryEggSpawns() throws Exception {
        for (var name : List.of("boss.yml", "damage-popups.yml")) {
            var file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, name);
            java.nio.file.Files.writeString(file.toPath(), "{}");
        }
        var mobs =
                new fr.cliker.clikerv2.mobs.CustomMobManager(
                        plugin, null, null, null, null, null, null, null);
        try {
            var locationsField = mobs.getClass().getDeclaredField("spawnLocation");
            var mobIdsField = mobs.getClass().getDeclaredField("spawnMobId");
            var temporaryField = mobs.getClass().getDeclaredField("oneShotSpawns");
            locationsField.setAccessible(true);
            mobIdsField.setAccessible(true);
            temporaryField.setAccessible(true);
            var locations = (Map<String, Location>) locationsField.get(mobs);
            var mobIds = (Map<String, String>) mobIdsField.get(mobs);
            var temporary = (Set<String>) temporaryField.get(mobs);
            var world = server.addSimpleWorld("warp-origins");
            locations.put("a-egg", new Location(world, 500, 65, 0));
            mobIds.put("a-egg", "vache");
            temporary.add("a-egg");
            assertTrue(mobs.travelPoint(fr.cliker.clikerv2.MobTier.VACHE, false).isEmpty());
            locations.put("z-permanent", new Location(world, 100, 65, 0));
            mobIds.put("z-permanent", "vache");
            var selected = mobs.travelPoint(fr.cliker.clikerv2.MobTier.VACHE, false).orElseThrow();
            assertEquals(100, selected.getX());
            world.getChunkAt(6, 0).load();
            var cow =
                    world.spawnEntity(
                            new Location(world, 100, 65, 0), org.bukkit.entity.EntityType.COW);
            cow.getPersistentDataContainer()
                    .set(
                            new NamespacedKey(plugin, "cliker_spawn_id"),
                            org.bukkit.persistence.PersistentDataType.STRING,
                            "z-permanent");
            var recover = mobs.getClass().getDeclaredMethod("recoverMissingMobs");
            recover.setAccessible(true);
            recover.invoke(mobs);
            var aliveField = mobs.getClass().getDeclaredField("aliveBySpawnId");
            aliveField.setAccessible(true);
            var alive = (Map<String, UUID>) aliveField.get(mobs);
            assertEquals(
                    cow.getUniqueId(),
                    alive.get("z-permanent"),
                    "Existing entity is adopted, not duplicated");
            assertFalse(alive.containsKey("a-egg"), "Temporary eggs are not restored");
            selected.setX(999);
            assertEquals(100, locations.get("z-permanent").getX(), "Returned location is a copy");
        } finally {
            mobs.shutdown();
        }
    }

    @Test
    void configurationMigrationPreservesValuesAndCreatesBackup() throws Exception {
        var old = new java.io.File(plugin.getDataFolder(), "daily.yml");
        old.getParentFile().mkdirs();
        java.nio.file.Files.writeString(old.toPath(), "personnalise: 123\n");
        var migrated = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "daily.yml");
        assertEquals("recompenses", migrated.getParentFile().getName());
        assertEquals(
                123, fr.cliker.clikerv2.config.YamlFiles.load(migrated).getInt("personnalise"));
        assertFalse(old.exists());
        try (var files =
                java.nio.file.Files.list(
                        plugin.getDataFolder().toPath().resolve("migration-configs"))) {
            assertEquals(1, files.count());
        }
    }
}
