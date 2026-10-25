package fr.cliker.clikerv2.comfort;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Mode de jeu conservé et bouton de menu protégé, sans écraser les objets du joueur. */
public final class SessionConvenience implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final NamespacedKey modeKey, menuKey;
    private final Set<UUID> joined = new HashSet<>();
    private final Map<UUID, BukkitTask> retries = new HashMap<>();

    public SessionConvenience(JavaPlugin plugin) {
        this.plugin = plugin;
        modeKey = new NamespacedKey(plugin, "saved_gamemode");
        menuKey = new NamespacedKey(plugin, "menu_hotbar");
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getServicesManager()
                .register(
                        SessionConvenience.class,
                        this,
                        plugin,
                        org.bukkit.plugin.ServicePriority.Normal);
        if (plugin.getCommand("gm") != null) {
            plugin.getCommand("gm").setExecutor(this);
            plugin.getCommand("gm").setTabCompleter(this);
        }
        for (var player : Bukkit.getOnlinePlayers()) {
            joined.add(player.getUniqueId());
            remember(player);
            ensureMenu(player);
        }
    }

    private void remember(Player player) {
        player.getPersistentDataContainer()
                .set(modeKey, PersistentDataType.STRING, player.getGameMode().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        var player = event.getPlayer();
        String saved = player.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        joined.add(player.getUniqueId());
        fr.cliker.clikerv2.runtime.ServerTasks.run(
                plugin,
                () -> {
                    if (!fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(player)) return;
                    if (saved != null)
                        try {
                            player.setGameMode(GameMode.valueOf(saved));
                        } catch (IllegalArgumentException error) {
                            plugin.getLogger()
                                    .warning(
                                            "Mode de jeu sauvegardé invalide pour "
                                                    + player.getUniqueId());
                        }
                    remember(player);
                    ensureMenu(player);
                });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mode(PlayerGameModeChangeEvent event) {
        if (joined.contains(event.getPlayer().getUniqueId()))
            event.getPlayer()
                    .getPersistentDataContainer()
                    .set(modeKey, PersistentDataType.STRING, event.getNewGameMode().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        remember(event.getPlayer());
        joined.remove(event.getPlayer().getUniqueId());
        var task = retries.remove(event.getPlayer().getUniqueId());
        if (task != null) task.cancel();
    }

    public static boolean protectedMenu(ItemStack item) {
        var service = Bukkit.getServicesManager().load(SessionConvenience.class);
        return service != null && service.isMenu(item);
    }

    public boolean isMenu(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta()
                        .getPersistentDataContainer()
                        .has(menuKey, PersistentDataType.BYTE);
    }

    public void ensureMenu(Player player) {
        if (!player.isOnline() || player.isDead()) return;
        var delivery =
                Bukkit.getServicesManager().load(fr.cliker.clikerv2.rewards.ClaimService.class);
        if (delivery != null && delivery.isDelivering(player.getUniqueId())) {
            retry(player);
            return;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++)
            if (slot != 8 && isMenu(inventory.getItem(slot))) inventory.setItem(slot, null);
        if (isMenu(inventory.getItem(8))) return;
        var previous = inventory.getItem(8);
        if (previous != null && !previous.getType().isAir()) {
            int empty = -1;
            for (int slot = 0; slot < 36; slot++)
                if (slot != 8
                        && (inventory.getItem(slot) == null
                                || inventory.getItem(slot).getType().isAir())) {
                    empty = slot;
                    break;
                }
            if (empty < 0) {
                player.sendMessage(
                        Component.text(
                                "Libère une case pour installer l’étoile de menu en neuvième case.",
                                NamedTextColor.YELLOW));
                return;
            }
            inventory.setItem(empty, previous.clone());
        }
        var star = new ItemStack(Material.NETHER_STAR);
        var meta = star.getItemMeta();
        meta.displayName(Component.text("Menu ClickHunter", NamedTextColor.GREEN));
        meta.lore(
                List.of(Component.text("Clic droit pour ouvrir le menu.", NamedTextColor.YELLOW)));
        meta.getPersistentDataContainer().set(menuKey, PersistentDataType.BYTE, (byte) 1);
        star.setItemMeta(meta);
        inventory.setItem(8, star);
    }

    private void retry(Player player) {
        if (retries.containsKey(player.getUniqueId())) return;
        retries.put(
                player.getUniqueId(),
                plugin.getServer()
                        .getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    retries.remove(player.getUniqueId());
                                    if (fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(player))
                                        ensureMenu(player);
                                },
                                20L));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void use(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || !isMenu(event.getItem())) return;
        if (event.useItemInHand() == Event.Result.DENY) return;
        event.setCancelled(true);
        var player = event.getPlayer();
        fr.cliker.clikerv2.runtime.ServerTasks.run(
                plugin,
                () -> {
                    if (fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(player))
                        player.performCommand("menu");
                });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (isMenu(event.getCurrentItem())
                || isMenu(event.getCursor())
                || (event.getClick() == ClickType.NUMBER_KEY
                        && event.getHotbarButton() == 8
                        && isMenu(event.getWhoClicked().getInventory().getItem(8))))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (isMenu(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }
        if (isMenu(event.getWhoClicked().getInventory().getItem(8)))
            for (int raw : event.getRawSlots())
                if (raw >= event.getView().getTopInventory().getSize()
                        && event.getView().convertSlot(raw) == 8) {
                    event.setCancelled(true);
                    return;
                }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void drop(PlayerDropItemEvent event) {
        if (isMenu(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void swap(PlayerSwapHandItemsEvent event) {
        if (isMenu(event.getMainHandItem()) || isMenu(event.getOffHandItem()))
            event.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) retry(player);
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isMenu);
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        retry(event.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.gamemode.admin")) {
            sender.sendMessage("Permission administrateur requise.");
            return true;
        }
        if (args.length > 2) {
            sender.sendMessage("Usage : /gm [0|1|2|3] [joueur]");
            return true;
        }
        Player target =
                args.length == 2
                        ? Bukkit.getPlayerExact(args[1])
                        : sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage("Joueur en ligne requis : /gm <mode> <joueur>.");
            return true;
        }
        GameMode mode =
                args.length == 0
                        ? (target.getGameMode() == GameMode.CREATIVE
                                ? GameMode.SURVIVAL
                                : GameMode.CREATIVE)
                        : parse(args[0]);
        if (mode == null) {
            sender.sendMessage("Modes : 0 survie, 1 créatif, 2 aventure, 3 spectateur.");
            return true;
        }
        target.setGameMode(mode);
        if (target.getGameMode() != mode) {
            sender.sendMessage("Changement de mode refusé par une protection du serveur.");
            return true;
        }
        remember(target);
        sender.sendMessage(
                Component.text(target.getName() + " : " + label(mode) + ".", NamedTextColor.GREEN));
        return true;
    }

    static GameMode parse(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survie", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "crea", "créa", "creatif", "créatif", "creative" -> GameMode.CREATIVE;
            case "2", "a", "aventure", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spec", "spectateur", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private static String label(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> "Survie";
            case CREATIVE -> "Créatif";
            case ADVENTURE -> "Aventure";
            case SPECTATOR -> "Spectateur";
        };
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cliker.gamemode.admin")) return List.of();
        List<String> values =
                args.length == 1
                        ? List.of("0", "1", "2", "3")
                        : args.length == 2
                                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                                : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    public void shutdown() {
        Bukkit.getServicesManager().unregister(SessionConvenience.class, this);
        for (var player : Bukkit.getOnlinePlayers()) remember(player);
        retries.values().forEach(BukkitTask::cancel);
        retries.clear();
        joined.clear();
        HandlerList.unregisterAll(this);
    }
}
