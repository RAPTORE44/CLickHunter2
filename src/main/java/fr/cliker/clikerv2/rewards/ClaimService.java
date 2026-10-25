package fr.cliker.clikerv2.rewards;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.daily.DailySettings;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/** Objets différés uniquement : monnaies créditées dans les services économiques. */
public final class ClaimService implements Listener, CommandExecutor {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final MailStore store;
    private final YamlConfiguration config;
    private final NamespacedKey receipt;
    private final java.util.function.Consumer<Player> savePlayer;
    private final java.util.function.BiConsumer<Player, ItemStack[]> writeStorage;
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, CompletableFuture<Void>> acknowledgements = new HashMap<>();
    private final Map<UUID, Object> opening = new HashMap<>();
    private volatile boolean stopped;

    public ClaimService(JavaPlugin plugin, DatabaseManager database) {
        this(plugin, database, Player::saveData);
    }

    ClaimService(
            JavaPlugin plugin,
            DatabaseManager database,
            java.util.function.Consumer<Player> savePlayer) {
        this(
                plugin,
                database,
                savePlayer,
                (player, contents) -> player.getInventory().setStorageContents(contents));
    }

    ClaimService(
            JavaPlugin plugin,
            DatabaseManager database,
            java.util.function.Consumer<Player> savePlayer,
            java.util.function.BiConsumer<Player, ItemStack[]> writeStorage) {
        this.savePlayer = savePlayer;
        this.writeStorage = writeStorage;
        this.plugin = plugin;
        this.database = database;
        store = new MailStore(database);
        receipt = new NamespacedKey(plugin, "claim_receipt");
        File file = new File(plugin.getDataFolder(), "claim.yml");
        if (!file.exists()) plugin.saveResource("claim.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void start() {
        plugin.getServer()
                .getServicesManager()
                .register(ClaimService.class, this, plugin, ServicePriority.Normal);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("claim")).setExecutor(this);
    }

    public static String serialize(ItemStack item) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item", item);
        return config.saveToString();
    }

    public static ItemStack deserialize(String payload) {
        try {
            YamlConfiguration c = new YamlConfiguration();
            c.loadFromString(payload);
            return Objects.requireNonNull(c.getItemStack("item"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Objet sauvegardé invalide", e);
        }
    }

    public MailStore store() {
        return store;
    }

    public boolean isDelivering(UUID uuid) {
        return busy.contains(uuid);
    }

    public CompletableFuture<UUID> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return CompletableFuture.completedFuture(online.getUniqueId());
        var cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.hasPlayedBefore())
            return CompletableFuture.completedFuture(cached.getUniqueId());
        return database.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "SELECT uuid FROM referral_players WHERE name=? COLLATE NOCASE"
                                            + " ORDER BY last_seen DESC LIMIT 1")) {
                        s.setString(1, name);
                        try (var r = s.executeQuery()) {
                            if (r.next()) return UUID.fromString(r.getString(1));
                        }
                        try (var b =
                                c.prepareStatement(
                                        "SELECT uuid FROM player_balances WHERE player_name=?"
                                                + " COLLATE NOCASE LIMIT 1")) {
                            b.setString(1, name);
                            try (var r = b.executeQuery()) {
                                return r.next() ? UUID.fromString(r.getString(1)) : null;
                            }
                        }
                    } catch (java.sql.SQLException e) {
                        throw new DatabaseManager.DatabaseException(e);
                    }
                });
    }

    public void give(UUID owner, String name, ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return;
        Player online = Bukkit.getPlayer(owner);
        ItemStack remaining = item.clone();
        if (online != null && online.isOnline() && !busy.contains(owner)) {
            var leftovers = online.getInventory().addItem(remaining);
            if (leftovers.isEmpty()) return;
            for (ItemStack leftover : leftovers.values()) enqueue(owner, leftover);
        } else enqueue(owner, remaining);
    }

    private void enqueue(UUID owner, ItemStack item) {
        int remaining = item.getAmount();
        while (remaining > 0) {
            ItemStack part = item.clone();
            int amount = Math.min(remaining, item.getMaxStackSize());
            part.setAmount(amount);
            remaining -= amount;
            store.enqueue(owner, UUID.randomUUID().toString(), serialize(part))
                    .whenComplete(
                            (entry, error) ->
                                    sync(
                                            () -> {
                                                Player player = Bukkit.getPlayer(owner);
                                                if (error != null) {
                                                    plugin.getLogger()
                                                            .log(
                                                                    Level.SEVERE,
                                                                    "Sauvegarde d'objet /claim"
                                                                            + " impossible pour "
                                                                            + owner,
                                                                    error);
                                                    if (player != null) message(player, "erreur");
                                                } else if (player != null)
                                                    message(player, "attente");
                                            }));
        }
    }

    public void notifyAndDeliver(UUID owner) {
        Player player = Bukkit.getPlayer(owner);
        if (player == null || !player.isOnline()) return;
        store.page(owner, 0)
                .whenComplete(
                        (page, error) ->
                                sync(
                                        () -> {
                                            if (!player.isOnline()
                                                    || error != null
                                                    || page.entries().isEmpty()) return;
                                            message(player, "attente");
                                            deliver(player, page.entries().getFirst().id(), true);
                                        }));
    }

    public void open(Player player, int page) {
        if (!player.hasPermission("cliker.claim")) {
            player.sendMessage(DailySettings.color("&cTu n'as pas la permission."));
            return;
        }
        if (busy.contains(player.getUniqueId())) {
            message(player, "traitement");
            return;
        }
        if (opening.containsKey(player.getUniqueId())) return;
        Object token = new Object();
        opening.put(player.getUniqueId(), token);
        player.sendActionBar(
                DailySettings.color(
                        config.getString(
                                "messages.chargement", "&7Chargement de tes récompenses...")));
        store.page(player.getUniqueId(), Math.max(0, page))
                .whenComplete(
                        (result, error) ->
                                sync(
                                        () -> {
                                            if (!player.isOnline()
                                                    || opening.get(player.getUniqueId()) != token)
                                                return;
                                            opening.remove(player.getUniqueId());
                                            if (error != null) {
                                                message(player, "erreur");
                                                return;
                                            }
                                            if (page > 0 && result.entries().isEmpty()) {
                                                open(player, page - 1);
                                                return;
                                            }
                                            Holder holder =
                                                    new Holder(
                                                            player.getUniqueId(),
                                                            page,
                                                            result.entries());
                                            holder.inventory =
                                                    Bukkit.createInventory(
                                                            holder,
                                                            54,
                                                            DailySettings.color(
                                                                    config.getString(
                                                                            "titre",
                                                                            "Récompenses")));
                                            for (int i = 45; i < 54; i++)
                                                holder.inventory.setItem(
                                                        i,
                                                        button(
                                                                Material.BLACK_STAINED_GLASS_PANE,
                                                                " "));
                                            for (int i = 0; i < result.entries().size(); i++) {
                                                ItemStack icon =
                                                        deserialize(
                                                                result.entries().get(i).payload());
                                                var meta = icon.getItemMeta();
                                                var lore =
                                                        meta.lore() == null
                                                                ? new ArrayList<
                                                                        net.kyori.adventure.text
                                                                                .Component>()
                                                                : new ArrayList<>(meta.lore());
                                                lore.add(
                                                        DailySettings.color(
                                                                "&aClique pour récupérer cet"
                                                                        + " objet."));
                                                meta.lore(lore);
                                                icon.setItemMeta(meta);
                                                holder.inventory.setItem(i, icon);
                                            }
                                            if (page > 0)
                                                holder.inventory.setItem(
                                                        45,
                                                        button(
                                                                Material.ARROW,
                                                                "&ePage précédente"));
                                            if ((long) (page + 1) * 45 < result.total())
                                                holder.inventory.setItem(
                                                        53,
                                                        button(Material.ARROW, "&ePage suivante"));
                                            holder.inventory.setItem(
                                                    49, button(Material.BARRIER, "&cFermer"));
                                            holder.inventory.setItem(
                                                    48,
                                                    button(
                                                            Material.BOOK,
                                                            "&e"
                                                                    + result.total()
                                                                    + " objet(s) en attente • Page "
                                                                    + (page + 1)));
                                            player.openInventory(holder.inventory);
                                            if (result.total() == 0) message(player, "vide");
                                        }));
    }

    private static ItemStack button(Material type, String text) {
        ItemStack item = new ItemStack(type);
        var meta = item.getItemMeta();
        meta.displayName(DailySettings.color(text));
        item.setItemMeta(meta);
        return item;
    }

    public void deliver(Player player, String id, boolean automatic) {
        UUID owner = player.getUniqueId();
        Inventory currentView = player.getOpenInventory().getTopInventory();
        if (automatic
                && currentView != null
                && !(currentView.getHolder() instanceof Holder)
                && currentView.getType() != InventoryType.CRAFTING
                && currentView.getType() != InventoryType.PLAYER) return;
        if (!busy.add(owner)) {
            if (!automatic) message(player, "traitement");
            return;
        }
        String lease = UUID.randomUUID().toString();
        store.reserve(owner, id, lease, System.currentTimeMillis())
                .whenComplete(
                        (entry, error) ->
                                sync(
                                        () -> {
                                            if (error != null || entry == null) {
                                                busy.remove(owner);
                                                if (player.isOnline() && !automatic)
                                                    message(
                                                            player,
                                                            error == null
                                                                    ? "traitement"
                                                                    : "erreur");
                                                return;
                                            }
                                            if (!player.isOnline()) {
                                                store.finish(owner, id, lease, false);
                                                busy.remove(owner);
                                                return;
                                            }
                                            try {
                                                boolean already = hasReceipt(player, id);
                                                if (!already) {
                                                    ItemStack[] planned =
                                                            plan(
                                                                    player.getInventory()
                                                                            .getStorageContents(),
                                                                    deserialize(entry.payload()),
                                                                    id);
                                                    if (planned == null) {
                                                        store.finish(owner, id, lease, false)
                                                                .whenComplete(
                                                                        (v, e) ->
                                                                                sync(
                                                                                        () -> {
                                                                                            busy
                                                                                                    .remove(
                                                                                                            owner);
                                                                                            if (player
                                                                                                    .isOnline())
                                                                                                message(
                                                                                                        player,
                                                                                                        e
                                                                                                                        == null
                                                                                                                ? "plein"
                                                                                                                : "erreur");
                                                                                        }));
                                                        return;
                                                    }
                                                    writeStorage.accept(player, planned);
                                                }
                                                saveAndAcknowledge(player, id, lease, automatic);
                                            } catch (RuntimeException failure) {
                                                plugin.getLogger()
                                                        .log(
                                                                Level.SEVERE,
                                                                "Récupération /claim interrompue"
                                                                        + " pour "
                                                                        + owner,
                                                                failure);
                                                if (hasReceipt(player, id)) {
                                                    Bukkit.getScheduler()
                                                            .runTaskLater(
                                                                    plugin,
                                                                    () ->
                                                                            saveAndAcknowledge(
                                                                                    player, id,
                                                                                    lease,
                                                                                    automatic),
                                                                    40L);
                                                } else {
                                                    store.finish(owner, id, lease, false)
                                                            .whenComplete(
                                                                    (v, e) ->
                                                                            sync(
                                                                                    () -> {
                                                                                        busy.remove(
                                                                                                owner);
                                                                                        if (player
                                                                                                .isOnline())
                                                                                            message(
                                                                                                    player,
                                                                                                    "erreur");
                                                                                    }));
                                                }
                                            }
                                        }));
    }

    private void saveAndAcknowledge(Player player, String id, String lease, boolean automatic) {
        if (stopped) return;
        if (Bukkit.getPlayer(player.getUniqueId()) != player || !player.isOnline()) {
            releaseAndReconcile(player.getUniqueId(), id, lease);
            return;
        }
        try {
            savePlayer.accept(player);
            acknowledge(player, id, lease, automatic);
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE, "Sauvegarde joueur /claim à réessayer", error);
            if (player.isOnline()) message(player, "sauvegarde");
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin, () -> saveAndAcknowledge(player, id, lease, automatic), 40L);
        }
    }

    private boolean hasReceipt(Player player, String id) {
        for (ItemStack item : player.getInventory().getStorageContents())
            if (id.equals(receipt(item))) return true;
        return false;
    }

    private String receipt(ItemStack item) {
        return item == null || !item.hasItemMeta()
                ? null
                : item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(receipt, PersistentDataType.STRING);
    }

    private void mark(ItemStack item, String id) {
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(receipt, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
    }

    /**
     * Préparer tous les changements sans modifier l'inventaire tant que l'objet entier ne tient
     * pas.
     */
    ItemStack[] plan(ItemStack[] contents, ItemStack reward, String id) {
        ItemStack[] next =
                Arrays.stream(contents)
                        .map(i -> i == null ? null : i.clone())
                        .toArray(ItemStack[]::new);
        int left = reward.getAmount();
        for (int i = 0; i < next.length && left > 0; i++) {
            ItemStack stack = next[i];
            if (stack == null || !stack.isSimilar(reward)) continue;
            int take =
                    Math.min(
                            left,
                            Math.max(0, Math.min(64, stack.getMaxStackSize()) - stack.getAmount()));
            if (take > 0) {
                stack.setAmount(stack.getAmount() + take);
                mark(stack, id);
                left -= take;
            }
        }
        for (int i = 0; i < next.length && left > 0; i++) {
            if (next[i] != null && !next[i].getType().isAir()) continue;
            int take = Math.min(left, reward.getMaxStackSize());
            ItemStack stack = reward.clone();
            stack.setAmount(take);
            mark(stack, id);
            next[i] = stack;
            left -= take;
        }
        return left == 0 ? next : null;
    }

    private void acknowledge(Player player, String id, String lease, boolean automatic) {
        UUID owner = player.getUniqueId();
        var ack = store.finish(owner, id, lease, true);
        acknowledgements.put(owner, ack);
        ack.whenComplete(
                (ignored, error) ->
                        sync(
                                () -> {
                                    if (error != null) {
                                        plugin.getLogger()
                                                .log(
                                                        Level.SEVERE,
                                                        "Validation /claim à réessayer pour "
                                                                + owner,
                                                        error);
                                        if (player.isOnline()) message(player, "sauvegarde");
                                        Bukkit.getScheduler()
                                                .runTaskLater(
                                                        plugin,
                                                        () ->
                                                                acknowledge(
                                                                        player, id, lease,
                                                                        automatic),
                                                        40L);
                                        return;
                                    }
                                    acknowledgements.remove(owner);
                                    finishDelivery(owner, id, automatic);
                                }));
    }

    private void finishDelivery(UUID owner, String id, boolean automatic) {
        if (stopped) return;
        Player current = Bukkit.getPlayer(owner);
        if (current == null) {
            busy.remove(owner);
            return;
        }
        try {
            clearReceipts(current, Set.of(id));
            savePlayer.accept(current);
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE, "Nettoyage du reçu /claim à réessayer", error);
            Bukkit.getScheduler()
                    .runTaskLater(plugin, () -> finishDelivery(owner, id, automatic), 40L);
            return;
        }
        busy.remove(owner);
        message(current, "recupere");
        Inventory top = current.getOpenInventory().getTopInventory();
        if (top != null && top.getHolder() instanceof Holder holder) open(current, holder.page);
        else if (automatic) notifyAndDeliver(owner);
    }

    private void releaseAndReconcile(UUID owner, String id, String lease) {
        if (stopped) return;
        store.finish(owner, id, lease, false)
                .whenComplete(
                        (ignored, error) ->
                                sync(
                                        () -> {
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                Level.SEVERE,
                                                                "Reprise de réservation /claim à"
                                                                    + " réessayer",
                                                                error);
                                                Bukkit.getScheduler()
                                                        .runTaskLater(
                                                                plugin,
                                                                () ->
                                                                        releaseAndReconcile(
                                                                                owner, id, lease),
                                                                40L);
                                                return;
                                            }
                                            busy.remove(owner);
                                            Player current = Bukkit.getPlayer(owner);
                                            if (current != null) reconcile(current);
                                        }));
    }

    private void retryReconcile(UUID owner) {
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (stopped) return;
                            busy.remove(owner);
                            Player current = Bukkit.getPlayer(owner);
                            if (current != null) reconcile(current);
                        },
                        40L);
    }

    private void clearReceipts(Player player, Set<String> delivered) {
        var contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (ItemStack item : contents) {
            String token = receipt(item);
            if (token == null || !delivered.contains(token)) continue;
            var meta = item.getItemMeta();
            meta.getPersistentDataContainer().remove(receipt);
            item.setItemMeta(meta);
            changed = true;
        }
        if (changed) writeStorage.accept(player, contents);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        reconcile(event.getPlayer());
    }

    private void reconcile(Player player) {
        UUID owner = player.getUniqueId();
        Set<String> receipts = new HashSet<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            String token = receipt(item);
            if (token != null) receipts.add(token);
        }
        // Bloquer les déplacements le temps de réconcilier un reçu issu d'une livraison
        // interrompue.
        if (!busy.add(owner)) return;
        store.delivered(owner, receipts)
                .whenComplete(
                        (ids, error) ->
                                sync(
                                        () -> {
                                            if (Bukkit.getPlayer(owner) != player
                                                    || !player.isOnline()) {
                                                busy.remove(owner);
                                                Player current = Bukkit.getPlayer(owner);
                                                if (current != null) reconcile(current);
                                                return;
                                            }
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                Level.SEVERE,
                                                                "Reprise de livraison en attente"
                                                                        + " pour "
                                                                        + owner,
                                                                error);
                                                retryReconcile(owner);
                                                return;
                                            }
                                            try {
                                                clearReceipts(player, ids);
                                            } catch (RuntimeException failure) {
                                                plugin.getLogger()
                                                        .log(
                                                                Level.SEVERE,
                                                                "Reprise des reçus /claim à"
                                                                    + " réessayer",
                                                                failure);
                                                retryReconcile(owner);
                                                return;
                                            }
                                            receipts.removeAll(ids);
                                            if (!receipts.isEmpty()) player.closeInventory();
                                            busy.remove(owner);
                                            if (!receipts.isEmpty())
                                                deliver(player, receipts.iterator().next(), true);
                                            else notifyAndDeliver(owner);
                                        }));
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        opening.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        opening.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (busy.contains(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.owner.equals(player.getUniqueId())) return;
        if (!event.isLeftClick() && !event.isRightClick()) return;
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < holder.entries.size())
            deliver(player, holder.entries.get(slot).id(), false);
        else if (slot == 49)
            fr.cliker.clikerv2.runtime.ServerTasks.run(plugin, () -> player.closeInventory());
        else if (slot == 45 && holder.page > 0) open(player, holder.page - 1);
        else if (slot == 53) open(player, holder.page + 1);
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (busy.contains(event.getWhoClicked().getUniqueId())
                || event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void opening(InventoryOpenEvent event) {
        if (busy.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interactEntity(PlayerInteractEntityEvent event) {
        if (busy.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void drop(PlayerDropItemEvent event) {
        if (busy.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void swap(PlayerSwapHandItemsEvent event) {
        if (busy.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void use(PlayerInteractEvent event) {
        if (busy.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void command(PlayerCommandPreprocessEvent event) {
        if (busy.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            message(event.getPlayer(), "traitement");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void attacking(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && busy.contains(player.getUniqueId()))
            event.setCancelled(true);
    }

    @EventHandler
    public void protect(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && busy.contains(p.getUniqueId()))
            event.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) open(p, 0);
        else sender.sendMessage("Commande réservée aux joueurs.");
        return true;
    }

    private void message(Player player, String key) {
        player.sendMessage(DailySettings.color(config.getString("messages." + key, key)));
    }

    private void sync(Runnable task) {
        if (!stopped && plugin.isEnabled())
            fr.cliker.clikerv2.runtime.ServerTasks.run(plugin, task);
    }

    public void shutdown() {
        stopped = true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof Holder) p.closeInventory();
        }
        try {
            CompletableFuture.allOf(acknowledgements.values().toArray(CompletableFuture[]::new))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            "Validations /claim encore en attente : conserver les reçus PDC",
                            e);
        }
        HandlerList.unregisterAll(this);
        plugin.getServer().getServicesManager().unregister(ClaimService.class, this);
    }

    private static final class Holder implements InventoryHolder {
        private final UUID owner;
        private final int page;
        private final List<MailStore.Entry> entries;
        private Inventory inventory;

        private Holder(UUID owner, int page, List<MailStore.Entry> entries) {
            this.owner = owner;
            this.page = page;
            this.entries = entries;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
