package fr.cliker.clikerv2.daily;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.HashSet;

class DailyProgressTest {
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Test
    void firstVisitStartsAtOne() {
        assertEquals(1, DailyProgress.EMPTY.currentDay(TODAY));
        assertEquals(DailyProgress.Status.AVAILABLE, DailyProgress.EMPTY.status(1, TODAY));
        assertEquals(DailyProgress.Status.LOCKED, DailyProgress.EMPTY.status(31, TODAY));
    }

    @Test
    void midnightUnlocksWithoutWaiting24Hours() {
        var before = ZonedDateTime.of(2026, 10, 21, 23, 59, 0, 0, PARIS);
        var after = before.plusMinutes(2);
        var progress = new DailyProgress(before.toLocalDate(), 7);
        assertTrue(progress.claimedToday(before.toLocalDate()));
        assertFalse(progress.claimedToday(after.toLocalDate()));
        assertEquals(8, progress.currentDay(after.toLocalDate()));
        assertEquals(Duration.ofMinutes(1), progress.untilUnlock(8, before));
    }

    @Test
    void missingFullCalendarDayResetsStreak() {
        var progress = new DailyProgress(TODAY.minusDays(2), 20);
        assertEquals(1, progress.currentDay(TODAY));
        assertEquals(DailyProgress.Status.AVAILABLE, progress.status(1, TODAY));
        assertEquals(DailyProgress.Status.LOCKED, progress.status(20, TODAY));
    }

    @Test
    void connectingWithoutClaimDoesNotAdvanceAnything() {
        var progress = new DailyProgress(TODAY.minusDays(1), 3);
        assertEquals(4, progress.currentDay(TODAY));
        assertEquals(4, progress.currentDay(TODAY));
        assertEquals(1, progress.currentDay(TODAY.plusDays(1)));
    }

    @Test
    void claimedTodayCannotBeClaimedAgain() {
        var progress = new DailyProgress(TODAY, 4);
        for (int day = 1; day <= 4; day++)
            assertEquals(DailyProgress.Status.CLAIMED, progress.status(day, TODAY));
        assertEquals(DailyProgress.Status.LOCKED, progress.status(5, TODAY));
    }

    @Test
    void day31StartsNewCycleOnlyNextDay() {
        var progress = new DailyProgress(TODAY, 31);
        assertEquals(DailyProgress.Status.CLAIMED, progress.status(31, TODAY));
        assertEquals(1, progress.currentDay(TODAY.plusDays(1)));
        assertEquals(DailyProgress.Status.AVAILABLE, progress.status(1, TODAY.plusDays(1)));
        assertEquals(DailyProgress.Status.LOCKED, progress.status(31, TODAY.plusDays(1)));
    }

    @Test
    void daylightSavingUsesCalendarMidnightNotFixedDuration() {
        var spring = LocalDate.of(2026, 3, 29).atStartOfDay(PARIS);
        var autumn = LocalDate.of(2026, 10, 25).atStartOfDay(PARIS);
        assertEquals(
                Duration.ofHours(23),
                new DailyProgress(spring.toLocalDate(), 1).untilUnlock(2, spring));
        assertEquals(
                Duration.ofHours(25),
                new DailyProgress(autumn.toLocalDate(), 1).untilUnlock(2, autumn));
    }

    @Test
    void futureUnlockDependsOnContinuingStreak() {
        var noon = TODAY.atTime(12, 0).atZone(PARIS);
        assertEquals(Duration.ofHours(36), DailyProgress.EMPTY.untilUnlock(3, noon));
    }

    @Test
    void backwardClockCannotClaimAgain() {
        var progress = new DailyProgress(TODAY.plusDays(1), 9);
        assertTrue(progress.claimedToday(TODAY));
        assertEquals(DailyProgress.Status.CLAIMED, progress.status(9, TODAY));
    }

    @Test
    void exactly31SlotsInsideCentralColumns() {
        var slots = new HashSet<Integer>();
        for (int day = 1; day <= 31; day++) {
            int slot = DailyMenu.slot(day);
            assertTrue(slot >= 9 && slot < 54);
            assertTrue(slot % 9 >= 1 && slot % 9 <= 7);
            assertTrue(slots.add(slot));
            assertEquals(day, DailyMenu.day(slot));
        }
        assertEquals(31, slots.size());
        assertEquals(-1, DailyMenu.day(4));
        assertEquals(-1, DailyMenu.day(54));
    }
}
