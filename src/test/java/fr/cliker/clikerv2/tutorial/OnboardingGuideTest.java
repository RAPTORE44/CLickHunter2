package fr.cliker.clikerv2.tutorial;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.features.HuntKillEvent;

import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class OnboardingGuideTest {
    ServerMock server;
    OnboardingGuide guide;
    org.mockbukkit.mockbukkit.entity.PlayerMock player;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        guide = new OnboardingGuide(MockBukkit.createMockPlugin());
        guide.start();
        player = server.addPlayer();
    }

    @AfterEach
    void end() {
        guide.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void stepsCanBeCompletedOutOfOrderWithoutRepeating() {
        OnboardingGuide.record(player, OnboardingGuide.Action.DAILY);
        OnboardingGuide.record(player, OnboardingGuide.Action.DAILY);
        assertEquals(16, guide.progress(player));
        OnboardingGuide.record(player, OnboardingGuide.Action.MENU);
        assertEquals(17, guide.progress(player));
    }

    @Test
    void fiveConfirmedOrdinaryKillsValidateHuntingButBossDoesNot() {
        var tier = MobTier.values()[0];
        guide.hunt(new HuntKillEvent(player.getUniqueId(), player.getName(), tier, true));
        for (int i = 0; i < 4; i++)
            guide.hunt(new HuntKillEvent(player.getUniqueId(), player.getName(), tier, false));
        assertEquals(0, guide.progress(player));
        guide.hunt(new HuntKillEvent(player.getUniqueId(), player.getName(), tier, false));
        assertEquals(2, guide.progress(player));
    }
}
