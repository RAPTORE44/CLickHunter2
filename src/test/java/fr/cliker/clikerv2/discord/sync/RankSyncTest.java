package fr.cliker.clikerv2.discord.sync;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import fr.cliker.clikerv2.DatabaseManager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class RankSyncTest {
    static final String GUILD = "100000000000000001",
            ROLE = "100000000000000002",
            CHANNEL = "100000000000000003",
            BOT = "100000000000000004",
            BOT_ROLE = "100000000000000005",
            USER = "100000000000000006",
            OTHER = "100000000000000007";
    DatabaseManager db;
    RankLinkStore store;
    FakePermissions permissions;
    FakeDiscord remote;
    DiscordBotApi api;
    RankSyncEngine engine;
    RankLinkStore.Link link;
    UUID owner = UUID.randomUUID();

    static class FakePermissions implements RankPermissions {
        final Map<UUID, Map<String, Membership>> users = new HashMap<>();
        int revision, saves;
        boolean failAfterSave, changeBeforeApply;

        void set(UUID id, String group, boolean value) {
            users.computeIfAbsent(id, k -> new HashMap<>())
                    .put(group, new Membership(value, "v" + (++revision)));
        }

        public Map<String, Membership> read(UUID id, Set<String> groups) {
            Map<String, Membership> out = new HashMap<>();
            for (String group : groups)
                out.put(
                        group,
                        users.getOrDefault(id, Map.of())
                                .getOrDefault(group, new Membership(false, "empty")));
            return out;
        }

        public boolean apply(UUID id, String group, Membership expected, boolean value)
                throws Exception {
            if (changeBeforeApply) {
                changeBeforeApply = false;
                set(id, group, !value);
                return false;
            }
            if (!read(id, Set.of(group)).get(group).equals(expected)) return false;
            if (expected.present() != value) set(id, group, value);
            saves++;
            if (failAfterSave) {
                failAfterSave = false;
                throw new java.io.IOException("Simulation sauvegarde LP");
            }
            return true;
        }

        public void validate(Set<String> groups) {}

        boolean has(UUID id, String group) {
            return read(id, Set.of(group)).get(group).present();
        }
    }

    static class FakeDiscord implements DiscordBotApi.Transport {
        Set<String> roles = new HashSet<>();
        List<String> writes = new ArrayList<>();
        boolean absent, deleted, forbidden;
        int failWrite;

        public DiscordBotApi.Reply request(String method, String path, String token) {
            if (method.equals("GET") && path.equals("/users/@me"))
                return ok("{\"id\":\"" + BOT + "\",\"bot\":true}");
            if (method.equals("GET") && path.equals("/guilds/" + GUILD + "/roles"))
                return ok(
                        "["
                                + role(GUILD, 0, "0", false)
                                + ","
                                + role(BOT_ROLE, 20, forbidden ? "0" : "268502016", false)
                                + (deleted ? "" : "," + role(ROLE, 5, "0", false))
                                + "]");
            if (method.equals("GET") && path.equals("/channels/" + CHANNEL))
                return ok("{\"guild_id\":\"" + GUILD + "\",\"type\":0}");
            if (method.equals("GET") && path.equals("/guilds/" + GUILD + "/members/" + BOT))
                return ok("{\"roles\":[\"" + BOT_ROLE + "\"]}");
            if (method.equals("GET") && path.equals("/guilds/" + GUILD + "/members/" + USER))
                return absent
                        ? new DiscordBotApi.Reply(404, "", 0)
                        : ok("{\"roles\":" + new Gson().toJson(roles) + "}");
            if (path.equals("/guilds/" + GUILD + "/members/" + USER + "/roles/" + ROLE)) {
                if (failWrite != 0) {
                    int status = failWrite;
                    failWrite = 0;
                    return new DiscordBotApi.Reply(status, "secret-response-never-logged", 1000);
                }
                writes.add(method + ":" + ROLE);
                if (method.equals("PUT")) roles.add(ROLE);
                else roles.remove(ROLE);
                return new DiscordBotApi.Reply(204, "", 0);
            }
            throw new AssertionError(method + " " + path);
        }

        static DiscordBotApi.Reply ok(String body) {
            return new DiscordBotApi.Reply(200, body, 0);
        }

        static String role(String id, int position, String perms, boolean managed) {
            return "{\"id\":\""
                    + id
                    + "\",\"position\":"
                    + position
                    + ",\"permissions\":\""
                    + perms
                    + "\",\"managed\":"
                    + managed
                    + "}";
        }
    }

    static RankSyncConfig config(boolean minecraft) {
        return new RankSyncConfig(
                true, "private-token", GUILD, CHANNEL, Map.of("vip", ROLE), minecraft, 30, 20, 10);
    }

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "discord-ranks-" + UUID.randomUUID() + ".db");
        db.initialize();
        store = new RankLinkStore(db);
        permissions = new FakePermissions();
        remote = new FakeDiscord();
        api = new DiscordBotApi("private-token", remote, 0);
        engine = new RankSyncEngine(config(true), store, permissions, api);
        long now = System.currentTimeMillis();
        assertTrue(store.issue(owner, "hash", now - 10, now + 600000).join());
        link = store.redeem("hash", USER, GUILD, now, now).join();
        assertNotNull(link);
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
    void validatesBotPermissionsRoleHierarchyAndChannelBeforeSync() throws Exception {
        engine.validate();
        assertEquals(BOT, engine.bot());
        remote.forbidden = true;
        assertThrows(IllegalArgumentException.class, engine::validate);
    }

    @Test
    void minecraftAdditionAndRemovalPropagateWithoutTouchingOtherRolesOrGroups() throws Exception {
        permissions.set(owner, "staff", true);
        remote.roles.add(OTHER);
        engine.sync(link);
        permissions.set(owner, "vip", true);
        engine.sync(link);
        assertTrue(remote.roles.contains(ROLE));
        assertTrue(remote.roles.contains(OTHER));
        permissions.set(owner, "vip", false);
        engine.sync(link);
        assertFalse(remote.roles.contains(ROLE));
        assertTrue(remote.roles.contains(OTHER));
        assertTrue(permissions.has(owner, "staff"));
        assertEquals(List.of("PUT:" + ROLE, "DELETE:" + ROLE), remote.writes);
    }

    @Test
    void discordAdditionAndRemovalUpdateOnlyMappedMinecraftParents() throws Exception {
        engine.sync(link);
        permissions.set(owner, "staff", true);
        remote.roles.add(ROLE);
        engine.sync(link);
        assertTrue(permissions.has(owner, "vip"));
        remote.roles.remove(ROLE);
        engine.sync(link);
        assertFalse(permissions.has(owner, "vip"));
        assertTrue(permissions.has(owner, "staff"));
        assertTrue(remote.writes.isEmpty());
    }

    @Test
    void firstLinkDefaultsToMinecraftAndDiscordInitialSourceIsConfigurable() throws Exception {
        remote.roles.add(ROLE);
        engine.sync(link);
        assertFalse(remote.roles.contains(ROLE));
        assertFalse(permissions.has(owner, "vip"));
        store.beginUnlink(owner).join();
        store.delete(store.link(owner).join()).join();
        long now = System.currentTimeMillis();
        store.issue(owner, "new-hash", now - 1, now + 10000).join();
        link = store.redeem("new-hash", USER, GUILD, now, now).join();
        remote.roles.add(ROLE);
        new RankSyncEngine(config(false), store, permissions, api).sync(link);
        assertTrue(permissions.has(owner, "vip"));
    }

    @Test
    void missingMemberOrFailedDiscordRequestNeverRemovesMinecraftGrade() throws Exception {
        permissions.set(owner, "vip", true);
        engine.sync(link);
        remote.absent = true;
        assertEquals(RankSyncEngine.Outcome.MEMBER_ABSENT, engine.sync(link));
        assertTrue(permissions.has(owner, "vip"));
        remote.absent = false;
        permissions.set(owner, "vip", false);
        remote.failWrite = 403;
        assertThrows(DiscordBotApi.Failure.class, () -> engine.sync(link));
        assertEquals(Boolean.TRUE, store.state(link, "vip", ROLE).join().baseline());
        assertFalse(permissions.has(owner, "vip"));
        assertTrue(remote.roles.contains(ROLE));
        engine.sync(link);
        assertFalse(remote.roles.contains(ROLE));
    }

    @Test
    void unfinishedMinecraftToDiscordOperationResumesAcrossEngineRestart() throws Exception {
        engine.sync(link);
        permissions.set(owner, "vip", true);
        remote.failWrite = 500;
        assertThrows(DiscordBotApi.Failure.class, () -> engine.sync(link));
        assertEquals(Boolean.TRUE, store.state(link, "vip", ROLE).join().pending());
        new RankSyncEngine(config(true), new RankLinkStore(db), permissions, api).sync(link);
        assertTrue(remote.roles.contains(ROLE));
        assertNull(store.state(link, "vip", ROLE).join().pending());
        int writes = remote.writes.size();
        engine.sync(link);
        assertEquals(writes, remote.writes.size());
    }

    @Test
    void failedLuckPermsSaveIsRetriedEvenWhenInMemoryGroupAlreadyMatches() throws Exception {
        engine.sync(link);
        remote.roles.add(ROLE);
        permissions.failAfterSave = true;
        assertThrows(java.io.IOException.class, () -> engine.sync(link));
        assertTrue(permissions.has(owner, "vip"));
        assertEquals(Boolean.TRUE, store.state(link, "vip", ROLE).join().pending());
        int saves = permissions.saves;
        new RankSyncEngine(config(true), store, permissions, api).sync(link);
        assertTrue(permissions.saves > saves);
        assertNull(store.state(link, "vip", ROLE).join().pending());
    }

    @Test
    void removingRoleFromDiscordCatalogDoesNotStripMinecraftPurchases() throws Exception {
        permissions.set(owner, "vip", true);
        engine.sync(link);
        remote.roles.clear();
        remote.deleted = true;
        assertThrows(IllegalArgumentException.class, () -> engine.sync(link));
        assertTrue(permissions.has(owner, "vip"));
    }

    @Test
    void unlinkRevokesMappedDiscordRolesPreservesMinecraftAndDeletesSnapshots() throws Exception {
        permissions.set(owner, "vip", true);
        remote.roles.add(OTHER);
        engine.sync(link);
        store.beginUnlink(owner).join();
        assertEquals(RankSyncEngine.Outcome.UNLINKED, engine.sync(store.link(owner).join()));
        assertNull(store.link(owner).join());
        assertTrue(permissions.has(owner, "vip"));
        assertFalse(remote.roles.contains(ROLE));
        assertTrue(remote.roles.contains(OTHER));
        assertNull(store.state(link, "vip", ROLE).join().baseline());
    }

    @Test
    void staleMinecraftMembershipIsNotOverwrittenByDiscordChange() throws Exception {
        engine.sync(link);
        remote.roles.add(ROLE);
        permissions.changeBeforeApply = true;
        assertEquals(RankSyncEngine.Outcome.RETRY, engine.sync(link));
        assertFalse(permissions.has(owner, "vip"));
        assertNotNull(store.state(link, "vip", ROLE).join().pending());
    }

    @Test
    void leavingAndRejoiningDiscordRestoresPurchasedGradeInsteadOfRevokingIt() throws Exception {
        permissions.set(owner, "vip", true);
        engine.sync(link);
        remote.absent = true;
        remote.roles.clear();
        engine.sync(link);
        assertTrue(permissions.has(owner, "vip"));
        remote.absent = false;
        new RankSyncEngine(config(true), store, permissions, api).sync(link);
        assertTrue(permissions.has(owner, "vip"));
        assertTrue(remote.roles.contains(ROLE));
    }

    @Test
    void closedEngineCannotWritePermissionsOrRoles() {
        permissions.set(owner, "vip", true);
        var closed = new RankSyncEngine(config(true), store, permissions, api, () -> false);
        assertThrows(java.io.InterruptedIOException.class, () -> closed.sync(link));
        assertTrue(remote.writes.isEmpty());
    }

    @Test
    void codesExpireAreSingleUseAndEnforceOneDiscordAccountPerMinecraftAccount() {
        UUID second = UUID.randomUUID();
        long now = System.currentTimeMillis();
        assertTrue(store.issue(second, "code2", now, now + 100).join());
        assertNull(store.redeem("code2", OTHER, GUILD, now - 1, now).join());
        assertNull(store.redeem("code2", OTHER, GUILD, now, now + 101).join());
        assertNull(store.redeem("code2", USER, GUILD, now, now).join());
        assertNotNull(store.redeem("code2", OTHER, GUILD, now, now).join());
        assertNull(store.redeem("code2", OTHER, GUILD, now, now).join());
        assertFalse(store.issue(owner, "another", now, now + 100).join());
    }

    @Test
    void newCodeInvalidatesOldCodeAndNoPlaintextIsStored() {
        UUID second = UUID.randomUUID();
        long now = System.currentTimeMillis();
        String code = DiscordRankSync.code(new java.security.SecureRandom());
        assertEquals(12, code.length());
        String hash = DiscordRankSync.hash(code);
        assertNotEquals(code, hash);
        assertEquals(hash, DiscordRankSync.hash(code.toLowerCase(Locale.ROOT)));
        store.issue(second, hash, now, now + 1000).join();
        store.issue(second, "replacement", now, now + 1000).join();
        assertFalse(store.validCode(hash, now, now).join());
        assertTrue(store.validCode("replacement", now, now).join());
    }

    @Test
    void configurationRejectsDuplicateRolesEveryoneAndInvalidIdsAndHidesToken() {
        var yaml = new YamlConfiguration();
        yaml.set("groupes.vip", ROLE);
        yaml.set("groupes.mvp", ROLE);
        assertThrows(IllegalArgumentException.class, () -> RankSyncConfig.read(yaml, null));
        yaml.set("groupes.mvp", "");
        yaml.set("serveur-id", ROLE);
        assertThrows(IllegalArgumentException.class, () -> RankSyncConfig.read(yaml, null));
        assertFalse(config(true).toString().contains("private-token"));
    }

    @Test
    void rateLimitPausesNextRequestsWithoutLeakingResponseBodyOrToken() throws Exception {
        int[] requests = {0};
        var limited =
                new DiscordBotApi(
                        "TOP_SECRET",
                        (method, path, token) -> {
                            requests[0]++;
                            return new DiscordBotApi.Reply(429, "TOP_SECRET", 8000);
                        },
                        0);
        try {
            var first =
                    assertThrows(
                            DiscordBotApi.Failure.class,
                            () -> limited.request("GET", "/users/@me"));
            assertEquals(8000, first.retryMillis);
            assertFalse(first.toString().contains("TOP_SECRET"));
            assertThrows(DiscordBotApi.Failure.class, () -> limited.request("GET", "/users/@me"));
            assertEquals(1, requests[0]);
        } finally {
            limited.close();
        }
    }

    @Test
    void oversizedHttpBodyIsCancelledBeforeAccumulatingTheWholeResponse() {
        var subscriber = new DiscordBotApi.LimitedBody(4);
        boolean[] cancelled = {false};
        subscriber.onSubscribe(
                new java.util.concurrent.Flow.Subscription() {
                    public void request(long n) {}

                    public void cancel() {
                        cancelled[0] = true;
                    }
                });
        subscriber.onNext(
                List.of(
                        java.nio.ByteBuffer.wrap(new byte[] {1, 2, 3}),
                        java.nio.ByteBuffer.wrap(new byte[] {4, 5})));
        assertTrue(cancelled[0]);
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join());
    }

    @Test
    void boundedHttpBodyCompletesNormallyAcrossSeveralChunks() {
        var subscriber = new DiscordBotApi.LimitedBody(4);
        subscriber.onSubscribe(
                new java.util.concurrent.Flow.Subscription() {
                    public void request(long n) {}

                    public void cancel() {
                        fail("Le corps valide ne doit pas être annulé");
                    }
                });
        subscriber.onNext(List.of(java.nio.ByteBuffer.wrap(new byte[] {1, 2})));
        subscriber.onNext(List.of(java.nio.ByteBuffer.wrap(new byte[] {3, 4})));
        subscriber.onComplete();
        assertArrayEquals(
                new byte[] {1, 2, 3, 4}, subscriber.getBody().toCompletableFuture().join());
    }
}
