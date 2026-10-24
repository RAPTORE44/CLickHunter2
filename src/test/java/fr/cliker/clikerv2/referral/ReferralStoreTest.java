package fr.cliker.clikerv2.referral;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.rewards.MailStore;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ReferralStoreTest {
    private DatabaseManager database;
    private ReferralStore store;
    private EconomyService economy;
    private final UUID recruit = UUID.randomUUID(), sponsor = UUID.randomUUID();
    private final long now = 10_000_000L;
    private final ReferralStore.Reward reward = new ReferralStore.Reward(Map.of(CurrencyType.ECUS, 1000L, CurrencyType.GEMMES_SOMBRES, 15L), List.of("saved-item"));
    @BeforeEach void setup() throws Exception {
        MockBukkit.mock(); var plugin = MockBukkit.createMockPlugin();
        database = new DatabaseManager(plugin, "referral-" + UUID.randomUUID() + ".db"); database.initialize();
        store = new ReferralStore(database, Map.of()); economy = new EconomyService(database, Map.of());
        store.join(recruit, "Filleul", now, now, 1800000).join();
    }
    @AfterEach void cleanup() { try { database.close(); } finally { MockBukkit.unmock(); } }
    private CompletableFuture<ReferralStore.Result> register(UUID target, long time) {
        return store.register(recruit, "Filleul", target, "RAPTORE44", time, reward, reward, "merci filleul", "merci parrain");
    }
    @Test void deadlineSurvivesReconnectAndConfigWindowChange() {
        var result = store.join(recruit, "Filleul", now, now + 60000, 3600000).join();
        assertEquals(now + 1800000, result.deadline()); assertFalse(result.registered());
        assertEquals(ReferralStore.Result.EXPIRED, register(sponsor, now + 1800000).join());
        assertEquals(0, new MailStore(database).page(recruit, 0).join().total());
    }
    @Test void oldPlayersDoNotReceiveANewEligibilityWindow() {
        UUID veteran = UUID.randomUUID();
        var result = store.join(veteran, "Ancien", now - 7200000, now, 1800000).join();
        assertTrue(result.deadline() < now);
    }
    @Test void bothRewardsAreAtomicAndOfflineSponsorGetsPrivateNotice() {
        assertEquals(ReferralStore.Result.ACCEPTED, register(sponsor, now + 1000).join());
        assertEquals(1000L, economy.getBalances(recruit).join().ecus());
        assertEquals(1000L, economy.getBalances(sponsor).join().ecus());
        assertEquals(15L, economy.getBalances(sponsor).join().gemmesSombres());
        assertEquals(1, new MailStore(database).page(sponsor, 0).join().total());
        assertEquals("merci parrain", store.notices(sponsor).join().getFirst().message());
        assertTrue(store.notices(UUID.randomUUID()).join().isEmpty());
        var notice = store.notices(sponsor).join().getFirst();
        store.readNotice(recruit, notice.id()).join(); assertEquals(1, store.notices(sponsor).join().size());
        store.readNotice(sponsor, notice.id()).join(); assertTrue(store.notices(sponsor).join().isEmpty());
    }
    @Test void concurrentSubmissionsRewardEachSideOnlyOnce() {
        List<CompletableFuture<ReferralStore.Result>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) calls.add(register(sponsor, now + 1000));
        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();
        assertEquals(1, calls.stream().map(CompletableFuture::join).filter(r -> r == ReferralStore.Result.ACCEPTED).count());
        assertEquals(1000L, economy.getBalances(recruit).join().ecus());
        assertEquals(1, new MailStore(database).page(recruit, 0).join().total());
        assertTrue(store.join(recruit, "Filleul", now, now + 2000, 1800000).join().registered());
    }
    @Test void selfAndReciprocalReferralsAreRejected() {
        assertEquals(ReferralStore.Result.SELF, register(recruit, now).join());
        store.join(sponsor, "RAPTORE44", now, now, 1800000).join();
        assertEquals(ReferralStore.Result.ACCEPTED, register(sponsor, now).join());
        assertEquals(ReferralStore.Result.CYCLE, store.register(sponsor, "RAPTORE44", recruit, "Filleul", now, reward, reward, "a", "b").join());
    }
    @Test void secondRewardFailureRollsBackReferralMoneyItemsAndNotices() {
        var invalid = new ReferralStore.Reward(Map.of(CurrencyType.ECUS, -1L), List.of());
        assertThrows(CompletionException.class, () -> store.register(recruit, "Filleul", sponsor, "RAPTORE44", now, reward, invalid, "a", "b").join());
        assertEquals(0L, economy.getBalances(recruit).join().ecus());
        assertEquals(0, new MailStore(database).page(recruit, 0).join().total());
        assertTrue(store.notices(recruit).join().isEmpty());
        assertEquals(ReferralStore.Result.ACCEPTED, register(sponsor, now).join());
    }
}
