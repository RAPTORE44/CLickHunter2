package fr.cliker.clikerv2.comfort;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.hunting.Gains;

import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

class FarmTelemetryTest {
    private FarmTelemetry telemetry;
    private PlayerMock player;

    @BeforeEach
    void setup() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        telemetry = new FarmTelemetry(plugin);
        player = server.addPlayer();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void gainsAreGrossConfirmedAmountsAndClickCounterSurvivesSessionCleanup() {
        telemetry.hit(player, UUID.randomUUID());
        telemetry.confirmedKill(player, MobTier.VACHE, false, Gains.of(50, 25, 2, 7), 0);
        var text = telemetry.lore(player).toString();
        assertTrue(text.contains("50 Écus"));
        assertTrue(text.contains("2.5 Gemmes"));
        assertTrue(text.contains("7 XP"));
        assertTrue(text.contains("total : 1"));
        telemetry.quit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        assertTrue(telemetry.lore(player).toString().contains("total : 1"));
    }

    @Test
    void minuteWindowExpiresWithoutExtrapolatingNewPlayersGains() {
        long[] clock = {0};
        telemetry = new FarmTelemetry(MockBukkit.createMockPlugin(), () -> clock[0]);
        telemetry.confirmedKill(player, MobTier.VACHE, false, Gains.of(50, 0, 0, 0), 0);
        assertTrue(telemetry.lore(player).toString().contains("50 Écus"));
        clock[0] = 60_000_000_000L;
        assertFalse(telemetry.lore(player).toString().contains("50 Écus"));
        assertTrue(telemetry.lore(player).toString().contains("Aucune élimination récente"));
    }

    @Test
    void finishingFightRemovesItsTimer() {
        UUID victim = UUID.randomUUID();
        telemetry.hit(player, victim);
        assertTrue(telemetry.finishFight(victim) >= 1);
        assertEquals(0, telemetry.finishFight(victim));
    }
}
