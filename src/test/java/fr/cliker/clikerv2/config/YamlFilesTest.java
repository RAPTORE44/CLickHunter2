package fr.cliker.clikerv2.config;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.*;

class YamlFilesTest {
    @Test
    void invalidCoordinatesAreIgnoredWithoutOverwritingConfiguration() {
        var server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("world");
            var yaml = new org.bukkit.configuration.file.YamlConfiguration();
            YamlFiles.location(yaml, "point", new Location(world, 4, 65, 8));
            for (Object invalid : new Object[] {"oops", Double.NaN, Double.POSITIVE_INFINITY}) {
                yaml.set("point.x", invalid);
                assertNull(YamlFiles.location(yaml, "point"));
                assertEquals(invalid, yaml.get("point.x"));
            }
            yaml.set("point.x", 4);
            yaml.set("point.yaw", Double.MAX_VALUE);
            assertNull(
                    YamlFiles.location(yaml, "point"),
                    "Overflow when converting a finite double to float must be rejected");
            yaml.set("point.yaw", 0);
            assertEquals(new Location(world, 4, 65, 8), YamlFiles.location(yaml, "point"));
            String before = yaml.saveToString();
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            YamlFiles.location(
                                    yaml, "point", new Location(world, Double.NaN, 65, 8)));
            assertEquals(before, yaml.saveToString());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void unloadedLegacyLocationPreservesEntireConfigurationUntilWorldArrives() throws Exception {
        var server = MockBukkit.mock();
        try {
            var file = Files.createTempFile("npc-legacy-", ".yml");
            Files.writeString(
                    file,
                    "messages:\n"
                            + "  bonjour: '&aSalut'\n"
                            + "pnj:\n"
                            + "  actif: true\n"
                            + "  position:\n"
                            + "    ==: org.bukkit.Location\n"
                            + "    world: later\n"
                            + "    x: 1.5\n"
                            + "    y: 65\n"
                            + "    z: 2.5\n"
                            + "    yaw: 90\n");
            var yaml = YamlFiles.load(file.toFile());
            assertTrue(yaml.getBoolean("pnj.actif"));
            assertEquals("&aSalut", yaml.getString("messages.bonjour"));
            assertNull(YamlFiles.location(yaml, "pnj.position"));
            var world = server.addSimpleWorld("later");
            var location = YamlFiles.location(yaml, "pnj.position");
            assertEquals(world, location.getWorld());
            assertEquals(1.5, location.getX());
            assertEquals(90, location.getYaw());
            YamlFiles.location(yaml, "pnj.position", location);
            yaml.save(file.toFile());
            assertFalse(Files.readString(file).contains("==:"));
            assertEquals(
                    location, YamlFiles.location(YamlFiles.load(file.toFile()), "pnj.position"));
            Files.delete(file);
        } finally {
            MockBukkit.unmock();
        }
    }
}
