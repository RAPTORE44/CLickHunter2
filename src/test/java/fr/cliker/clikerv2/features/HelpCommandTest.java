package fr.cliker.clikerv2.features;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class HelpCommandTest {
    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void tabCompletionShowsOnlyCommandsThePlayerCanUse() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var player = server.addPlayer();
        player.addAttachment(plugin, "cliker.navigation.use", false);
        var help = new HelpCommand();
        var command =
                new Command("aide") {
                    @Override
                    public boolean execute(
                            org.bukkit.command.CommandSender sender, String label, String[] args) {
                        return true;
                    }
                };

        var hidden = help.onTabComplete(player, command, "aide", new String[] {"wa"});
        assertTrue(hidden.isEmpty());

        player.addAttachment(plugin, "cliker.navigation.use", true);
        var visible = help.onTabComplete(player, command, "aide", new String[] {"wa"});
        assertEquals(java.util.List.of("warp"), visible);
    }
}
