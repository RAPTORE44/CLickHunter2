package fr.cliker.clikerv2.features;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.lootbox.LootboxItem;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;

import java.util.*;

class ChatActivitiesTest {
    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void answersIgnoreCaseAccentsSpacingAndArticles() {
        assertEquals("francais", ChatActivities.normalize("Le FRANÇAIS !"));
        assertEquals(
                "tabledEnchantement".toLowerCase(),
                ChatActivities.normalize("Table d’enchantement"));
        assertEquals("-12", ChatActivities.normalize("-12"));
    }

    @Test
    void twoCorrectAnswersOnlyPersistOneDivineRewardForFirstPlayer() throws Exception {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        ConfigurationSerialization.registerClass(ItemStackMock.class);
        LootboxItem.init(plugin);
        var database = new DatabaseManager(plugin, "chat-" + UUID.randomUUID() + ".db");
        database.initialize();
        var claims = ClaimTestFactory.create(plugin, database);
        server.getServicesManager()
                .register(
                        ClaimService.class,
                        claims,
                        plugin,
                        org.bukkit.plugin.ServicePriority.Normal);
        var activities = new ChatActivities(plugin);
        try {
            var winner = server.addPlayer();
            var other = server.addPlayer();
            activities.start("calcul");
            var question = activities.currentQuestion();
            assertNotNull(question);
            String answer = question.answers().iterator().next();
            activities.answer(winner, answer);
            activities.answer(other, answer);
            database.submit(c -> null).join();
            server.getScheduler().performTicks(3);
            database.submit(c -> null).join();
            var rows =
                    database.submit(
                                    c -> {
                                        try (var s = c.createStatement();
                                                var r =
                                                        s.executeQuery(
                                                                "SELECT owner,payload FROM"
                                                                    + " claim_items WHERE source"
                                                                    + " LIKE 'chat-game:%'")) {
                                            var result = new ArrayList<String>();
                                            while (r.next())
                                                result.add(r.getString(1) + " " + r.getString(2));
                                            return result;
                                        } catch (java.sql.SQLException error) {
                                            throw new RuntimeException(error);
                                        }
                                    })
                            .join();
            assertEquals(1, rows.size());
            assertTrue(rows.getFirst().startsWith(winner.getUniqueId().toString()));
            assertTrue(rows.getFirst().contains("divin"));
            assertNull(activities.currentQuestion());
        } finally {
            activities.shutdown();
            claims.shutdown();
            database.close();
        }
    }
}
