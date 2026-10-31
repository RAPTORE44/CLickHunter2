package fr.cliker.clikerv2.staff;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.command.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class StaffToolsTest {
    ServerMock server;
    org.mockbukkit.mockbukkit.entity.PlayerMock player;
    StaffTools tools;
    org.bukkit.plugin.java.JavaPlugin plugin;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player =
                new org.mockbukkit.mockbukkit.entity.PlayerMock(server, "StaffTester") {
                    private float speed = .1f;

                    @Override
                    public float getFlySpeed() {
                        return speed;
                    }

                    @Override
                    public void setFlySpeed(float value) {
                        speed = value;
                    }
                };
        server.addPlayer(player);
        tools = new StaffTools(plugin);
        tools.start();
    }

    @AfterEach
    void end() {
        tools.shutdown();
        MockBukkit.unmock();
    }

    void cmd(String name, String... args) {
        tools.onCommand(
                player,
                new Command(name) {
                    public boolean execute(CommandSender s, String l, String[] a) {
                        return false;
                    }
                },
                name,
                args);
    }

    @Test
    void speedNeedsPermissionAndRejectsNonFiniteOrOutOfRangeValues() {
        float initial = player.getFlySpeed();
        cmd("flyspeed", "5");
        assertEquals(initial, player.getFlySpeed());
        player.addAttachment(plugin, "cliker.staff.flyspeed", true);
        cmd("flyspeed", "NaN");
        cmd("flyspeed", "11");
        assertEquals(initial, player.getFlySpeed());
        cmd("flyspeed", "5");
        assertEquals(.5f, player.getFlySpeed());
    }

    @Test
    @SuppressWarnings("deprecation")
    void godTogglesDamageProtection() {
        player.addAttachment(plugin, "cliker.staff.god", true);
        cmd("god");
        var hit = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FIRE, 5);
        tools.damage(hit);
        assertTrue(hit.isCancelled());
        cmd("god");
        var second = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FIRE, 5);
        tools.damage(second);
        assertFalse(second.isCancelled());
    }

    @Test
    void targetingAnotherPlayerRequiresOthersPermission() {
        player.addAttachment(plugin, "cliker.staff.feed", true);
        var other = server.addPlayer();
        other.setFoodLevel(2);
        cmd("feed", other.getName());
        assertEquals(2, other.getFoodLevel());
        player.addAttachment(plugin, "cliker.staff.others", true);
        cmd("feed", other.getName());
        assertEquals(20, other.getFoodLevel());
    }
}
