package fr.cliker.clikerv2.donors;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.features.Settings;

import org.bukkit.command.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class DonorPodiumTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DonorPodium podium;
    private final Command command =
            new Command("donateurs") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return false;
                }
            };

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        podium = new DonorPodium(plugin);
        podium.start();
    }

    @AfterEach
    void cleanup() {
        try {
            podium.shutdown();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void defaultsAndConsoleUpdatesPersistWithoutMovingOtherRanks() {
        for (int rank = 1; rank <= 3; rank++) {
            var settings = new Settings(plugin, "pnj/donateurs-" + rank + ".yml");
            assertEquals("RAPTORE44", settings.yaml.getString("pnj.skin"));
            assertEquals(2.0, settings.yaml.getDouble("pnj.echelle"));
            assertTrue(settings.yaml.getString("pnj.titre").contains("donateur"));
            assertFalse(settings.yaml.getBoolean("pnj.actif"));
        }
        var settings = new Settings(plugin, "pnj/donateurs-2.yml");
        settings.yaml.set("pnj.position.x", 42);
        settings.save();
        // Relire le fichier comme lors d'un redémarrage, avant la commande.
        podium.shutdown();
        podium = new DonorPodium(plugin);
        podium.start();
        podium.onCommand(
                server.getConsoleSender(),
                command,
                "donateurs",
                new String[] {"set", "2", "Nati144378"});
        podium.shutdown();
        podium = new DonorPodium(plugin);
        podium.start();
        settings = new Settings(plugin, "pnj/donateurs-2.yml");
        assertEquals("Nati144378", settings.yaml.getString("joueur"));
        assertEquals("Nati144378", settings.yaml.getString("pnj.skin"));
        assertEquals("&fNati144378", settings.yaml.getString("pnj.sous-titre"));
        assertEquals(42, settings.yaml.getInt("pnj.position.x"));
        assertEquals(
                "RAPTORE44", new Settings(plugin, "pnj/donateurs-1.yml").yaml.getString("joueur"));
    }

    @Test
    void unauthorizedAndInvalidUpdatesCannotChangeThePodium() {
        var player = server.addPlayer();
        podium.onCommand(player, command, "donateurs", new String[] {"set", "1", "Other"});
        podium.onCommand(
                server.getConsoleSender(),
                command,
                "donateurs",
                new String[] {"set", "1", "bad-name!"});
        podium.onCommand(
                server.getConsoleSender(),
                command,
                "donateurs",
                new String[] {"set", "4", "Other"});
        assertEquals(
                "RAPTORE44", new Settings(plugin, "pnj/donateurs-1.yml").yaml.getString("joueur"));
        assertTrue(podium.onTabComplete(player, command, "donateurs", new String[] {""}).isEmpty());
    }
}
