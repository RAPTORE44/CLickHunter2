package fr.cliker.clikerv2.raid;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.hunting.Gains;

import org.junit.jupiter.api.Test;

import java.util.*;

class PublicRaidRewardsTest {
    @Test
    void onlyContributorsEarnAndHelpersGetOneThird() {
        UUID leader = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        var base = Gains.of(300, 30, 3, 600);
        var result =
                PublicRaidRewards.allocate(base, leader, Set.of(a, b), Map.of(leader, 1L, a, 5L));
        assertEquals(base, result.get(leader));
        assertEquals(Gains.of(100, 10, 1, 200), result.get(a));
        assertFalse(result.containsKey(b));
    }

    @Test
    void aFourthHelperCannotIncreaseThePayout() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PublicRaidRewards.allocate(
                                Gains.of(3, 3, 3, 3),
                                UUID.randomUUID(),
                                Set.of(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID()),
                                Map.of()));
    }
}
