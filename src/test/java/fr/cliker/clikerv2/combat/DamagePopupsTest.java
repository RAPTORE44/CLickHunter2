package fr.cliker.clikerv2.combat;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DamagePopupsTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private World world;
    private PlayerMock player;
    private LivingEntity victim;
    private DamagePopups popups;
    @BeforeEach void setup() throws Exception {
        server = MockBukkit.mock(); plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("damage"); player = server.addPlayer();
        victim = world.spawn(new Location(world, 1, 65, 1), Cow.class);
        var file = plugin.getDataFolder().toPath().resolve("damage-popups.yml");
        Files.createDirectories(file.getParent());
        try (var input = getClass().getResourceAsStream("/damage-popups.yml")) { Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING); }
        var config = YamlConfiguration.loadConfiguration(file.toFile());
        config.set("dispersion", 0); config.set("maximum-actifs", 3); config.set("maximum-par-joueur", 2); config.save(file.toFile());
        popups = new DamagePopups(plugin);
    }
    @AfterEach void cleanup() { try { if (popups != null) popups.shutdown(); } finally { MockBukkit.unmock(); } }
    private List<ArmorStand> active() { return world.getEntitiesByClass(ArmorStand.class).stream().filter(Entity::isValid).toList(); }
    @Test void risesExactlyOneBlockThenDisappears() {
        popups.show(player, victim, 25);
        assertEquals(1, active().size()); ArmorStand stand = active().getFirst();
        Location initial = stand.getLocation();
        assertEquals("-25", PlainTextComponentSerializer.plainText().serialize(stand.customName()));
        assertFalse(stand.isPersistent()); assertTrue(stand.isMarker()); assertFalse(stand.hasGravity());
        server.getScheduler().performTicks(10);
        assertEquals(initial.getY() + 0.5, stand.getLocation().getY(), 0.00001);
        server.getScheduler().performTicks(10);
        assertEquals(initial.getY() + 1.0, stand.getLocation().getY(), 0.00001);
        assertEquals(initial.getX(), stand.getLocation().getX()); assertEquals(initial.getZ(), stand.getLocation().getZ());
        server.getScheduler().performOneTick(); assertFalse(stand.isValid()); assertTrue(active().isEmpty());
    }
    @Test void zeroDamageDoesNotCreateTextAndSpamIsBounded() {
        popups.show(player, victim, 0); assertTrue(active().isEmpty());
        for (int i = 0; i < 20; i++) popups.show(player, victim, 25);
        assertEquals(2, active().size());
        PlayerMock second = server.addPlayer();
        for (int i = 0; i < 20; i++) popups.show(second, victim, 25);
        assertEquals(3, active().size());
        server.getScheduler().performTicks(21); assertTrue(active().isEmpty());
    }
    @Test void quittingAndWorldUnloadingRemoveTexts() {
        popups.show(player, victim, 25);
        server.getPluginManager().callEvent(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        assertTrue(active().isEmpty());
        popups.show(player, victim, 25);
        server.getPluginManager().callEvent(new WorldUnloadEvent(world));
        assertTrue(active().isEmpty());
    }
    @Test void shutdownRemovesTextsAndPreventsNewOnes() {
        popups.show(player, victim, 25); ArmorStand stand = active().getFirst();
        popups.shutdown(); assertFalse(stand.isValid());
        popups.show(player, victim, 25); assertTrue(active().isEmpty());
    }
}
