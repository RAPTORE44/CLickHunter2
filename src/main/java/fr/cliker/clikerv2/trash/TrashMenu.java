package fr.cliker.clikerv2.trash;

import fr.cliker.clikerv2.comfort.PlayerComfort;
import fr.cliker.clikerv2.rewards.ClaimService;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Consumer;

/** Objets récupérables avant fermeture ; snapshot avec le retrait dans le même playerdata. */
public final class TrashMenu implements Listener, CommandExecutor {
    private final JavaPlugin plugin;
    private final NamespacedKey pending;
    private final Consumer<Player> save;
    private final Set<UUID> failed = new HashSet<>();
    private boolean stopped;
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> recoveryTasks = new HashMap<>();

    public static final class Holder implements InventoryHolder {
        final UUID owner;
        Inventory inventory;

        Holder(UUID owner) {
            this.owner = owner;
        }

        public Inventory getInventory() {
            return inventory;
        }
    }

    public TrashMenu(JavaPlugin plugin) {
        this(plugin, Player::saveData);
    }

    public TrashMenu(JavaPlugin plugin, Consumer<Player> save) {
        this.plugin = plugin;
        this.save = save;
        pending = new NamespacedKey(plugin, "trash_pending");
    }

    public void start() {
        var cmd = plugin.getCommand("trash");
        if (cmd != null) cmd.setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player p : Bukkit.getOnlinePlayers()) recover(p);
    }

    public boolean onCommand(CommandSender s, Command c, String label, String[] args) {
        if (s instanceof Player p) {
            if (!p.hasPermission("cliker.trash"))
                p.sendMessage("§cTu ne peux pas utiliser la poubelle.");
            else open(p);
        }
        return true;
    }

    public void open(Player p) {
        if (stopped || failed.contains(p.getUniqueId())) return;
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)) {
            p.sendMessage("§eReviens dans le monde Clicker pour utiliser la poubelle.");
            return;
        }
        var delivery = Bukkit.getServicesManager().load(ClaimService.class);
        if (delivery != null && delivery.isDelivering(p.getUniqueId())) {
            p.sendMessage("§eAttends la fin de ta livraison avant d’ouvrir la poubelle.");
            return;
        }
        p.closeInventory();
        if (!recover(p)) return;
        Holder holder = new Holder(p.getUniqueId());
        holder.inventory =
                Bukkit.createInventory(
                        holder, 54, Component.text("Poubelle — fermeture = destruction"));
        p.openInventory(holder.inventory);
        p.sendMessage(
                "§7Gauche : déposer/reprendre 1 objet. Droite : toute la pile. §cFermer détruit le"
                        + " contenu.");
    }

    private void snapshot(Player player, Inventory inventory) {
        var payload = new YamlConfiguration();
        var items = new ArrayList<String>();
        for (var item : inventory.getContents())
            if (item != null && !item.getType().isAir()) items.add(ClaimService.serialize(item));
        if (items.isEmpty()) player.getPersistentDataContainer().remove(pending);
        else {
            payload.set("items", items);
            player.getPersistentDataContainer()
                    .set(pending, PersistentDataType.STRING, payload.saveToString());
        }
        if (!save(player)) disconnect(player);
    }

    private boolean save(Player player) {
        try {
            save.accept(player);
            return true;
        } catch (RuntimeException error) {
            failed.add(player.getUniqueId());
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Sauvegarde de poubelle impossible ; contenu conservé pour reprise",
                            error);
            return false;
        }
    }

    private void disconnect(Player player) {
        player.kick(
                Component.text(
                        "Sauvegarde indisponible. Reconnecte-toi pour récupérer les objets en"
                                + " attente."));
    }

    public boolean recover(Player player) {
        String raw = player.getPersistentDataContainer().get(pending, PersistentDataType.STRING);
        if (raw == null) return true;
        var delivery = Bukkit.getServicesManager().load(ClaimService.class);
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)
                || (delivery != null && delivery.isDelivering(player.getUniqueId()))) {
            if (!stopped && !recoveryTasks.containsKey(player.getUniqueId()))
                recoveryTasks.put(
                        player.getUniqueId(),
                        Bukkit.getScheduler()
                                .runTaskLater(
                                        plugin,
                                        () -> {
                                            recoveryTasks.remove(player.getUniqueId());
                                            if (!stopped
                                                    && fr.cliker.clikerv2.runtime.PlayerSessions
                                                            .isCurrent(player)) recover(player);
                                        },
                                        20));
            return false;
        }
        try {
            var config = new YamlConfiguration();
            config.loadFromString(raw);
            var items = new ArrayList<ItemStack>();
            for (String payload : config.getStringList("items")) {
                var item = ClaimService.deserialize(payload);
                if (item == null || item.getType().isAir())
                    throw new IllegalStateException("Objet de poubelle invalide");
                items.add(item);
            }
            var left = new ArrayList<String>();
            for (var item : items)
                for (var remainder : player.getInventory().addItem(item).values())
                    left.add(ClaimService.serialize(remainder));
            if (left.isEmpty()) player.getPersistentDataContainer().remove(pending);
            else {
                config.set("items", left);
                player.getPersistentDataContainer()
                        .set(pending, PersistentDataType.STRING, config.saveToString());
            }
            if (!save(player)) {
                disconnect(player);
                return false;
            }
            if (!left.isEmpty())
                player.sendMessage(
                        "§eLibère de la place puis réutilise /trash pour reprendre les objets"
                                + " conservés.");
            return left.isEmpty();
        } catch (Exception error) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Reprise de poubelle impossible ; sauvegarde conservée",
                            error);
            player.sendMessage(
                    "§cLa poubelle contient une sauvegarde à vérifier. Contacte un"
                            + " administrateur.");
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)
                || !h.owner.equals(p.getUniqueId())
                || failed.contains(h.owner)
                || !p.hasPermission("cliker.trash")
                || (e.getCursor() != null && !e.getCursor().getType().isAir())
                || (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT)) return;
        var claims = Bukkit.getServicesManager().load(ClaimService.class);
        var outgoing =
                Bukkit.getServicesManager().load(fr.cliker.clikerv2.rewards.OutgoingItems.class);
        if ((claims != null && claims.isDelivering(p.getUniqueId()))
                || (outgoing != null && outgoing.locked(p.getUniqueId()))) return;
        var item = e.getCurrentItem();
        if (item == null || item.getType().isAir()) return;
        int amount = e.getClick() == ClickType.LEFT ? 1 : item.getAmount();
        var moving = item.clone();
        moving.setAmount(amount);
        if (e.getClickedInventory() == p.getInventory()) {
            if (PlayerComfort.protectedForSale(p, item)) {
                p.sendActionBar(Component.text("Cet objet est protégé."));
                return;
            }
            int overflow =
                    h.inventory.addItem(moving).values().stream()
                            .mapToInt(ItemStack::getAmount)
                            .sum();
            int moved = amount - overflow;
            if (moved == 0) return;
            var remaining = item.clone();
            remaining.setAmount(item.getAmount() - moved);
            e.setCurrentItem(remaining.getAmount() == 0 ? null : remaining);
        } else if (e.getClickedInventory() == h.inventory) {
            int overflow =
                    p.getInventory().addItem(moving).values().stream()
                            .mapToInt(ItemStack::getAmount)
                            .sum();
            int moved = amount - overflow;
            if (moved == 0) return;
            var remaining = item.clone();
            remaining.setAmount(item.getAmount() - moved);
            e.setCurrentItem(remaining.getAmount() == 0 ? null : remaining);
        } else return;
        snapshot(p, h.inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)
                || !(e.getPlayer() instanceof Player p)
                || !h.owner.equals(p.getUniqueId())) return;
        // Déconnexion, arrêt, mort : ne pas détruire des objets lors d'une fermeture imposée.
        if (stopped
                || !plugin.isEnabled()
                || failed.contains(h.owner)
                || (e.getReason() != InventoryCloseEvent.Reason.PLAYER
                        && e.getReason() != InventoryCloseEvent.Reason.OPEN_NEW)) return;
        String old = p.getPersistentDataContainer().get(pending, PersistentDataType.STRING);
        p.getPersistentDataContainer().remove(pending);
        if (!save(p)) {
            if (old != null)
                p.getPersistentDataContainer().set(pending, PersistentDataType.STRING, old);
            disconnect(p);
            return;
        }
        e.getInventory().clear();
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        failed.remove(e.getPlayer().getUniqueId());
        recover(e.getPlayer());
    }

    @EventHandler
    public void world(PlayerChangedWorldEvent e) {
        recover(e.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        failed.remove(e.getPlayer().getUniqueId());
        var task = recoveryTasks.remove(e.getPlayer().getUniqueId());
        if (task != null) task.cancel();
    }

    public void shutdown() {
        stopped = true;
        recoveryTasks.values().forEach(org.bukkit.scheduler.BukkitTask::cancel);
        recoveryTasks.clear();
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getOpenInventory().getTopInventory() != null
                    && p.getOpenInventory().getTopInventory().getHolder() instanceof Holder)
                p.closeInventory();
        HandlerList.unregisterAll(this);
    }
}
