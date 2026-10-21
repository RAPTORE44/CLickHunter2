package fr.cliker.clikerv2.bag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** GUI /sac : stockage persistant, capacité selon permission cliker.sac.&lt;N&gt;. Talismans actifs tant qu'ils sont dedans. */
public final class BagMenu implements Listener {
    private static final int SIZE = 9;

    private final BagManager bagManager;
    private final NamespacedKey lockedKey;

    public BagMenu(JavaPlugin plugin, BagManager bagManager) {
        this.bagManager = bagManager;
        this.lockedKey = new NamespacedKey(plugin, "cliker_sac_locked");
    }

    public void open(Player player) {
        int capacity = bagManager.capacity(player);
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("Sac (" + capacity + "/" + SIZE + ")", NamedTextColor.GOLD, TextDecoration.BOLD));
        holder.inventory = inventory;

        List<ItemStack> stored = bagManager.items(player.getUniqueId());
        for (int i = 0; i < stored.size() && i < SIZE; i++) inventory.setItem(i, stored.get(i));

        for (int slot = capacity; slot < SIZE; slot++) {
            if (inventory.getItem(slot) != null) continue;
            inventory.setItem(slot, locked(slot + 1));
        }

        player.openInventory(inventory);
    }

    private ItemStack locked(int neededLevel) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Emplacement verrouillé", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Nécessite la permission cliker.sac." + neededLevel, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(lockedKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isLocked(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(lockedKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        boolean topClicked = event.getClickedInventory() == event.getView().getTopInventory();
        if (topClicked && isLocked(event.getCurrentItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            ItemStack item = holder.inventory.getItem(i);
            if (item != null && !isLocked(item)) items.add(item);
        }
        bagManager.setItems(holder.uuid, items);
    }

    private static final class Holder implements InventoryHolder {
        private final UUID uuid;
        private Inventory inventory;
        private Holder(UUID uuid) { this.uuid = uuid; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
