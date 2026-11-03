package fr.cliker.clikerv2.pinata;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.entity.Llama;
import org.bukkit.inventory.LlamaInventory;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

class PinataAppearanceTest {
    org.mockbukkit.mockbukkit.ServerMock server;
    Llama llama;
    AtomicLong now = new AtomicLong();
    PinataAppearance appearance;
    int skins, decorations;
    boolean glowing;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        var world = server.addSimpleWorld("test");
        UUID id = UUID.randomUUID();
        var inventory =
                (LlamaInventory)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {LlamaInventory.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("setDecor")) {
                                        decorations++;
                                        return null;
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });
        llama =
                (Llama)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {Llama.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "isValid" -> true;
                                            case "getWorld" -> world;
                                            case "getLocation" -> new Location(world, 0, 65, 0);
                                            case "getUniqueId" -> id;
                                            case "getInventory" -> inventory;
                                            case "setAdult" -> null;
                                            case "setGlowing" -> {
                                                glowing = (boolean) args[0];
                                                yield null;
                                            }
                                            case "setColor" -> {
                                                skins++;
                                                yield null;
                                            }
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        appearance = new PinataAppearance(now::get);
    }

    @AfterEach
    void close() {
        appearance.clear();
        MockBukkit.unmock();
    }

    @Test
    void globalThrottleChangesSkinAndDecorOncePerHalfSecondRegardlessOfHitCount() {
        appearance.attach(llama);
        skins = 0;
        decorations = 0;
        assertTrue(appearance.hit());
        for (int i = 0; i < 100; i++) {
            now.addAndGet(4_000_000);
            assertFalse(appearance.hit());
        }
        now.set(499_999_999);
        assertFalse(appearance.hit());
        assertEquals(1, skins);
        assertEquals(1, decorations);
        now.set(500_000_000);
        assertTrue(appearance.hit());
        assertEquals(2, skins);
        assertEquals(2, decorations);
    }

    @Test
    void personalScoreboardsReceiveGlowAndAreCleanedWhenPlayersLeave() {
        var player = server.addPlayer();
        player.teleport(llama.getLocation());
        var board = server.getScoreboardManager().getNewScoreboard();
        player.setScoreboard(board);
        var existing = board.registerNewTeam("other_plugin");
        appearance.attach(llama);
        assertTrue(glowing);
        String name = "chpn_" + llama.getUniqueId().toString().substring(0, 8);
        var team = board.getTeam(name);
        assertNotNull(team);
        assertTrue(team.hasEntry(llama.getUniqueId().toString()));
        var first = team.color();
        appearance.hit();
        assertNotEquals(first, team.color());
        var replacement = server.getScoreboardManager().getNewScoreboard();
        player.setScoreboard(replacement);
        now.addAndGet(1_000_000_000L);
        appearance.viewers();
        assertNull(board.getTeam(name));
        assertNotNull(replacement.getTeam(name));
        appearance.clear();
        assertNull(replacement.getTeam(name));
        assertSame(existing, board.getTeam("other_plugin"));
    }
}
