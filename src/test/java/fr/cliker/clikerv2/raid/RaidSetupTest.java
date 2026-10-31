package fr.cliker.clikerv2.raid;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.features.Settings;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class RaidSetupTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private RaidSetup setup;
    private PlayerMock admin;
    private World lobby, arena;

    @BeforeEach
    void before() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        lobby = server.addSimpleWorld("lobby");
        arena = server.addSimpleWorld("clickhunter_raid_groupe_0");
        server.addSimpleWorld("clickhunter_boss_serveur");
        admin = server.addPlayer("Admin");
        admin.teleport(lobby.getSpawnLocation());
        admin.addAttachment(plugin, "cliker.raid.admin", true);
        admin.addAttachment(plugin, "cliker.boss.admin", true);
        setup = new RaidSetup(plugin, (p, b) -> {});
    }

    @AfterEach
    void after() {
        try {
            setup.shutdown();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void pointsPersistWithOrientationAndExitMustBeOutsideArena() {
        admin.teleport(new Location(arena, 12, 66, 14, 47, 13));
        setup.command(admin, false, new String[] {"admin", "set", "1", "centre"});
        assertEquals(admin.getLocation(), setup.point(arena, "centre", arena.getSpawnLocation()));
        setup.command(admin, false, new String[] {"admin", "set", "1", "sortie"});
        assertNull(setup.exit(arena));
        admin.teleport(new Location(lobby, 4, 65, 2, 180, 0));
        setup.command(admin, false, new String[] {"admin", "set", "1", "sortie"});
        assertEquals(admin.getLocation(), setup.exit(arena));
        setup.shutdown();
        setup = new RaidSetup(plugin, (p, b) -> {});
        assertEquals(47, setup.point(arena, "centre", arena.getSpawnLocation()).getYaw());
        assertEquals(admin.getLocation(), setup.exit(arena));
    }

    @Test
    void mapRejectsSpawnAndDuplicateWorldAssignments() {
        setup.command(admin, false, new String[] {"admin", "map", "1", "lobby"});
        assertEquals("clickhunter_raid_groupe_0", setup.world(false, 0));
        setup.command(admin, true, new String[] {"admin", "map", "clickhunter_raid_groupe_0"});
        assertEquals("clickhunter_boss_serveur", setup.world(true, 0));
        var custom = server.addSimpleWorld("arena_custom");
        setup.command(admin, false, new String[] {"admin", "map", "1", custom.getName()});
        assertEquals(
                "clickhunter_raid_groupe_0",
                setup.world(false, 0)); // Un combat en cours conserve sa carte.
        setup.shutdown();
        setup = new RaidSetup(plugin, (p, b) -> {});
        assertEquals(custom.getName(), setup.world(false, 0));
        setup.command(admin, false, new String[] {"admin", "map", "2", custom.getName()});
        assertEquals("clickhunter_raid_groupe_1", setup.world(false, 1));
    }

    @Test
    void adminPermissionAndNumericBoundsAreEnforcedWithoutChangingConfig() {
        var player = server.addPlayer("Normal");
        setup.command(player, false, new String[] {"admin", "rayon", "1", "250"});
        assertEquals(31, setup.radius(arena));
        setup.command(admin, false, new String[] {"admin", "rayon", "1", "257"});
        assertEquals(31, setup.radius(arena));
        setup.command(admin, false, new String[] {"admin", "rayon", "1", "40"});
        assertEquals(40, setup.radius(arena));
        setup.command(admin, true, new String[] {"admin", "param", "boss.echelle", "NaN"});
        var saved = new Settings(plugin, "activites/arenes.yml");
        assertFalse(saved.yaml.contains("boss.parametres.boss.echelle"));
        setup.command(admin, true, new String[] {"admin", "param", "boss.echelle", "6"});
        setup.command(admin, true, new String[] {"admin", "param", "boss.pv", "9000"});
        fr.cliker.clikerv2.config.ConfigFiles.install(plugin, "raid.yml");
        setup.command(admin, false, new String[] {"admin", "param", "boss.pv", "2000"});
        var file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "raid.yml");
        var normal = YamlConfiguration.loadConfiguration(file);
        var actual = YamlConfiguration.loadConfiguration(file);
        setup.applyBossParameters(actual);
        assertEquals(6, actual.getDouble("boss.echelle"));
        assertEquals(9000, actual.getLong("boss.pv"));
        assertEquals(2000, normal.getLong("boss.pv"));
    }
}
