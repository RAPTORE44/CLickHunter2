package fr.cliker.clikerv2.pinata;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PinataDisplayTest {
    @Test
    void removesLegacyPlaceholdersAndLiteralCountdownsForAllCurrencies() {
        for (String reward : java.util.List.of("Écus", "XP", "Gemmes Sombres")) {
            String title = PinataModule.withoutTimer("&aPiñata " + reward + " {secondes}s");
            assertEquals("&aPiñata " + reward, title);
            assertEquals(
                    "Piñata " + reward,
                    PinataModule.withoutTimer("Piñata " + reward + " 180 secondes"));
            assertFalse(PinataModule.withoutTimer("{seconds} seconds").contains("second"));
        }
        assertEquals(
                "+25 Écus | Total : 125",
                PinataModule.withoutTimer("+25 Écus | Total : 125 {temps}s"));
    }
}
