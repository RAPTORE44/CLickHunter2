package fr.cliker.clikerv2.bag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/** Menu de neuf cases : deux vitres et sept coffres personnels de 54 cases. */
public final class BagMenu implements Listener {
    private static final int MENU_SIZE = 9;
    private static final int FIRST_BAG_SLOT = 1;
    private static final List<String> RANKS =
            List.of("Joueur", "Mini-VIP", "VIP", "VIP+", "MVP", "MVP+", "Legend");

    private final JavaPlugin plugin;
    private final BagManager bagManager;

    public BagMenu(JavaPlugin plugin, BagManager bagManager) {
        this.plugin = plugin;
        this.bagManager = bagManager;
    }

    public void open(Player player) {
        // Fermer d'abord un coffre déjà ouvert pour enregistrer son contenu.
        player.closeInventory();
        if (!bagManager.isLoaded(player.getUniqueId())) {
            player.sendMessage(
                    Component.text(
                            "Tes sacs ne sont pas encore disponibles. Réessaie dans un instant.",
                            NamedTextColor.RED));
            return;
        }
        Holder holder = new Holder(player.getUniqueId(), -1);
        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        MENU_SIZE,
                        Component.text("Sacs", NamedTextColor.GOLD, TextDecoration.BOLD));
        holder.inventory = inventory;
        ItemStack border = icon(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < MENU_SIZE; slot++) inventory.setItem(slot, border);
        int capacity = bagManager.capacity(player);
        for (int bag = 0; bag < BagManager.MAX_CAPACITY; bag++) {
            boolean unlocked = bag < capacity;
            String rank = RANKS.get(bag);
            List<Component> lore =
                    List.of(
                            text(rank, NamedTextColor.GRAY),
                            Component.empty(),
                            text(
                                    unlocked
                                            ? "54 emplacements personnels"
                                            : "Débloque ce rang pour accéder au sac.",
                                    NamedTextColor.GRAY),
                            text(
                                    unlocked ? "Clique pour ouvrir" : "Accès verrouillé",
                                    unlocked ? NamedTextColor.GREEN : NamedTextColor.RED));
            inventory.setItem(
                    FIRST_BAG_SLOT + bag,
                    icon(
                            unlocked ? Material.NETHER_STAR : Material.BARRIER,
                            text(
                                    "Sac " + (bag + 1) + (unlocked ? "" : " — Verrouillé"),
                                    unlocked ? NamedTextColor.GOLD : NamedTextColor.RED),
                            lore));
        }
        player.openInventory(inventory);
    }

    private void openStorage(Player player, int bag) {
        if (!bagManager.isLoaded(player.getUniqueId()) || bag >= bagManager.capacity(player))
            return;
        Holder holder = new Holder(player.getUniqueId(), bag);
        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        BagManager.STORAGE_SIZE,
                        Component.text(
                                "Sac " + (bag + 1), NamedTextColor.GOLD, TextDecoration.BOLD));
        holder.inventory = inventory;
        inventory.setContents(bagManager.contents(player.getUniqueId(), bag));
        player.openInventory(inventory);
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.uuid.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (holder.bag >= 0) return; // Inventaire de stockage : déplacements normaux autorisés.
        event.setCancelled(true); // Aucun objet du sélecteur ne peut être récupéré.
        int bag = event.getRawSlot() - FIRST_BAG_SLOT;
        if (bag < 0 || bag >= BagManager.MAX_CAPACITY || bag >= bagManager.capacity(player)) return;
        if (!event.isLeftClick() && !event.isRightClick()) return;
        fr.cliker.clikerv2.runtime.ServerTasks.run(
                plugin,
                () -> {
                    if (player.isOnline()
                            && player.getOpenInventory().getTopInventory() == holder.inventory)
                        openStorage(player, bag);
                });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        if (holder.bag < 0 || !holder.uuid.equals(event.getWhoClicked().getUniqueId()))
            event.setCancelled(true);
    }

    private void save(Holder holder) {
        if (holder.bag >= 0 && bagManager.isLoaded(holder.uuid)) {
            bagManager.setContents(holder.uuid, holder.bag, holder.inventory.getContents());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder) save(holder);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder()
                instanceof Holder holder) save(holder);
    }

    public void shutdown() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof Holder holder) {
                save(holder); // À l'arrêt, les listeners du plugin peuvent déjà être désactivés.
                player.closeInventory();
            }
        }
    }

    private static final class Holder implements InventoryHolder {
        private final UUID uuid;
        private final int bag; // -1 : sélecteur ; 0-6 : coffre personnel.
        private Inventory inventory;

        private Holder(UUID uuid, int bag) {
            this.uuid = uuid;
            this.bag = bag;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
