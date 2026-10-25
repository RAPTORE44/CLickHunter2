package fr.cliker.clikerv2.features;

import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Le matériel simple ne passe ni par les ateliers ClickHunter ni par les améliorations vanilla. */
public final class PlainGear implements Listener {
    private final NamespacedKey tag;

    public PlainGear(JavaPlugin plugin) {
        tag = new NamespacedKey(plugin, "plain_shop");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void mark(ItemStack item) {
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private boolean blocked(ItemStack i) {
        return i != null
                && i.hasItemMeta()
                && i.getItemMeta().getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void enchant(EnchantItemEvent e) {
        if (blocked(e.getItem())) {
            e.setCancelled(true);
            e.getEnchanter().sendMessage("L’équipement de la boutique n’est pas améliorable.");
        }
    }

    @EventHandler
    public void craft(PrepareItemCraftEvent e) {
        for (var item : e.getInventory().getMatrix())
            if (blocked(item)) {
                e.getInventory().setResult(null);
                return;
            }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void crafted(CraftItemEvent e) {
        for (var item : e.getInventory().getMatrix())
            if (blocked(item)) {
                e.setCancelled(true);
                return;
            }
    }

    @EventHandler
    public void anvil(PrepareAnvilEvent e) {
        if (blocked(e.getInventory().getItem(0)) || blocked(e.getInventory().getItem(1)))
            e.setResult(null);
    }

    @EventHandler
    public void smithing(PrepareSmithingEvent e) {
        for (var i : e.getInventory().getContents())
            if (blocked(i)) {
                e.setResult(null);
                return;
            }
    }

    @EventHandler
    public void grindstone(PrepareGrindstoneEvent e) {
        if (blocked(e.getInventory().getItem(0)) || blocked(e.getInventory().getItem(1)))
            e.setResult(null);
    }
}
