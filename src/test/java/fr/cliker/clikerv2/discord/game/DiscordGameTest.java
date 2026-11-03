package fr.cliker.clikerv2.discord.game;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.discord.sync.DiscordBotApi;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.rankings.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class DiscordGameTest {
    static final String GUILD = "200000000000000001",
            CHANNEL = "200000000000000002",
            BOT = "200000000000000003",
            ROLE = "200000000000000004";
    DatabaseManager db;
    DiscordGameStore store;
    FakeDiscord transport;
    DiscordBotApi api;
    GameDiscordConfig config;
    DiscordGamePublisher publisher;

    static class FakeDiscord implements DiscordBotApi.Transport {
        final Map<String, JsonObject> messages = new ConcurrentHashMap<>();
        final AtomicInteger posts = new AtomicInteger(), edits = new AtomicInteger();
        boolean forbidden, foreignGuild;
        String deleted;
        String failEditOnce;
        volatile boolean backgroundOnly;

        public DiscordBotApi.Reply request(String method, String path, String token)
                throws Exception {
            return request(method, path, token, null);
        }

        public DiscordBotApi.Reply request(String method, String path, String token, String raw)
                throws Exception {
            if (backgroundOnly) assertFalse(org.bukkit.Bukkit.isPrimaryThread());
            if (path.equals("/users/@me")) return ok("{\"id\":\"" + BOT + "\",\"bot\":true}");
            if (path.equals("/guilds/" + GUILD + "/members/" + BOT))
                return ok("{\"roles\":[\"" + ROLE + "\"]}");
            if (path.equals("/guilds/" + GUILD + "/roles"))
                return ok(
                        "[{\"id\":\""
                                + GUILD
                                + "\",\"permissions\":\"0\"},{\"id\":\""
                                + ROLE
                                + "\",\"permissions\":\""
                                + (forbidden ? "0" : "84992")
                                + "\"}]");
            if (path.equals("/channels/" + CHANNEL))
                return ok(
                        "{\"guild_id\":\""
                                + (foreignGuild ? "300000000000000001" : GUILD)
                                + "\",\"type\":0}");
            if (method.equals("GET")
                    && path.equals("/channels/" + CHANNEL + "/messages?limit=100")) {
                JsonArray out = new JsonArray();
                messages.values().forEach(v -> out.add(v.deepCopy()));
                return ok(out.toString());
            }
            if (method.equals("POST") && path.equals("/channels/" + CHANNEL + "/messages")) {
                int next = posts.incrementAndGet();
                String id = "4000000000000000" + String.format("%02d", next);
                JsonObject body = JsonParser.parseString(raw).getAsJsonObject();
                body.addProperty("id", id);
                var author = new JsonObject();
                author.addProperty("id", BOT);
                body.add("author", author);
                messages.put(id, body);
                return ok(body.toString());
            }
            if (method.equals("PATCH") && path.startsWith("/channels/" + CHANNEL + "/messages/")) {
                String id = path.substring(path.lastIndexOf('/') + 1);
                if (id.equals(failEditOnce)) {
                    failEditOnce = null;
                    return new DiscordBotApi.Reply(500, "", 0);
                }
                if (!messages.containsKey(id)) return new DiscordBotApi.Reply(404, "", 0);
                var body = JsonParser.parseString(raw).getAsJsonObject();
                body.addProperty("id", id);
                var author = new JsonObject();
                author.addProperty("id", BOT);
                body.add("author", author);
                messages.put(id, body);
                edits.incrementAndGet();
                return ok(body.toString());
            }
            throw new AssertionError(method + " " + path);
        }

        static DiscordBotApi.Reply ok(String body) {
            return new DiscordBotApi.Reply(200, body, 0);
        }
    }

    static GameDiscordConfig configuration() {
        return new GameDiscordConfig(
                true,
                "fake-secret",
                GUILD,
                3_600_000,
                ZoneId.of("Europe/Paris"),
                List.of(
                        new GameDiscordConfig.Board(
                                "ecus", RankingMetric.ECUS, RankingPeriod.ALL, CHANNEL)),
                Map.of(
                        "boss-serveur-spawn",
                        new GameDiscordConfig.Event(
                                CHANNEL,
                                "Boss {boss}",
                                "PV : {pv} ({pv_bruts}) • {adresse}",
                                0xff0000)),
                "play.clickhunter.fr");
    }

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "discord-game-" + UUID.randomUUID() + ".db");
        db.initialize();
        store = new DiscordGameStore(db);
        transport = new FakeDiscord();
        api = new DiscordBotApi("fake-secret", transport);
        config = configuration();
        publisher = new DiscordGamePublisher(config, store, new RankingStore(db), api, () -> true);
        publisher.validate();
    }

    @AfterEach
    void cleanup() {
        try {
            api.close();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void topTenUsesActualGameBalancesAndExactlyTenRowsWithoutMentions() throws Exception {
        var economy = new EconomyService(db, Map.of());
        economy.set(UUID.randomUUID(), "Rich_Player", CurrencyType.ECUS, 12345).join();
        economy.set(UUID.randomUUID(), "Other", CurrencyType.ECUS, 20).join();
        assertEquals(1, publisher.refresh(false));
        assertEquals(1, transport.posts.get());
        var body = transport.messages.values().iterator().next();
        var embed = body.getAsJsonArray("embeds").get(0).getAsJsonObject();
        String text = embed.get("description").getAsString();
        assertTrue(text.indexOf("Rich\\_Player") < text.indexOf("Other"));
        assertEquals(10, text.lines().filter(s -> s.startsWith("`#")).count());
        assertTrue(text.contains("`#10` **—** — 0 Écus"));
        assertTrue(body.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty());
        assertTrue(body.get("enforce_nonce").getAsBoolean());
    }

    @Test
    void hourlyScheduleSkipsEarlyRefreshAndForcedUpdateEditsSameMessageAfterRestart()
            throws Exception {
        publisher.refresh(false);
        String id = transport.messages.keySet().iterator().next();
        assertEquals(0, publisher.refresh(false));
        assertEquals(0, transport.edits.get());
        var restarted =
                new DiscordGamePublisher(
                        config, new DiscordGameStore(db), new RankingStore(db), api, () -> true);
        restarted.validate();
        assertEquals(0, restarted.refresh(false));
        restarted.refresh(true);
        assertEquals(1, transport.posts.get());
        assertEquals(1, transport.edits.get());
        assertTrue(transport.messages.containsKey(id));
        store.save(BOT, CHANNEL, "ecus", id, System.currentTimeMillis() - 3_600_001).join();
        assertEquals(1, restarted.refresh(false));
        assertEquals(1, transport.posts.get());
    }

    @Test
    void deletedDiscordMessageIsRecreatedOnceAndReplacementIdIsPersisted() throws Exception {
        publisher.refresh(false);
        String old = store.message(BOT, CHANNEL, "ecus").join().id();
        transport.messages.remove(old);
        publisher.refresh(true);
        assertEquals(2, transport.posts.get());
        String replacement = store.message(BOT, CHANNEL, "ecus").join().id();
        assertNotEquals(old, replacement);
        publisher.refresh(true);
        assertEquals(2, transport.posts.get());
    }

    @Test
    void lostSqlAcknowledgementRecoversOwnMessageInsteadOfPostingDuplicate() throws Exception {
        publisher.refresh(false);
        db.submit(
                        c -> {
                            try (var q = c.createStatement()) {
                                q.executeUpdate("DELETE FROM discord_game_boards");
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        var restarted =
                new DiscordGamePublisher(config, store, new RankingStore(db), api, () -> true);
        restarted.validate();
        restarted.refresh(false);
        assertEquals(1, transport.posts.get());
        assertNotNull(store.message(BOT, CHANNEL, "ecus").join());
        assertEquals(1, transport.edits.get());
    }

    @Test
    void eventShowsBossHealthAndIsDeduplicatedAcrossPublisherRestart() throws Exception {
        var values = Map.of("boss", "Vache", "pv", "1B", "pv_bruts", "1000000000");
        assertTrue(publisher.event("boss-serveur-spawn", "boss-12", values));
        var restarted =
                new DiscordGamePublisher(config, store, new RankingStore(db), api, () -> true);
        restarted.validate();
        assertFalse(restarted.event("boss-serveur-spawn", "boss-12", values));
        assertEquals(1, transport.posts.get());
        String body = transport.messages.values().iterator().next().toString();
        assertTrue(body.contains("1000000000"));
        assertTrue(body.contains("play.clickhunter.fr"));
    }

    @Test
    void missingSqlEventReceiptRecoversPostedEventAndDoesNotDuplicate() throws Exception {
        var values = Map.of("boss", "Vache", "pv", "1B", "pv_bruts", "1000000000");
        publisher.event("boss-serveur-spawn", "boss-13", values);
        db.submit(
                        c -> {
                            try (var q = c.createStatement()) {
                                q.executeUpdate("DELETE FROM discord_game_sent");
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertTrue(publisher.event("boss-serveur-spawn", "boss-13", values));
        assertEquals(1, transport.posts.get());
    }

    @Test
    void wrongGuildAndMissingChannelPermissionsRefusePublication() throws Exception {
        transport.foreignGuild = true;
        assertThrows(IllegalArgumentException.class, publisher::validate);
        transport.foreignGuild = false;
        transport.forbidden = true;
        assertThrows(IllegalArgumentException.class, publisher::validate);
        assertEquals(0, transport.posts.get());
    }

    @Test
    void gemsUseTenthsAndUsernamesCannotTriggerDiscordMentions() {
        var board =
                new GameDiscordConfig.Board(
                        "gemmes", RankingMetric.GEMS, RankingPeriod.ALL, CHANNEL);
        var payload =
                DiscordGamePayload.board(
                        board,
                        new RankingStore.Board(
                                List.of(new RankingStore.Entry("@everyone", 15)), LocalDate.now()),
                        LocalDate.now());
        String text = payload.toString();
        assertTrue(text.contains("1.5 Gemmes Sombres") || text.contains("1,5 Gemmes Sombres"));
        assertFalse(text.contains("@everyone"));
    }

    @Test
    void configurationSupportsEachExistingMetricAndAlternatePeriodsWithoutLuckPerms() {
        var yaml = new YamlConfiguration();
        yaml.set("actif", true);
        yaml.set("serveur-id", GUILD);
        yaml.set("classements.salon-id", CHANNEL);
        for (var metric : RankingMetric.values()) {
            yaml.set("classements.tableaux." + metric.id + ".type", metric.id);
            yaml.set("classements.tableaux." + metric.id + ".periode", "semaine");
        }
        var shared = new YamlConfiguration();
        shared.set("bot-token", "private-shared");
        var parsed = GameDiscordConfig.read(yaml, shared, null);
        assertEquals(6, parsed.boards().size());
        assertTrue(parsed.boards().stream().allMatch(b -> b.period() == RankingPeriod.WEEK));
        assertEquals(3600000, parsed.interval());
        assertFalse(parsed.toString().contains("private-shared"));
    }

    @Test
    void partialForcedRefreshResumesRemainingBoardsWithoutEditingCompletedBoardsAgain()
            throws Exception {
        var expanded =
                new GameDiscordConfig(
                        true,
                        "fake-secret",
                        GUILD,
                        3_600_000,
                        ZoneId.of("Europe/Paris"),
                        List.of(
                                new GameDiscordConfig.Board(
                                        "ecus", RankingMetric.ECUS, RankingPeriod.ALL, CHANNEL),
                                new GameDiscordConfig.Board(
                                        "kills", RankingMetric.KILLS, RankingPeriod.ALL, CHANNEL)),
                        Map.of(),
                        "play.clickhunter.fr");
        var panels =
                new DiscordGamePublisher(expanded, store, new RankingStore(db), api, () -> true);
        panels.validate();
        panels.refresh(false);
        transport.failEditOnce = store.message(BOT, CHANNEL, "kills").join().id();
        assertThrows(DiscordBotApi.Failure.class, () -> panels.refresh(true));
        assertEquals(1, transport.edits.get());
        panels.refresh(true);
        assertEquals(2, transport.edits.get());
        assertEquals(2, transport.posts.get());
    }
}
