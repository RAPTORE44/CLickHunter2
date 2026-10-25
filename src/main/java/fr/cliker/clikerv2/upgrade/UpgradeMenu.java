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
        Material.GOLD_INGOT,
        Material.EMERALD,
        Material.EXPERIENCE_BOTTLE,
        Material.DIAMOND_SWORD,
        Material.FEATHER,
        Material.APPLE,
        Material.SHIELD,
        Material.CLOCK
    };

    private final UpgradeManager manager;
    private final UpgradeQuantityMenu quantities;
    private final JavaPlugin plugin;
    private final fr.cliker.clikerv2.ui.ViewRequests requests;
    private final NamespacedKey typeKey;

    public UpgradeMenu(JavaPlugin plugin, UpgradeManager manager) {
        this.manager = manager;
        quantities = new UpgradeQuantityMenu(plugin, manager, this::open);
        this.plugin = plugin;
        requests = new fr.cliker.clikerv2.ui.ViewRequests(plugin);
        this.typeKey = new NamespacedKey(plugin, "cliker_upgrade_type");
    }

    public void open(Player player) {
        if (!manager.isLoaded(player.getUniqueId())) {
            player.sendMessage(
                    Component.text(
                            "Tes améliorations sont en cours de chargement. Réessaie dans un"
                                    + " instant.",
                            NamedTextColor.YELLOW));
            return;
        }
        var request = requests.begin(player);
        manager.gems(player)
                .whenComplete(
                        (balance, error) -> {
                            if (!plugin.isEnabled()) return;
                            fr.cliker.clikerv2.runtime.ServerTasks.run(
                                    plugin,
                                    () -> {
                                        if (!requests.complete(player, request)
                                                || !player.hasPermission("cliker.upgrade.use"))
                                            return;
                                        if (error != null) {
                                            plugin.getLogger()
                                                    .log(
                                                            java.util.logging.Level.WARNING,
                                                            "Menu des améliorations indisponible",
                                                            error);
                                            player.sendMessage(
                                                    Component.text(
                                                            "Tes améliorations sont temporairement"
                                                                    + " indisponibles.",
                                                            NamedTextColor.RED));
                                            return;
                                        }
                                        var holder = new Holder();
                                        holder.owner = player.getUniqueId();
                                        holder.gems = balance;
                                        holder.inventory =
                                                Bukkit.createInventory(
                                                        holder,
                                                        27,
                                                        Component.text(
                                                                "Améliorations",
                                                                NamedTextColor.GREEN));
                                        refresh(player, holder);
                                        fr.cliker.clikerv2.ui.MenuNavigation.back(
                                                plugin, holder.inventory, 26);
                                        player.openInventory(holder.inventory);
                                    });
                        });
    }

    private void refresh(Player player, Holder holder) {
        var types = UpgradeType.values();
        for (int i = 0; i < types.length; i++)
            holder.inventory.setItem(
                    SLOTS[i], buildItem(player, types[i], MATERIALS[i], holder.gems));
    }

    private ItemStack buildItem(Player player, UpgradeType type, Material material, long gems) {
        int level = manager.getLevel(player.getUniqueId(), type);
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(
                Component.text(
                                type.symbol() + " " + type.label(),
                                level >= UpgradeType.MAX_LEVEL
                                        ? NamedTextColor.GOLD
                                        : gems >= UpgradeType.costForLevel(level + 1) * 10
                                                ? NamedTextColor.GREEN
                                                : NamedTextColor.RED,
                                TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));

        Component bonusLine =
                Component.text("■ ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("Bonus actuel : ", NamedTextColor.GRAY))
                        .append(
                                Component.text(
                                        manager.formatBonus(type, level) + " " + type.unitLabel(),
                                        NamedTextColor.WHITE));
        Component levelLine =
                Component.text("Niveau : ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(level + "/" + UpgradeType.MAX_LEVEL, type.color()));

        if (level >= UpgradeType.MAX_LEVEL) {
            meta.lore(
                    List.of(
                            bonusLine,
                            levelLine,
                            Component.text(""),
                            Component.text(
                                            "NIVEAU MAXIMUM",
                                            NamedTextColor.GOLD,
                                            TextDecoration.BOLD)
                                    .decoration(TextDecoration.ITALIC, false)));
        } else {
            long cost = UpgradeType.costForLevel(level + 1);
            Component nextLine =
                    Component.text("Prochain niveau : ", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                            .append(
                                    Component.text(
                                            manager.formatBonus(type, level + 1)
                                                    + " "
                                                    + type.unitLabel(),
                                            NamedTextColor.GREEN));
            Component costLine =
                    Component.text("Coût : ", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                            .append(
                                    Component.text(
                                            Numbers.compact(cost) + " Gemmes Sombres",
                                            NamedTextColor.DARK_PURPLE));
            meta.lore(
                    List.of(
                            bonusLine,
                            levelLine,
                            Component.text(""),
                            nextLine,
                            costLine,
                            Component.text(
                                    "Ton solde : "
                                            + fr.cliker.clikerv2.economy.CurrencyType.GEMMES_SOMBRES
                                                    .format(gems)
                                            + " Gemmes Sombres",
                                    NamedTextColor.GRAY),
                            Component.text(""),
                            Component.text(
                                            gems >= cost * 10
                                                    ? "Gauche : +1 | Droite : choisir une quantité"
                                                    : "Il manque "
                                                            + fr.cliker.clikerv2.economy
                                                                    .CurrencyType.GEMMES_SOMBRES
                                                                    .format(cost * 10 - gems)
                                                            + " Gemmes Sombres.",
                                            gems >= cost * 10
                                                    ? NamedTextColor.GREEN
                                                    : NamedTextColor.RED)
                                    .decoration(TextDecoration.ITALIC, false)));
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
        String id =
                clicked.getItemMeta()
                        .getPersistentDataContainer()
                        .get(typeKey, PersistentDataType.STRING);
        UpgradeType type = id == null ? null : UpgradeType.parse(id);
        if (type == null || !(event.getWhoClicked() instanceof Player player)) return;

        if (event.getClickedInventory() != holder.inventory
                || !holder.owner.equals(player.getUniqueId())
                || !player.hasPermission("cliker.upgrade.use")
                || holder.pending
                || (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                        && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT)) return;
        if (event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT) {
            quantities.open(player, type);
            return;
        }
        holder.pending = true;
        manager.purchase(
                player,
                type,
                event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT,
                () -> {
                    manager.gems(player)
                            .whenComplete(
                                    (balance, error) -> {
                                        if (!plugin.isEnabled()) return;
                                        fr.cliker.clikerv2.runtime.ServerTasks.run(
                                                plugin,
                                                () -> {
                                                    holder.pending = false;
                                                    if (error != null
                                                            || !player.isOnline()
                                                            || player.getOpenInventory()
                                                                            .getTopInventory()
                                                                    != holder.inventory) return;
                                                    holder.gems = balance;
                                                    refresh(player, holder);
                                                });
                                    });
                });
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
    }

    private static final class Holder implements InventoryHolder {
        private java.util.UUID owner;
        private long gems;
        private boolean pending;
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
