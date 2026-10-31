package fr.cliker.clikerv2.staff;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class StaffInventoriesTest {
    @Test
    void permissionIsRequiredAndViewUsesRealInventoryInsteadOfCopies() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var staff = server.addPlayer();
        var target = server.addPlayer();
        var module = new StaffInventories(plugin);
        module.start();
        var command =
                new Command("inv") {
                    public boolean execute(CommandSender s, String l, String[] a) {
                        return false;
                    }
                };
        try {
            module.onCommand(staff, command, "inv", new String[] {target.getName()});
            assertNotSame(target.getInventory(), staff.getOpenInventory().getTopInventory());
            staff.addAttachment(plugin, "cliker.staff.inv", true);
            module.onCommand(staff, command, "inv", new String[] {target.getName()});
            assertSame(target.getInventory(), staff.getOpenInventory().getTopInventory());
            staff.getOpenInventory()
                    .getTopInventory()
                    .setItem(5, new ItemStack(Material.DIAMOND, 3));
            assertEquals(3, target.getInventory().getItem(5).getAmount());
            target.getInventory().setItem(5, new ItemStack(Material.EMERALD, 2));
            assertEquals(
                    Material.EMERALD,
                    staff.getOpenInventory().getTopInventory().getItem(5).getType());
        } finally {
            module.shutdown();
            MockBukkit.unmock();
        }
    }
}
