package fr.cliker.clikerv2.display;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.mobs.MobKillStore;
import fr.cliker.clikerv2.prestige.*;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;

class MoneyScoreboardTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager database;
    private MoneyScoreboard scoreboard;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        MoneyScoreboard.installStyle(plugin);
        database = new DatabaseManager(plugin, "board-" + java.util.UUID.randomUUID() + ".db");
        database.initialize();
    }

    @AfterEach
    void cleanup() {
        try {
            if (scoreboard != null) scoreboard.shutdown();
            if (database != null) database.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void formatsActualCurrenciesIncludingTenthsOfGems() {
        var values =
                MoneyScoreboard.values(
                        "RAPTORE44", 25, 4, new EconomyService.Balances(1234, 185, 50), 42L, 3);
        assertEquals("1.2K", values.get("ecus"));
        assertEquals("18.5", values.get("gemmes"));
        assertEquals("50", values.get("cendres"));
        assertEquals("IV", values.get("prestige"));
        assertEquals("25", values.get("niveau"));
        assertEquals("3", values.get("connectes"));
    }

    @Test
    void doesNotShowFakeZeroWhileBalancesAreLoading() {
        var values = MoneyScoreboard.values("Joueur", 1, 0, null, null, 1);
        assertEquals("…", values.get("ecus"));
        assertEquals("…", values.get("gemmes"));
        assertEquals("…", values.get("kills"));
    }

    @Test
    void activationIsOnceAndLaterDisablingIsRespected() {
        assertTrue(plugin.getConfig().getBoolean("displays.scoreboard.enabled"));
        plugin.getConfig().set("displays.scoreboard.enabled", false);
        plugin.saveConfig();
        MoneyScoreboard.installStyle(plugin);
        assertFalse(plugin.getConfig().getBoolean("displays.scoreboard.enabled"));
    }

    @Test
    void sidebarUsesGreenBrandRealBalancesAndRestoresPreviousBoard() throws Exception {
        PlayerMock player = server.addPlayer();
        var previous = player.getScoreboard();
        var economy = new EconomyService(database, Map.of(CurrencyType.ECUS, 0L));
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.ECUS, 12000).join();
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.GEMMES_SOMBRES, 185)
                .join();
        economy.set(player.getUniqueId(), player.getName(), CurrencyType.CENDRES, 90).join();
        var kills = new MobKillStore(database);
        kills.add(player.getUniqueId(), 42).join();
        var prestige = new PrestigeManager(plugin, new PrestigeStore(database), kills);
        // ScoreMock ne gère pas NumberFormat ; capturer la demande de masquage à la frontière
        // Paper.
        var formats = new java.util.concurrent.atomic.AtomicInteger();
        scoreboard =
                new MoneyScoreboard(
                        plugin, economy, kills, prestige, score -> formats.incrementAndGet());
        scoreboard.start();
        server.getScheduler().performOneTick();
        database.submit(c -> null).join();
        server.getScheduler().performOneTick();
        var board = player.getScoreboard();
        var objective = board.getObjective("cliker_money");
        assertEquals(
                "ClickHunter",
                PlainTextComponentSerializer.plainText().serialize(objective.displayName()));
        assertEquals(NamedTextColor.GREEN, objective.displayName().color());
        assertTrue(
                PlainTextComponentSerializer.plainText()
                        .serialize(board.getTeam("cliker_line_5").prefix())
                        .contains("18.5"));
        assertEquals(
                "play.clickhunter.fr",
                PlainTextComponentSerializer.plainText()
                        .serialize(board.getTeam("cliker_line_14").prefix()));
        assertEquals(16, board.getTeams().size());
        assertEquals(
                org.bukkit.scoreboard.Team.OptionStatus.NEVER,
                board.getTeam("cliker_no_push")
                        .getOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE));
        assertTrue(board.getTeam("cliker_no_push").hasEntry(player.getName()));
        assertEquals(15, formats.get());
        scoreboard.shutdown();
        assertSame(previous, player.getScoreboard());
    }
}
