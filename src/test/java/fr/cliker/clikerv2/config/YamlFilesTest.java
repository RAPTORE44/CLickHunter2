package fr.cliker.clikerv2.config;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.*;

class YamlFilesTest {
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
