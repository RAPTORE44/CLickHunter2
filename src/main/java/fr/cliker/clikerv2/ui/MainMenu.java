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
        File file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "gui/menu.yml");
        if (!file.exists()) fr.cliker.clikerv2.config.ConfigFiles.install(plugin, "gui/menu.yml");
        config = YamlConfiguration.loadConfiguration(file);
        addActivityButtons(file);
        expandLayout(file);
        size = config.getInt("taille", 54);
        if (size < 9 || size > 54 || size % 9 != 0)
            throw new IllegalArgumentException("Taille du menu principal invalide");
        background = item(config.getString("fond", "BLACK_STAINED_GLASS_PANE"), " ", List.of());
        var section = config.getConfigurationSection("boutons");
        if (section != null)
            for (String key : section.getKeys(false)) {
                var button = section.getConfigurationSection(key);
                int slot = button.getInt("slot");
                if (slot < 0 || slot >= size || actions.containsKey(slot))
                    throw new IllegalArgumentException("Slot dupliqué ou invalide : " + key);
                actions.put(
                        slot,
                        new Action(
                                button.getString("commande", ""),
                                button.getString("permission", ""),
                                item(
                                        button.getString("materiau", "PAPER"),
                                        button.getString("nom", key),
                                        button.getStringList("description"))));
            }
    }

    /** Ajouter les nouveautés sans écraser un bouton ou un emplacement personnalisé. */
    private void addActivityButtons(File file) {
        try (var input = plugin.getResource("gui/menu.yml")) {
            var defaults =
                    YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(
                                    java.util.Objects.requireNonNull(input),
                                    java.nio.charset.StandardCharsets.UTF_8));
            java.util.Set<Integer> occupied = new java.util.HashSet<>();
            var buttons = config.getConfigurationSection("boutons");
            if (buttons != null)
                for (String id : buttons.getKeys(false))
                    occupied.add(config.getInt("boutons." + id + ".slot"));
            boolean changed = false;
            if ("&8NorthBlue &7• &8Menu principal".equals(config.getString("titre"))) {
                config.set("titre", "&2ClickHunter &8• &2Menu principal");
                changed = true;
            }
            for (String id :
                    java.util.List.of(
                            "bestiaire",
                            "contrats",
                            "raid",
                            "avantposte",
                            "session",
                            "warp",
                            "talismans",
                            "guide",
                            "atm",
                            "wiki",
                            "claim")) {
                String path = "boutons." + id;
                if (config.contains(path)) continue;
                int slot = defaults.getInt(path + ".slot"), size = config.getInt("taille", 36);
                if (slot >= size || occupied.contains(slot)) {
                    slot = -1;
                    for (int candidate = 0; candidate < size; candidate++)
                        if (!occupied.contains(candidate)) {
                            slot = candidate;
                            break;
                        }
                }
                if (slot < 0) {
                    plugin.getLogger()
                            .warning("Menu complet : ajouter manuellement le bouton " + id);
                    continue;
                }
                config.createSection(path, defaults.getConfigurationSection(path).getValues(false));
                config.set(path + ".slot", slot);
                occupied.add(slot);
                changed = true;
            }
            if (changed) config.save(file);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Mise à jour du menu impossible", error);
        }
    }

    private void expandLayout(File file) {
        if (config.getInt("version-disposition", 0) >= 2) return;
        try (var input = plugin.getResource("gui/menu.yml")) {
            var defaults =
                    YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(
                                    Objects.requireNonNull(input),
                                    java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.copy(
                    file.toPath(),
                    file.toPath()
                            .resolveSibling(
                                    "menu-avant-double-coffre-"
                                            + System.currentTimeMillis()
                                            + ".yml"));
            Set<Integer> used = new HashSet<>();
            var buttons = config.getConfigurationSection("boutons");
            var known = defaults.getConfigurationSection("boutons");
            for (String key : known.getKeys(false))
                if (config.contains("boutons." + key)) {
                    int slot = defaults.getInt("boutons." + key + ".slot");
                    config.set("boutons." + key + ".slot", slot);
                    used.add(slot);
                }
            if (buttons != null)
                for (String key : buttons.getKeys(false))
                    if (!known.contains(key)) {
                        int slot = config.getInt("boutons." + key + ".slot");
                        if (slot < 0 || slot >= 54 || used.contains(slot)) {
                            slot = -1;
                            for (int candidate = 0; candidate < 54; candidate++)
                                if (!used.contains(candidate)) {
                                    slot = candidate;
                                    break;
                                }
                        }
                        if (slot < 0)
                            throw new IllegalArgumentException("Trop de raccourcis pour 54 cases");
                        config.set("boutons." + key + ".slot", slot);
                        used.add(slot);
                    }
            config.set("taille", 54);
            config.set("version-disposition", 2);
            config.save(file);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Migration du menu impossible", e);
        }
    }

    private static ItemStack item(String material, String title, List<String> lore) {
        Material type = Material.matchMaterial(material);
        if (type == null || type.isAir() || !type.isItem())
            throw new IllegalArgumentException("Matériau du menu invalide : " + material);
        ItemStack item = new ItemStack(type);
        var meta = item.getItemMeta();
        meta.displayName(
                DailySettings.color(title)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(
                lore.stream()
                        .map(
                                s ->
                                        DailySettings.color(s)
                                                .decoration(
                                                        net.kyori.adventure.text.format
                                                                .TextDecoration.ITALIC,
                                                        false))
                        .toList());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (!player.hasPermission("cliker.menu")) {
            player.sendMessage(
                    DailySettings.color(
                            config.getString(
                                    "messages.interdit", "&cTu n'as pas accès à ce menu.")));
            return true;
        }
        Holder holder = new Holder(player.getUniqueId());
        holder.inventory =
                Bukkit.createInventory(
                        holder,
                        size,
                        DailySettings.color(config.getString("titre", "ClickHunter • Menu")));
        for (int slot = 0; slot < size; slot++) holder.inventory.setItem(slot, background);
        for (var entry : actions.entrySet()) {
            Action action = entry.getValue();
            if (!action.permission.isBlank() && !player.hasPermission(action.permission)) {
                holder.inventory.setItem(
                        entry.getKey(),
                        item(
                                "BARRIER",
                                "&cAccès verrouillé",
                                List.of("&7Tu n'as pas encore accès à cette fonction.")));
            } else holder.inventory.setItem(entry.getKey(), action.icon);
        }
        player.openInventory(holder.inventory);
        return true;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.owner.equals(player.getUniqueId())
                || holder.pending) return;
        if (!event.isLeftClick() && !event.isRightClick()) return;
        Action action = actions.get(event.getRawSlot());
        if (action == null) return;
        if (!action.permission.isBlank() && !player.hasPermission(action.permission)) return;
        holder.pending = true;
        fr.cliker.clikerv2.runtime.ServerTasks.run(
                plugin,
                () -> {
                    if (!player.isOnline()
                            || player.getOpenInventory().getTopInventory() != holder.inventory)
                        return;
                    player.closeInventory();
                    if (!player.hasPermission("cliker.menu")
                            || (!action.permission.isBlank()
                                    && !player.hasPermission(action.permission))) return;
                    if (!action.command.isBlank()) {
                        if (!fr.cliker.clikerv2.outpost.WorldInventories.commandAllowed(
                                player, action.command)) {
                            player.sendMessage(
                                    "Reviens au /spawn pour utiliser cette fonction avec ton"
                                            + " inventaire clicker.");
                            return;
                        }
                        player.performCommand(action.command);
                    }
                });
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
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

        private Holder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
