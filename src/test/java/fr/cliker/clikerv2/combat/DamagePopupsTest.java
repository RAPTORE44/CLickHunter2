package fr.cliker.clikerv2.combat;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.*;
import java.util.List;

class DamagePopupsTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private World world;
    private PlayerMock player;
    private LivingEntity victim;
    private DamagePopups popups;
    private final java.util.List<TextDisplay> displays = new java.util.ArrayList<>();

    // MockBukkit 4.36 ne crée pas TextDisplay : déléguer le cycle de vie Entity à son
    // ArmorStandMock, conserver séparément les propriétés spécifiques d'affichage.
    private TextDisplay display(
            Player owner, Location location, java.util.function.Consumer<TextDisplay> setup) {
        var entity = location.getWorld().spawn(location, ArmorStand.class);
        var state = new java.util.HashMap<String, Object>();
        TextDisplay display =
                (TextDisplay)
                        java.lang.reflect.Proxy.newProxyInstance(
                                TextDisplay.class.getClassLoader(),
                                new Class<?>[] {TextDisplay.class},
                                (proxy, method, args) -> {
                                    String name = method.getName();
                                    if (name.equals("text")) {
                                        if (args == null || args.length == 0)
                                            return state.get("text");
                                        state.put("text", args[0]);
                                        return null;
                                    }
                                    if (name.equals("getBillboard")) return state.get("billboard");
                                    if (name.equals("setBillboard")) {
                                        state.put("billboard", args[0]);
                                        return null;
                                    }
                                    if (java.util.Set.of(
                                                    "setShadowed",
                                                    "setDefaultBackground",
                                                    "setTeleportDuration")
                                            .contains(name)) return null;
                                    try {
                                        return entity.getClass()
                                                .getMethod(name, method.getParameterTypes())
                                                .invoke(entity, args);
                                    } catch (java.lang.reflect.InvocationTargetException error) {
                                        throw error.getCause();
                                    }
                                });
        setup.accept(display);
        displays.add(display);
        return display;
    }

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("damage");
        player = server.addPlayer();
        victim = world.spawn(new Location(world, 1, 65, 1), Cow.class);
        var file = plugin.getDataFolder().toPath().resolve("damage-popups.yml");
        Files.createDirectories(file.getParent());
        try (var input = getClass().getResourceAsStream("/damage-popups.yml")) {
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
        }
        var config = YamlConfiguration.loadConfiguration(file.toFile());
        config.set("dispersion", 0);
        config.set("maximum-actifs", 3);
        config.set("maximum-par-joueur", 2);
        config.save(file.toFile());
        popups = new DamagePopups(plugin, this::display);
    }

    @AfterEach
    void cleanup() {
        try {
            if (popups != null) popups.shutdown();
        } finally {
            MockBukkit.unmock();
        }
    }

    private List<TextDisplay> active() {
        return displays.stream().filter(Entity::isValid).toList();
    }

    @Test
    void risesExactlyOneBlockThenDisappears() {
        popups.show(player, victim, 25);
        assertEquals(1, active().size());
        TextDisplay stand = active().getFirst();
        Location initial = stand.getLocation();
        assertEquals("-25", PlainTextComponentSerializer.plainText().serialize(stand.text()));
        assertFalse(stand.isPersistent());
        assertEquals(Display.Billboard.CENTER, stand.getBillboard());
        assertFalse(stand.hasGravity());
        server.getScheduler().performTicks(10);
        assertEquals(initial.getY() + 0.5, stand.getLocation().getY(), 0.00001);
        server.getScheduler().performTicks(10);
        assertEquals(initial.getY() + 1.0, stand.getLocation().getY(), 0.00001);
        assertEquals(initial.getX(), stand.getLocation().getX());
        assertEquals(initial.getZ(), stand.getLocation().getZ());
        server.getScheduler().performOneTick();
        assertFalse(stand.isValid());
        assertTrue(active().isEmpty());
    }

    @Test
    void zeroDamageDoesNotCreateTextAndSpamIsBounded() {
        popups.show(player, victim, 0);
        assertTrue(active().isEmpty());
        for (int i = 0; i < 20; i++) popups.show(player, victim, 25);
        assertEquals(2, active().size());
        PlayerMock second = server.addPlayer();
        for (int i = 0; i < 20; i++) popups.show(second, victim, 25);
        assertEquals(3, active().size());
        server.getScheduler().performTicks(21);
        assertTrue(active().isEmpty());
    }

    @Test
    void quittingAndWorldUnloadingRemoveTexts() {
        popups.show(player, victim, 25);
        server.getPluginManager()
                .callEvent(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        assertTrue(active().isEmpty());
        popups.show(player, victim, 25);
        server.getPluginManager().callEvent(new WorldUnloadEvent(world));
        assertTrue(active().isEmpty());
    }

    @Test
    void shutdownRemovesTextsAndPreventsNewOnes() {
        popups.show(player, victim, 25);
        TextDisplay stand = active().getFirst();
        popups.shutdown();
        assertFalse(stand.isValid());
        popups.show(player, victim, 25);
        assertTrue(active().isEmpty());
    }
}
