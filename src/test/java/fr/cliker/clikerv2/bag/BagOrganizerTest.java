package fr.cliker.clikerv2.bag;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class BagOrganizerTest {
    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void sortingConservesItemsMetadataAndOriginalSnapshot() {
        MockBukkit.mock();
        var items = new ItemStack[54];
        items[0] = new ItemStack(Material.STONE, 40);
        items[1] = new ItemStack(Material.STONE, 40);
        var named = new ItemStack(Material.STONE, 3);
        var meta = named.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Spécial"));
        named.setItemMeta(meta);
        items[2] = named;
        var sorted = BagOrganizer.sorted(items);
        assertEquals(
                83,
                Arrays.stream(sorted)
                        .filter(Objects::nonNull)
                        .mapToInt(ItemStack::getAmount)
                        .sum());
        assertEquals(3, Arrays.stream(sorted).filter(Objects::nonNull).count());
        assertEquals(
                3,
                Arrays.stream(sorted)
                        .filter(i -> i != null && i.getItemMeta().hasDisplayName())
                        .mapToInt(ItemStack::getAmount)
                        .sum());
        assertEquals(40, items[0].getAmount());
        assertEquals(40, items[1].getAmount());
    }

    @Test
    void insertionMovesOnlyCapacityAndLeavesSourceUnchanged() {
        MockBukkit.mock();
        var slots = new ItemStack[2];
        slots[0] = new ItemStack(Material.STONE, 63);
        slots[1] = new ItemStack(Material.DIAMOND, 64);
        var source = new ItemStack(Material.STONE, 12);
        assertEquals(11, BagOrganizer.insert(slots, source));
        assertEquals(64, slots[0].getAmount());
        assertEquals(12, source.getAmount());
        assertEquals(64, slots[1].getAmount());
    }
}
