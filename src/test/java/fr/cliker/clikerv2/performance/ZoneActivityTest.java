package fr.cliker.clikerv2.performance;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class ZoneActivityTest {
    ServerMock server;
    ZoneActivity zones;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        zones = new ZoneActivity(MockBukkit.createMockPlugin());
        zones.start();
    }

    @AfterEach
    void end() {
        zones.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void emptyWorldsSleepAndDistanceIncludesWorldAndHeight() {
        var w = server.addSimpleWorld("world");
        var other = server.addSimpleWorld("other");
        assertFalse(ZoneActivity.active(new Location(w, 0, 65, 0)));
        var p = server.addPlayer();
        p.teleport(new Location(w, 0, 65, 0));
        zones.refresh();
        assertTrue(ZoneActivity.active(new Location(w, 63, 65, 0)));
        assertFalse(ZoneActivity.active(new Location(w, 65, 65, 0)));
        assertFalse(ZoneActivity.active(new Location(w, 0, 130, 0)));
        assertFalse(ZoneActivity.active(new Location(other, 0, 65, 0)));
        p.teleport(new Location(w, -130, 65, -130));
        zones.refresh();
        assertTrue(ZoneActivity.active(new Location(w, -150, 65, -150)));
        assertFalse(ZoneActivity.active(new Location(w, 0, 65, 0)));
    }
}
