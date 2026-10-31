package fr.cliker.clikerv2.rankings;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.*;

class RankingPeriodTest {
    @Test
    void weeksStartMondayAndCrossYearsWithoutAnOffset() {
        assertEquals(
                LocalDate.of(2025, 12, 29), RankingPeriod.WEEK.start(LocalDate.of(2026, 1, 1)));
        assertEquals(
                LocalDate.of(2026, 1, 5), RankingPeriod.WEEK.shift(LocalDate.of(2026, 1, 1), 1));
        assertEquals("29/12/2025 — 04/01/2026", RankingPeriod.WEEK.range(LocalDate.of(2026, 1, 1)));
    }

    @Test
    void monthsAndYearsRespectLeapYears() {
        assertEquals(
                "01/02/2024 — 29/02/2024", RankingPeriod.MONTH.range(LocalDate.of(2024, 2, 29)));
        assertEquals(
                LocalDate.of(2024, 3, 1), RankingPeriod.MONTH.shift(LocalDate.of(2024, 2, 29), 1));
        assertEquals(
                "01/01/2024 — 31/12/2024", RankingPeriod.YEAR.range(LocalDate.of(2024, 2, 29)));
        assertEquals(
                LocalDate.of(2023, 1, 1), RankingPeriod.YEAR.shift(LocalDate.of(2024, 2, 29), -1));
    }

    @Test
    void navigationAllowsTheFirstPartialPeriodButNotMissingHistoryOrFutureDates() {
        LocalDate since = LocalDate.of(2024, 2, 15), today = LocalDate.of(2024, 3, 3);
        assertTrue(RankingPeriod.MONTH.available(since, since, today));
        assertFalse(RankingPeriod.MONTH.available(since.minusMonths(1), since, today));
        assertFalse(RankingPeriod.DAY.available(today.plusDays(1), since, today));
        assertFalse(RankingPeriod.WEEK.available(today.plusWeeks(1), since, today));
        assertTrue(RankingPeriod.DAY.available(since, since, today));
        assertEquals("Depuis toujours", RankingPeriod.ALL.range(today));
    }
}
