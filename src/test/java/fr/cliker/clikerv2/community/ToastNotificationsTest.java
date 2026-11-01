package fr.cliker.clikerv2.community;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ToastNotificationsTest {
    @Test
    void notificationJsonEscapesPlayerTextAndDoesNotBroadcastOrUnlockVanilla() {
        var json =
                JsonParser.parseString(
                                ToastNotifications.json(
                                        new ToastNotifications.Notice(
                                                Material.PAPER,
                                                "Titre \"privé\"",
                                                "Ligne\nseconde")))
                        .getAsJsonObject();
        var display = json.getAsJsonObject("display");
        assertEquals("Titre \"privé\"", display.getAsJsonObject("title").get("text").getAsString());
        assertFalse(display.get("announce_to_chat").getAsBoolean());
        assertTrue(display.get("show_toast").getAsBoolean());
        assertEquals(
                "minecraft:impossible",
                json.getAsJsonObject("criteria")
                        .getAsJsonObject("notification")
                        .get("trigger")
                        .getAsString());
    }
}
