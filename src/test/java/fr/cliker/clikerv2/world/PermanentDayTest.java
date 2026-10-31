package fr.cliker.clikerv2.world;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.GameRule;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class PermanentDayTest {
    private ServerMock server;
    private PermanentDay day;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void close() {
        MockBukkit.unmock();
    }

    @Test
    void existingAndLaterWorldsStayAtNoon() {
        var first = server.addSimpleWorld("world");
        first.setTime(18000);
        day = new PermanentDay(MockBukkit.createMockPlugin());
        assertEquals(6000, first.getTime());
        assertEquals(false, first.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE));
        var later = server.addSimpleWorld("later");
        later.setTime(18000);
        day.load(new WorldLoadEvent(later));
        assertEquals(6000, later.getTime());
        assertEquals(false, later.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE));
    }

    @Test
    void nightChangesAreBlockedButCalendarDayAdvancesAreAllowed() {
        var world = server.addSimpleWorld("world");
        day = new PermanentDay(MockBukkit.createMockPlugin());
        for (var reason : TimeSkipEvent.SkipReason.values()) {
            var night = new TimeSkipEvent(world, reason, 12000);
            day.skip(night);
            assertTrue(night.isCancelled());
        }
        var nextDay = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.CUSTOM, 24000);
        day.skip(nextDay);
        assertFalse(nextDay.isCancelled());
    }
}
