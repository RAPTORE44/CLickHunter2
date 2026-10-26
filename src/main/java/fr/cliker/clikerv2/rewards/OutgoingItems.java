package fr.cliker.clikerv2.rewards;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.runtime.PlayerSessions;
import fr.cliker.clikerv2.runtime.ServerTasks;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

/** Toutes les mutations Bukkit ont lieu sur le thread serveur ; le journal seul est asynchrone. */
public final class OutgoingItems implements Listener {
    private final JavaPlugin plugin;
    private final OutgoingStore store;
    private final ClaimService claims;
    private final Consumer<Player> savePlayer;
    private final NamespacedKey receipt;

    private static final class Attempt {
        boolean warned;
    }

    private final Map<UUID, Attempt> active = new HashMap<>();
    private boolean stopped;

    public OutgoingItems(JavaPlugin plugin, DatabaseManager database, ClaimService claims) {
        this(plugin, database, claims, Player::saveData);
    }

    public OutgoingItems(
            JavaPlugin plugin,
            DatabaseManager database,
            ClaimService claims,
            Consumer<Player> savePlayer) {
        this.plugin = plugin;
        this.store = new OutgoingStore(database);
        this.claims = claims;
        this.savePlayer = savePlayer;
        this.receipt = new NamespacedKey(plugin, "outgoing_item");
    }

    public void start() {
        Bukkit.getServicesManager()
                .register(OutgoingItems.class, this, plugin, ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) recover(player);
    }

    public boolean locked(UUID owner) {
        return active.containsKey(owner);
    }

    private boolean current(Player player, Attempt token) {
        return !stopped
                && active.get(player.getUniqueId()) == token
                && PlayerSessions.isCurrent(player);
    }

    /** Le commit doit consommer le reçu SQL dans sa propre transaction (OutgoingStore.commit). */
    public boolean consumeHeld(
            Player player,
            ItemStack requested,
            Function<String, CompletableFuture<Void>> commit,
            Consumer<Throwable> completed) {
        if (stopped
                || !PlayerSessions.isCurrent(player)
                || claims.isDelivering(player.getUniqueId())
                || fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)) return false;
        if (player.getPersistentDataContainer().has(receipt)) {
            recover(player);
            return false;
        }
        if (requested == null || requested.getType().isAir() || requested.getAmount() <= 0)
            return false;
        ItemStack item = requested.clone();
        int slot = player.getInventory().getHeldItemSlot();
        if (!matches(player, slot, item)) return false;
        String id = UUID.randomUUID().toString();
        Attempt token = new Attempt();
        String payload = ClaimService.serialize(item);
        active.put(player.getUniqueId(), token);
        store.prepare(id, player.getUniqueId(), payload)
                .whenComplete(
                        (v, error) ->
                                sync(
                                        () -> {
                                            if (!current(player, token)) return;
                                            if (error != null
                                                    || !matches(player, slot, item)
                                                    || fr.cliker.clikerv2.comfort.PlayerComfort
                                                            .protectedForSale(
                                                                    player,
                                                                    player.getInventory()
                                                                            .getItem(slot))) {
                                                complete(
                                                        player,
                                                        token,
                                                        completed,
                                                        error == null
                                                                ? new IllegalStateException(
                                                                        "Objet modifié ou protégé")
                                                                : error);
                                                return;
                                            }
                                            ItemStack held =
                                                    player.getInventory().getItem(slot).clone();
                                            int remaining = held.getAmount() - item.getAmount();
                                            held.setAmount(remaining);
                                            player.getPersistentDataContainer()
                                                    .set(receipt, PersistentDataType.STRING, id);
                                            player.getInventory()
                                                    .setItem(slot, remaining == 0 ? null : held);
                                            try {
                                                // Un même playerdata contient le retrait et le
                                                // reçu. Aucune publication avant cela.
                                                savePlayer.accept(player);
                                                commit.apply(id)
                                                        .whenComplete(
                                                                (ignored, failure) ->
                                                                        sync(
                                                                                () -> {
                                                                                    complete(
                                                                                            player,
                                                                                            token,
                                                                                            completed,
                                                                                            failure);
                                                                                }));
                                            } catch (RuntimeException failure) {
                                                // Le disque peut avoir écrit malgré une exception :
                                                // ne pas restaurer aveuglément.
                                                complete(player, token, completed, failure);
                                            }
                                        }));
        return true;
    }

    private void complete(
            Player player, Attempt token, Consumer<Throwable> completed, Throwable error) {
        try {
            completed.accept(error);
        } catch (RuntimeException callbackError) {
            plugin.getLogger()
                    .log(Level.SEVERE, "Notification de publication interrompue", callbackError);
        } finally {
            if (current(player, token)) recover(player, token);
        }
    }

    private static boolean matches(Player player, int slot, ItemStack item) {
        ItemStack held = player.getInventory().getItem(slot);
        return held != null && held.isSimilar(item) && held.getAmount() >= item.getAmount();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void join(PlayerJoinEvent event) {
        recover(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }

    public void recover(Player player) {
        if (stopped || !PlayerSessions.isCurrent(player)) return;
        Attempt token = new Attempt();
        active.put(player.getUniqueId(), token);
        recover(player, token);
    }

    private void recover(Player player, Attempt token) {
        if (!current(player, token)) return;
        String savedReceipt =
                player.getPersistentDataContainer().get(receipt, PersistentDataType.STRING);
        try {
            // Réessayer une sauvegarde interrompue avant de rendre le moindre objet.
            if (savedReceipt != null) savePlayer.accept(player);
        } catch (RuntimeException error) {
            retry(player, token, error);
            return;
        }
        store.recover(player.getUniqueId(), savedReceipt)
                .whenComplete(
                        (v, error) ->
                                sync(
                                        () -> {
                                            if (!current(player, token)) return;
                                            if (error != null) {
                                                retry(player, token, error);
                                                return;
                                            }
                                            try {
                                                if (savedReceipt != null) {
                                                    player.getPersistentDataContainer()
                                                            .remove(receipt);
                                                    savePlayer.accept(player);
                                                }
                                            } catch (RuntimeException failure) {
                                                player.getPersistentDataContainer()
                                                        .set(
                                                                receipt,
                                                                PersistentDataType.STRING,
                                                                savedReceipt);
                                                retry(player, token, failure);
                                                return;
                                            }
                                            active.remove(player.getUniqueId(), token);
                                            claims.resume(player.getUniqueId());
                                        }));
    }

    private void retry(Player player, Attempt token, Throwable error) {
        if (!token.warned) {
            token.warned = true;
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            "Reprise de sortie d'objet à réessayer pour " + player.getUniqueId(),
                            error);
            player.sendMessage(
                    "§cUne sauvegarde reste à vérifier. Les déplacements d’objets sont"
                        + " temporairement bloqués.");
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> recover(player, token), 100L);
    }

    private void sync(Runnable action) {
        if (!stopped && plugin.isEnabled()) ServerTasks.run(plugin, action);
    }

    public void shutdown() {
        stopped = true;
        active.clear();
        org.bukkit.event.HandlerList.unregisterAll(this);
        Bukkit.getServicesManager().unregister(OutgoingItems.class, this);
    }
}
