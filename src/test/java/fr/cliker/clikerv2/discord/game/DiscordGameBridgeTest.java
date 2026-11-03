package fr.cliker.clikerv2.discord.game;

import static fr.cliker.clikerv2.discord.game.DiscordGameTest.*;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.discord.DiscordNotifications;
import fr.cliker.clikerv2.discord.sync.DiscordBotApi;

import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

class DiscordGameBridgeTest {
    ServerMock server;
    org.bukkit.plugin.java.JavaPlugin plugin;
    DatabaseManager db;
    DiscordGameBridge bridge;
    FakeDiscord remote;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "game-worker-" + UUID.randomUUID() + ".db");
        db.initialize();
        remote = new FakeDiscord();
        remote.backgroundOnly = true;
        Path path = plugin.getDataFolder().toPath().resolve("integrations/discord-jeu.yml");
        Files.createDirectories(path.getParent());
        try (var in = getClass().getResourceAsStream("/integrations/discord-jeu.yml")) {
            Files.copy(in, path);
        }
        var config = YamlConfiguration.loadConfiguration(path.toFile());
        config.set("actif", true);
        config.set("bot-token", "unit-only");
        config.set("serveur-id", GUILD);
        config.set("classements.actif", false);
        config.set("evenements.salon-id", CHANNEL);
        config.save(path.toFile());
        bridge = new DiscordGameBridge(plugin, db, token -> new DiscordBotApi(token, remote));
    }

    @AfterEach
    void close() {
        try {
            bridge.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    void await(BooleanSupplier condition) throws Exception {
        long until = System.currentTimeMillis() + 8000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < until) {
            db.submit(c -> null).join();
            server.getScheduler().performTicks(2);
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean());
    }

    @Test
    void nativeRouteReceivesExistingGameEventsOffMainAndDeduplicatesRepeatedAnnouncements()
            throws Exception {
        var values = Map.of("boss", "Vache", "pv", "1B", "pv_bruts", "1000000000");
        DiscordNotifications.emit("boss-serveur-spawn", "native-boss-1", values);
        DiscordNotifications.emit("boss-serveur-spawn", "native-boss-1", values);
        await(() -> remote.posts.get() == 1);
        Thread.sleep(1100);
        assertTrue(DiscordGameBridge.route("boss-serveur-spawn", "native-boss-1", values));
        Thread.sleep(1200);
        assertEquals(1, remote.posts.get());
        assertFalse(DiscordGameBridge.route("not-configured", "id", Map.of()));
    }

    @Test
    void administrationIsPermissionProtectedAndDoesNotRevealToken() {
        var player = server.addPlayer();
        player.addAttachment(plugin, DiscordGameBridge.ADMIN, false);
        var command =
                new Command("discordjeu") {
                    public boolean execute(CommandSender s, String l, String[] a) {
                        return false;
                    }
                };
        bridge.onCommand(player, command, "discordjeu", new String[] {"reload"});
        assertTrue(player.nextMessage().contains("Permission"));
        assertTrue(
                bridge.onTabComplete(player, command, "discordjeu", new String[] {""}).isEmpty());
        player.addAttachment(plugin, DiscordGameBridge.ADMIN, true);
        bridge.onCommand(player, command, "discordjeu", new String[] {"statut"});
        assertFalse(player.nextMessage().contains("unit-only"));
    }
}
