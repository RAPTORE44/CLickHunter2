package fr.cliker.clikerv2.raid;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.hunting.Gains;

import org.junit.jupiter.api.Test;

import java.util.*;

class ProportionalRewardsTest {
    private final UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);

    @Test
    void exactSharesAndTiesAreStable() {
        assertEquals(
                Map.of(a, 75L, b, 25L), ProportionalRewards.split(100, Map.of(a, 75L, b, 25L)));
        assertEquals(
                Map.of(a, 1L, b, 1L, c, 0L),
                ProportionalRewards.split(2, Map.of(a, 1L, b, 1L, c, 1L)));
    }

    @Test
    void zeroParticipantsAndZeroDamageReceiveNothing() {
        assertTrue(ProportionalRewards.split(100, Map.of()).isEmpty());
        assertEquals(Map.of(a, 100L), ProportionalRewards.split(100, Map.of(a, 50L, b, 0L)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProportionalRewards.split(100, Map.of(a, -1L)));
    }

    @Test
    void hugeNumbersAndAllCurrenciesPreserveEntirePool() {
        Gains pool = Gains.of(Long.MAX_VALUE, 1001, 503, 701);
        Map<UUID, Gains> shares =
                ProportionalRewards.allocate(
                        pool, Map.of(a, Long.MAX_VALUE, b, Long.MAX_VALUE, c, 1L));
        for (var currency : CurrencyType.values())
            assertEquals(
                    pool.amount(currency),
                    shares.values().stream().mapToLong(g -> g.amount(currency)).sum());
        assertEquals(pool.xp(), shares.values().stream().mapToLong(Gains::xp).sum());
    }

    @Test
    void randomizedDistributionNeverExceedsPoolAndFollowsDamage() {
        Random random = new Random(42);
        for (int i = 0; i < 500; i++) {
            long pool = random.nextInt(100000);
            long da = 1 + random.nextInt(100000), db = 1 + random.nextInt(100000);
            var result = ProportionalRewards.split(pool, Map.of(a, da, b, db));
            assertEquals(pool, result.values().stream().mapToLong(Long::longValue).sum());
            assertTrue(Math.abs(result.get(a) - (double) pool * da / (da + db)) < 1.01);
        }
    }
}
