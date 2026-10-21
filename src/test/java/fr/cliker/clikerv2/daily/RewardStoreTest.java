package fr.cliker.clikerv2.daily;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class RewardStoreTest {
    @TempDir Path folder;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    @Test void concurrentClaimsAcrossTwoConnectionsDeliverExactlyOnce() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (var first = new RewardStore(folder.resolve("daily.db")); var second = new RewardStore(folder.resolve("daily.db"))) {
            List<CompletableFuture<RewardStore.Claim>> calls = new ArrayList<>();
            for (int i = 0; i < 40; i++) calls.add((i % 2 == 0 ? first : second).claim(uuid, 1, () -> TODAY));
            CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();
            assertEquals(1, calls.stream().map(CompletableFuture::join).filter(RewardStore.Claim::accepted).count());
            assertEquals(new DailyProgress(TODAY, 1), first.load(uuid).join());
        }
    }
    @Test void restartKeepsUuidProgressAndDoesNotMixPlayers() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path path = folder.resolve("daily.db");
        try (var store = new RewardStore(path)) { assertTrue(store.claim(uuid, 1, () -> TODAY).join().accepted()); }
        try (var store = new RewardStore(path)) {
            assertFalse(store.claim(uuid, 1, () -> TODAY).join().accepted());
            assertEquals(DailyProgress.EMPTY, store.load(UUID.randomUUID()).join());
            assertTrue(store.claim(uuid, 2, () -> TODAY.plusDays(1)).join().accepted());
            assertFalse(store.claim(uuid, 2, () -> TODAY.plusDays(1)).join().accepted());
        }
    }
    @Test void fullCycleResetAndMissingDayArePersisted() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (var store = new RewardStore(folder.resolve("daily.db"))) {
            for (int day = 1; day <= 31; day++) {
                LocalDate date = TODAY.plusDays(day - 1);
                assertTrue(store.claim(uuid, day, () -> date).join().accepted());
            }
            assertFalse(store.claim(uuid, 1, () -> TODAY.plusDays(30)).join().accepted());
            assertTrue(store.claim(uuid, 1, () -> TODAY.plusDays(31)).join().accepted());
            assertFalse(store.claim(uuid, 2, () -> TODAY.plusDays(33)).join().accepted());
            assertTrue(store.claim(uuid, 1, () -> TODAY.plusDays(33)).join().accepted());
        }
    }
    @Test void outOfSequenceAndInvalidDaysDoNotConsumeDay() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (var store = new RewardStore(folder.resolve("daily.db"))) {
            for (int day : new int[]{0, 2, 31, 32}) assertFalse(store.claim(uuid, day, () -> TODAY).join().accepted());
            assertTrue(store.claim(uuid, 1, () -> TODAY).join().accepted());
        }
    }
    @Test void queuedClaimUsesDateSupplierWhenTransactionExecutes() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (var store = new RewardStore(folder.resolve("daily.db"))) {
            assertTrue(store.claim(uuid, 1, () -> TODAY).join().accepted());
            assertTrue(store.claim(uuid, 2, () -> TODAY.plusDays(1)).join().accepted());
            assertFalse(store.claim(uuid, 3, () -> TODAY.plusDays(1)).join().accepted());
        }
    }
}
