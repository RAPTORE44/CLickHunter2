package fr.cliker.clikerv2.ui;

import fr.cliker.clikerv2.daily.DailySettings;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/** Navigation centralisée : uniquement les commandes existantes et autorisées. */
public final class MainMenu implements CommandExecutor, Listener {
    private final JavaPlugin plugin;
    private final YamlConfiguration config;
    private final Map<Integer, Action> actions = new LinkedHashMap<>();
    private final ItemStack background;
    private final int size;
    public MainMenu(JavaPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "gui/menu.yml");
        if (!file.exists()) plugin.saveResource("gui/menu.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        size = config.getInt("taille", 36);
        if (size < 9 || size > 54 || size % 9 != 0) throw new IllegalArgumentException("Taille du menu principal invalide");
        background = item(config.getString("fond", "BLACK_STAINED_GLASS_PANE"), " ", List.of());
        var section = config.getConfigurationSection("boutons");
        if (section != null) for (String key : section.getKeys(false)) {
            var button = section.getConfigurationSection(key);
            int slot = button.getInt("slot");
            if (slot < 0 || slot >= size || actions.containsKey(slot)) throw new IllegalArgumentException("Slot dupliqué ou invalide : " + key);
            actions.put(slot, new Action(button.getString("commande", ""), button.getString("permission", ""),
                    item(button.getString("materiau", "PAPER"), button.getString("nom", key), button.getStringList("description"))));
        }
    }
    private static ItemStack item(String material, String title, List<String> lore) {
        Material type = Material.matchMaterial(material);
        if (type == null || type.isAir() || !type.isItem()) throw new IllegalArgumentException("Matériau du menu invalide : " + material);
        ItemStack item = new ItemStack(type);
        var meta = item.getItemMeta();
        meta.displayName(DailySettings.color(title).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(s -> DailySettings.color(s).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta); return item;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Commande réservée aux joueurs."); return true; }
        if (!player.hasPermission("cliker.menu")) { player.sendMessage(DailySettings.color(config.getString("messages.interdit", "&cTu n'as pas accès à ce menu."))); return true; }
        Holder holder = new Holder(player.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, size, DailySettings.color(config.getString("titre", "NorthBlue • Menu")));
        for (int slot = 0; slot < size; slot++) holder.inventory.setItem(slot, background);
        for (var entry : actions.entrySet()) {
            Action action = entry.getValue();
            if (!action.permission.isBlank() && !player.hasPermission(action.permission)) {
                holder.inventory.setItem(entry.getKey(), item("BARRIER", "&cAccès verrouillé", List.of("&7Tu n'as pas encore accès à cette fonction.")));
            } else holder.inventory.setItem(entry.getKey(), action.icon);
        }
        player.openInventory(holder.inventory); return true;
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId()) || holder.pending) return;
        if (!event.isLeftClick() && !event.isRightClick()) return;
        Action action = actions.get(event.getRawSlot());
        if (action == null) return;
        if (!action.permission.isBlank() && !player.hasPermission(action.permission)) return;
        holder.pending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            player.closeInventory();
            if (!player.hasPermission("cliker.menu") || (!action.permission.isBlank() && !player.hasPermission(action.permission))) return;
            if (!action.command.isBlank()) player.performCommand(action.command);
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
    public void shutdown() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof Holder) player.closeInventory();
        }
        HandlerList.unregisterAll(this);
    }
    private record Action(String command, String permission, ItemStack icon) {}
    private static final class Holder implements InventoryHolder {
        private final UUID owner;
        private boolean pending;
        private Inventory inventory;
        private Holder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
