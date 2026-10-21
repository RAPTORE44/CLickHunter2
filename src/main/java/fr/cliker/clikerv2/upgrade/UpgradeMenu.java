package fr.cliker.clikerv2.upgrade;

import fr.cliker.clikerv2.Numbers;

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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class UpgradeMenu implements Listener {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 22};
    private static final Material[] MATERIALS = {
            Material.GOLD_INGOT, Material.EMERALD, Material.EXPERIENCE_BOTTLE,
            Material.DIAMOND_SWORD, Material.FEATHER, Material.APPLE, Material.SHIELD, Material.CLOCK
    };

    private final UpgradeManager manager;
    private final NamespacedKey typeKey;

    public UpgradeMenu(JavaPlugin plugin, UpgradeManager manager) {
        this.manager = manager;
        this.typeKey = new NamespacedKey(plugin, "cliker_upgrade_type");
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Upgrades", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        holder.inventory = inventory;

        UpgradeType[] types = UpgradeType.values();
        for (int i = 0; i < types.length; i++) inventory.setItem(SLOTS[i], buildItem(player, types[i], MATERIALS[i]));

        player.openInventory(inventory);
    }

    private ItemStack buildItem(Player player, UpgradeType type, Material material) {
        int level = manager.getLevel(player.getUniqueId(), type);
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(type.symbol() + " " + type.label(), type.color(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        Component bonusLine = Component.text("■ ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Bonus actuel : ", NamedTextColor.GRAY))
                .append(Component.text(manager.formatBonus(type, level) + " " + type.unitLabel(), NamedTextColor.WHITE));
        Component levelLine = Component.text("Niveau : ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(level + "/" + UpgradeType.MAX_LEVEL, type.color()));

        if (level >= UpgradeType.MAX_LEVEL) {
            meta.lore(List.of(bonusLine, levelLine, Component.text(""), Component.text("NIVEAU MAXIMUM", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)));
        } else {
            long cost = UpgradeType.costForLevel(level + 1);
            Component nextLine = Component.text("Prochain niveau : ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(manager.formatBonus(type, level + 1) + " " + type.unitLabel(), NamedTextColor.GREEN));
            Component costLine = Component.text("Coût : ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(Numbers.compact(cost) + " Gemmes Sombres", NamedTextColor.DARK_PURPLE));
            meta.lore(List.of(bonusLine, levelLine, Component.text(""), nextLine, costLine, Component.text(""),
                    Component.text("Clique pour acheter !", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        }
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        UpgradeType type = id == null ? null : UpgradeType.parse(id);
        if (type == null || !(event.getWhoClicked() instanceof Player player)) return;

        manager.purchase(player, type, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder) {
                int index = java.util.Arrays.asList(UpgradeType.values()).indexOf(type);
                holder.inventory.setItem(SLOTS[index], buildItem(player, type, MATERIALS[index]));
            }
        });
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
