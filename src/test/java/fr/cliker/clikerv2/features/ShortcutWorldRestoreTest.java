package fr.cliker.clikerv2.features;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.world.*;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class ShortcutWorldRestoreTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private TravelAndTools travel;

    @BeforeEach
    void before() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("spawn");
        var constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        for (String name :
                List.of(
                        "warp",
                        "warpbloc",
                        "sacbloc",
                        "upgradebloc",
                        "bestiairebloc",
                        "batonspawn"))
            server.getCommandMap()
                    .register(
                            plugin.getName().toLowerCase(Locale.ROOT),
                            constructor.newInstance(name, plugin));
        var settings = new Settings(plugin, "navigation/warps.yml");
        for (String section : List.of("raccourcis-warp", "sacs", "upgrades", "bestiaires")) {
            settings.yaml.set(section + ".saved.world", "late_world");
            settings.yaml.set(section + ".saved.x", 4);
            settings.yaml.set(section + ".saved.y", 64);
            settings.yaml.set(section + ".saved.z", 8);
        }
        settings.save();
        travel = new TravelAndTools(plugin, new Menus(plugin), null, null);
    }

    @AfterEach
    void after() {
        try {
            if (travel != null) travel.shutdown();
        } finally {
            MockBukkit.unmock();
        }
    }

    private int indexed(String name) throws Exception {
        var field = TravelAndTools.class.getDeclaredField(name);
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(travel)).size();
    }

    @Test
    void shortcutsAreIndexedWhenTheirWorldLoadsAfterThePlugin() throws Exception {
        assertEquals(0, indexed("warpBlocks"));
        var world = server.addSimpleWorld("late_world");
        server.getPluginManager().callEvent(new WorldLoadEvent(world));
        for (String field : List.of("warpBlocks", "bagBlocks", "upgradeBlocks", "bestiaryBlocks"))
            assertEquals(1, indexed(field));
        assertTrue(travel.diagnostics().contains("raccourcis-warp : 1 actifs / 1 sauvegardés"));
        assertEquals(
                "late_world",
                new Settings(plugin, "navigation/warps.yml")
                        .yaml.getString("raccourcis-warp.saved.world"));
    }

    @Test
    void unloadRemovesActiveLinksWithoutErasingTheirSavedPositions() throws Exception {
        var world = server.addSimpleWorld("late_world");
        server.getPluginManager().callEvent(new WorldLoadEvent(world));
        assertEquals(1, indexed("warpBlocks"));
        server.getPluginManager().callEvent(new WorldUnloadEvent(world));
        assertEquals(0, indexed("warpBlocks"));
        assertTrue(
                new Settings(plugin, "navigation/warps.yml")
                        .yaml.contains("raccourcis-warp.saved"));
        server.getPluginManager().callEvent(new WorldLoadEvent(world));
        assertEquals(1, indexed("warpBlocks"));
    }

    @Test
    void moduleDiagnosticsDetectsAConflictingWarpCommand() throws Exception {
        var other = MockBukkit.createMockPlugin("OtherWarp");
        server.getCommandMap().getCommand("warp").unregister(server.getCommandMap());
        server.getCommandMap().clearCommands();
        var constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        server.getCommandMap().register("otherwarp", constructor.newInstance("warp", other));
        var lines = AdminDiagnostic.runtimeLines(plugin);
        assertTrue(lines.contains("/warp : OtherWarp"));
        assertTrue(
                lines.contains(
                        "Commande ClickHunter directe : /"
                                + plugin.getName().toLowerCase(Locale.ROOT)
                                + ":warp"));
    }
}
