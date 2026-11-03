package fr.cliker.clikerv2.discord.sync;

import static fr.cliker.clikerv2.discord.sync.RankSyncTest.*;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.regex.*;

class DiscordRankSyncTest {
    ServerMock server;
    org.bukkit.plugin.java.JavaPlugin plugin;
    DatabaseManager db;
    DiscordRankSync sync;
    org.mockbukkit.mockbukkit.entity.PlayerMock player;
    RankSyncTest.FakePermissions permissions;
    final List<String> threads = new CopyOnWriteArrayList<>();
    final List<String> proofMessages = new CopyOnWriteArrayList<>();
    volatile String author = USER, content = "";
    volatile long messageId;
    boolean enabled = true;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("LinkedPlayer");
        player.addAttachment(plugin, DiscordRankSync.LINK, true);
        player.addAttachment(plugin, "cliker.discord.use", true);
        db = new DatabaseManager(plugin, "rank-worker-" + UUID.randomUUID() + ".db");
        db.initialize();
        permissions = new RankSyncTest.FakePermissions();
        var file = plugin.getDataFolder().toPath().resolve("integrations/discord-grades.yml");
        Files.createDirectories(file.getParent());
        try (var in = getClass().getResourceAsStream("/integrations/discord-grades.yml")) {
            Files.copy(in, file);
        }
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        yaml.set("actif", true);
        yaml.set("bot-token", "unit-test-token");
        yaml.set("serveur-id", GUILD);
        yaml.set("salon-liaison-id", CHANNEL);
        yaml.set("groupes.vip", ROLE);
        yaml.save(file.toFile());
        var remote = new RankSyncTest.FakeDiscord();
        sync =
                new DiscordRankSync(
                        plugin,
                        db,
                        () -> permissions,
                        token ->
                                new DiscordBotApi(
                                        token,
                                        (method, path, secret) -> {
                                            assertFalse(org.bukkit.Bukkit.isPrimaryThread());
                                            threads.add(Thread.currentThread().getName());
                                            if (path.startsWith(
                                                    "/channels/" + CHANNEL + "/messages")) {
                                                if (content.isEmpty() || path.endsWith("limit=1"))
                                                    return FakeDiscord.ok("[]");
                                                String after =
                                                        path.substring(path.indexOf("after=") + 6);
                                                if (new java.math.BigInteger(after)
                                                                .compareTo(
                                                                        java.math.BigInteger
                                                                                .valueOf(messageId))
                                                        >= 0) return FakeDiscord.ok("[]");
                                                return FakeDiscord.ok(
                                                        "[{\"id\":\""
                                                                + messageId
                                                                + "\",\"content\":\""
                                                                + content
                                                                + "\",\"author\":{\"id\":\""
                                                                + author
                                                                + "\",\"bot\":false}}]");
                                            }
                                            return remote.request(method, path, secret);
                                        },
                                        0));
    }

    void pump() {
        db.submit(c -> null).join();
        server.getScheduler().performTicks(2);
    }

    void await(BooleanSupplier condition, long millis) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
            pump();
            Thread.sleep(20);
        }
        pump();
        assertTrue(condition.getAsBoolean(), "condition non atteinte");
    }

    List<String> chat() {
        List<String> out = new ArrayList<>();
        net.kyori.adventure.text.Component msg;
        while ((msg = player.nextComponentMessage()) != null)
            out.add(PlainTextComponentSerializer.plainText().serialize(msg));
        return out;
    }

    @AfterEach
    void close() {
        try {
            sync.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void nativeWorkerLinksVerifiedAccountFromCodeAndOnlyUsesItsBackgroundThread() throws Exception {
        List<String> messages = new ArrayList<>();
        String[] code = {null};
        player.addAttachment(plugin, DiscordRankSync.ADMIN, true);
        Command status =
                new Command("discordsync") {
                    public boolean execute(CommandSender s, String l, String[] a) {
                        return false;
                    }
                };
        await(
                () -> {
                    sync.onCommand(player, status, "discordsync", new String[] {"statut"});
                    messages.addAll(chat());
                    return messages.stream().anyMatch(s -> s.contains("connecté"));
                },
                5000);
        sync.playerCommand(player, new String[] {"lier"});
        await(
                () -> {
                    messages.addAll(chat());
                    for (String text : messages) {
                        var match =
                                Pattern.compile("Code de liaison : ([A-Z2-9]{12})").matcher(text);
                        if (match.find()) code[0] = match.group(1);
                    }
                    return code[0] != null;
                },
                5000);
        assertTrue(messages.stream().anyMatch(s -> s.contains("Copier le message Discord")));
        Thread.sleep(5);
        messageId = (System.currentTimeMillis() - 1420070400000L) << 22;
        content = "<@" + BOT + "> lier " + code[0];
        var links = new RankLinkStore(db);
        await(() -> links.link(player.getUniqueId()).join() != null, 8000);
        var link = links.link(player.getUniqueId()).join();
        assertEquals(USER, link.discord());
        assertEquals(GUILD, link.guild());
        assertFalse(threads.isEmpty());
        assertTrue(threads.stream().allMatch(n -> n.equals("ClickHunter-DiscordRanks")));
        assertFalse(links.issue(player.getUniqueId(), "another", 0, Long.MAX_VALUE).join());
    }

    @Test
    void linkingAndReloadCommandsEnforcePermissionsAndDoNotExposeToken() {
        player.addAttachment(plugin, DiscordRankSync.LINK, false);
        sync.playerCommand(player, new String[] {"lier"});
        assertTrue(chat().stream().anyMatch(s -> s.contains("Permission")));
        Command command =
                new Command("discordsync") {
                    public boolean execute(CommandSender s, String l, String[] a) {
                        return false;
                    }
                };
        sync.onCommand(player, command, "discordsync", new String[] {"reload"});
        var text = chat();
        assertTrue(text.stream().anyMatch(s -> s.contains("Permission")));
        assertFalse(text.toString().contains("unit-test-token"));
    }
}
