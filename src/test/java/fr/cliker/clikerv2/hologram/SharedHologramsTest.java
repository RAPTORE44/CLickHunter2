package fr.cliker.clikerv2.hologram;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class SharedHologramsTest {
    public static class Api {
        final List<Holo> all = new ArrayList<>();

        public Holo createHologram(Location at) {
            var holo = new Holo(at);
            all.add(holo);
            return holo;
        }
    }

    public static class Lines {
        final List<String> text = new ArrayList<>();

        public void clear() {
            text.clear();
        }

        public Object appendText(String line) {
            text.add(line);
            return null;
        }
    }

    public static class Holo {
        final Lines lines = new Lines();
        Location at;
        boolean deleted;

        Holo(Location at) {
            this.at = at.clone();
        }

        public Lines getLines() {
            return lines;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public void setPosition(Location at) {
            this.at = at.clone();
        }

        public void delete() {
            if (deleted) throw new IllegalStateException("already deleted by provider");
            deleted = true;
        }
    }

    @AfterEach
    void cleanup() {
        if (SharedHolograms.service() != null) SharedHolograms.service().shutdown();
        MockBukkit.unmock();
    }

    @Test
    void adapterReplacesOnlyOwnedHandleUpdatesTextAndCleansEverything() throws Exception {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var world = server.addSimpleWorld("holo");
        var bridge = new SharedHolograms(plugin);
        var api = new Api();
        var field = SharedHolograms.class.getDeclaredField("api");
        field.setAccessible(true);
        field.set(bridge, api);
        var first =
                bridge.create(
                        "owned",
                        new Location(world, 0, 65, 0),
                        List.of("&aTitre", "Ligne"),
                        null,
                        false);
        assertTrue(first.isValid());
        assertEquals(2, api.all.getFirst().lines.text.size());
        first.setLines(List.of("Modifié"));
        assertEquals(List.of("Modifié"), api.all.getFirst().lines.text);
        first.setLocation(new Location(world, 0, 66, 0));
        assertEquals(66, api.all.getFirst().at.getY());
        var second =
                bridge.create(
                        "owned", new Location(world, 0, 70, 0), List.of("Nouveau"), null, false);
        assertFalse(first.isValid());
        assertTrue(api.all.getFirst().deleted);
        assertSame(second, bridge.get("owned"));
        api.all.getLast().delete();
        bridge.shutdown();
        assertTrue(api.all.getLast().deleted);
        assertNull(SharedHolograms.service());
    }
}
