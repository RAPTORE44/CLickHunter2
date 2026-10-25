package fr.cliker.clikerv2.outpost;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.npc.ZnpcsNpc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Boutique de construction : débit et livraison en attente sont une transaction SQLite. */
public final class OutpostBlocks implements Listener {
    private final JavaPlugin plugin;
    private final fr.cliker.clikerv2.features.Menus blockMenus;
    private final DatabaseManager db;
    private final YamlConfiguration config;
    private final World world;
    private final List<Material> materials =
            Arrays.stream(Material.values())
                    .filter(m -> !m.isLegacy() && m.isBlock() && m.isItem() && !m.isAir())
                    .sorted(Comparator.comparing(Enum::name))
                    .toList();
    private final Map<UUID, ZnpcsNpc> npcs = new HashMap<>();
    private final Map<UUID, Long> moves = new HashMap<>(), notices = new HashMap<>();
    private final Set<UUID> buying = new HashSet<>(), delivering = new HashSet<>();
    private final NamespacedKey receipt;
    private BukkitTask task;
    private boolean ready, stopped;

    public OutpostBlocks(
            JavaPlugin plugin, DatabaseManager db, YamlConfiguration config, World world) {
        this.plugin = plugin;
        blockMenus = new fr.cliker.clikerv2.features.Menus(plugin);
        this.db = db;
        this.config = config;
        this.world = world;
        receipt = new NamespacedKey(plugin, "outpost_shop_receipt");
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.execute(
                                        "CREATE TABLE IF NOT EXISTS outpost_block_deliveries(id"
                                            + " TEXT PRIMARY KEY,owner TEXT NOT NULL,material TEXT"
                                            + " NOT NULL,amount INTEGER NOT NULL CHECK(amount"
                                            + " BETWEEN 1 AND 64),created INTEGER NOT NULL)");
                                s.execute(
                                        "CREATE INDEX IF NOT EXISTS outpost_block_delivery_owner ON"
                                                + " outpost_block_deliveries(owner,created)");
                                return null;
                            } catch (java.sql.SQLException e) {
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        })
                .whenComplete(
                        (done, error) ->
                                sync(
                                        () -> {
                                            if (error != null)
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Boutique de blocs indisponible",
                                                                error);
                                            else ready = true;
                                        }));
        task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (ready) for (var p : world.getPlayers()) pump(p);
                                },
                                40,
                                40);
    }

    private void sync(Runnable run) {
        fr.cliker.clikerv2.runtime.ServerTasks.run(
                plugin,
                () -> {
                    if (!stopped) run.run();
                });
    }

    public void ensureNpc(OutpostStore.Plot plot) {
        if (stopped
                || npcs.containsKey(plot.owner())
                || !Bukkit.getPluginManager().isPluginEnabled("ZNPCsPlus")) return;
        var at =
                fr.cliker.clikerv2.config.YamlFiles.location(
                        config, "marchands." + plot.owner() + ".position");
        if (at == null || at.getWorld() != world)
            at = new Location(world, plot.x() + 3.5, 65, plot.z() + 0.5, 90, 0);
        var settings = new YamlConfiguration();
        settings.set("pnj.actif", true);
        settings.set("pnj.type", config.getString("boutique-blocs.type", "villager"));
        settings.set("pnj.skin", config.getString("boutique-blocs.skin", ""));
        settings.set("pnj.titre", "&aMarchand de construction");
        settings.set("pnj.sous-titre", "&7Clic droit pour acheter des blocs");
        fr.cliker.clikerv2.config.YamlFiles.location(settings, "pnj.position", at);
        var npc =
                new ZnpcsNpc(
                        plugin,
                        settings,
                        () -> {},
                        p -> open(p, 0),
                        "outpost-blocks-" + plot.owner(),
                        "Construction");
        npcs.put(plot.owner(), npc);
        npc.start();
    }

    public void retainNpcs(Set<UUID> visible) {
        var iterator = npcs.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!visible.contains(entry.getKey())) {
                entry.getValue().shutdown();
                iterator.remove();
            }
        }
    }

    public void move(Player player, OutpostStore.Plot plot) {
        long now = System.nanoTime();
        if (now - moves.getOrDefault(player.getUniqueId(), now - 4_000_000_000L) < 3_000_000_000L) {
            player.sendMessage("Patiente trois secondes entre deux déplacements.");
            return;
        }
        String path = "marchands." + plot.owner() + ".position";
        Object old = config.get(path);
        fr.cliker.clikerv2.config.YamlFiles.location(config, path, player.getLocation());
        try {
            config.save(fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "avantpostes.yml"));
        } catch (java.io.IOException error) {
            config.set(path, old);
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Déplacement de marchand non sauvegardé",
                            error);
            player.sendMessage("Déplacement impossible. Réessaie plus tard.");
            return;
        }
        moves.put(player.getUniqueId(), now);
        var npc = npcs.remove(plot.owner());
        if (npc != null) npc.shutdown();
        ensureNpc(plot);
        player.sendMessage(
                Component.text(
                        "Marchand déplacé à ta position et orientation.", NamedTextColor.GREEN));
    }

    private long price(Material material) {
        long value =
                config.getLong(
                        "boutique-blocs.prix." + material.name(),
                        config.getLong("boutique-blocs.prix-defaut", 10));
        return value > 0 && value <= 1_000_000_000_000L ? value : 10;
    }

    private static final List<String> CATEGORIES =
            List.of(
                    "Tous les blocs",
                    "Bois",
                    "Pierre et minerais",
                    "Couleurs et verre",
                    "Nature",
                    "Décoration et mécanismes");

    static int category(Material material) {
        String n = material.name();
        if (n.contains("LOG")
                || n.contains("WOOD")
                || n.contains("PLANK")
                || n.contains("BAMBOO")
                || n.contains("STEM")
                || n.contains("HYPHAE")) return 1;
        if (n.contains("WOOL")
                || n.contains("CONCRETE")
                || n.contains("TERRACOTTA")
                || n.contains("GLASS")
                || n.contains("CARPET")) return 3;
        if (n.contains("STONE")
                || n.contains("ORE")
                || n.contains("DEEPSLATE")
                || n.contains("TUFF")
                || n.contains("ANDESITE")
                || n.contains("GRANITE")
                || n.contains("DIORITE")
                || n.contains("BRICK")
                || n.contains("BASALT")) return 2;
        if (n.contains("DIRT")
                || n.contains("GRASS")
                || n.contains("LEAVES")
                || n.contains("FLOWER")
                || n.contains("SAPLING")
                || n.contains("MOSS")
                || n.contains("SAND")
                || n.contains("CORAL")
                || n.contains("ICE")
                || n.contains("SNOW")) return 4;
        return 5;
    }

    public void open(Player player, int page) {
        if (!ready || player.getWorld() != world || !WorldInventories.readyForBuilder(player)) {
            player.sendMessage("Boutique en cours de chargement.");
            return;
        }
        var menu = blockMenus.create(player, 27, "Blocs — Catégories");
        Material[] icons = {
            Material.CHEST,
            Material.OAK_LOG,
            Material.STONE,
            Material.CYAN_CONCRETE,
            Material.GRASS_BLOCK,
            Material.REDSTONE
        };
        for (int i = 0; i < CATEGORIES.size(); i++) {
            final int choice = i;
            menu.put(
                    10 + i,
                    fr.cliker.clikerv2.features.Menus.item(icons[i], CATEGORIES.get(i)),
                    (who, right) -> openCategory(who, 0, choice));
        }
        menu.show(player);
    }

    private void openCategory(Player player, int page, int category) {
        List<Material> products =
                materials.stream().filter(m -> category == 0 || category(m) == category).toList();
        if (!ready || player.getWorld() != world || !WorldInventories.readyForBuilder(player)) {
            player.sendMessage("Boutique en cours de chargement.");
            return;
        }
        int pages = Math.max(1, (products.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        var view = new View(player.getUniqueId(), page, category, products);
        view.inventory =
                Bukkit.createInventory(
                        view,
                        54,
                        Component.text(
                                CATEGORIES.get(category) + " — " + (page + 1) + "/" + pages,
                                NamedTextColor.DARK_GREEN));
        for (int slot = 0; slot < 45; slot++) {
            int index = page * 45 + slot;
            if (index >= products.size()) break;
            Material material = products.get(index);
            var item = new ItemStack(material);
            var meta = item.getItemMeta();
            meta.lore(
                    List.of(
                            Component.text(
                                    "Prix : " + price(material) + " Écus / bloc",
                                    NamedTextColor.GOLD),
                            Component.text("Gauche : 1 | Droite : une pile", NamedTextColor.YELLOW),
                            Component.text(
                                    "Utilisable uniquement dans les avant-postes.",
                                    NamedTextColor.GRAY)));
            item.setItemMeta(meta);
            view.inventory.setItem(slot, item);
        }
        button(view.inventory, 45, "Page précédente", Material.ARROW);
        button(view.inventory, 48, "Catégories", Material.BOOK);
        button(view.inventory, 49, "Fermer", Material.BARRIER);
        button(view.inventory, 53, "Page suivante", Material.ARROW);
        player.openInventory(view.inventory);
    }

    private static void button(Inventory inventory, int slot, String name, Material type) {
        var item = new ItemStack(type);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GREEN));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !view.owner.equals(player.getUniqueId())
                || player.getWorld() != world
                || event.getClickedInventory() != view.inventory
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT))
            return;
        int slot = event.getRawSlot();
        if (slot == 45 || slot == 53) {
            sync(
                    () -> {
                        if (player.isOnline()
                                && player.getOpenInventory().getTopInventory() == view.inventory)
                            openCategory(player, view.page + (slot == 45 ? -1 : 1), view.category);
                    });
            return;
        }
        if (slot == 48) {
            sync(() -> open(player, 0));
            return;
        }
        if (slot == 49) {
            sync(() -> player.closeInventory());
            return;
        }
        int index = view.page * 45 + slot;
        if (slot < 0
                || slot >= 45
                || index >= view.products.size()
                || !buying.add(player.getUniqueId())) return;
        Material material = view.products.get(index);
        int count =
                event.getClick() == ClickType.RIGHT ? new ItemStack(material).getMaxStackSize() : 1;
        long cost = Math.multiplyExact(price(material), count);
        purchase(player.getUniqueId(), material, count, cost)
                .whenComplete(
                        (paid, error) ->
                                sync(
                                        () -> {
                                            buying.remove(player.getUniqueId());
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Achat de blocs impossible",
                                                                error);
                                                if (player.isOnline())
                                                    player.sendMessage(
                                                            "Achat indisponible. Réessaie plus"
                                                                    + " tard.");
                                                return;
                                            }
                                            if (player.isOnline())
                                                player.sendMessage(
                                                        Component.text(
                                                                Boolean.TRUE.equals(paid)
                                                                        ? "Achat payé : livraison"
                                                                              + " dans ton"
                                                                              + " inventaire"
                                                                              + " d’avant-poste dès"
                                                                              + " qu’une place est"
                                                                              + " libre."
                                                                        : "Écus insuffisants :"
                                                                                + " aucun achat"
                                                                                + " effectué.",
                                                                Boolean.TRUE.equals(paid)
                                                                        ? NamedTextColor.GREEN
                                                                        : NamedTextColor.RED));
                                            if (Boolean.TRUE.equals(paid)
                                                    && fr.cliker.clikerv2.runtime.PlayerSessions
                                                            .isCurrent(player)) pump(player);
                                        }));
    }

    public CompletableFuture<Boolean> purchase(
            UUID owner, Material material, int count, long cost) {
        if (!materials.contains(material) || count < 1 || count > 64 || cost < 1)
            throw new IllegalArgumentException("Achat de blocs invalide");
        String id = UUID.randomUUID().toString();
        return db.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        try (var s =
                                c.prepareStatement(
                                        "UPDATE player_balances SET ecus=ecus-? WHERE uuid=? AND"
                                                + " ecus>=?")) {
                            s.setLong(1, cost);
                            s.setString(2, owner.toString());
                            s.setLong(3, cost);
                            if (s.executeUpdate() != 1) {
                                c.rollback();
                                return false;
                            }
                        }
                        try (var s =
                                c.prepareStatement(
                                        "INSERT INTO"
                                            + " outpost_block_deliveries(id,owner,material,amount,created)"
                                            + " VALUES(?,?,?,?,?)")) {
                            s.setString(1, id);
                            s.setString(2, owner.toString());
                            s.setString(3, material.name());
                            s.setInt(4, count);
                            s.setLong(5, System.currentTimeMillis());
                            s.executeUpdate();
                        }
                        c.commit();
                        return true;
                    } catch (java.sql.SQLException error) {
                        try {
                            c.rollback();
                        } catch (java.sql.SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(error);
                    }
                });
    }

    private record Delivery(String id, Material material, int count) {}

    void pump(Player player) {
        if (!ready
                || player.getWorld() != world
                || !WorldInventories.readyForBuilder(player)
                || !delivering.add(player.getUniqueId())) return;
        UUID owner = player.getUniqueId();
        db.submit(
                        c -> {
                            try (var s =
                                    c.prepareStatement(
                                            "SELECT id,material,amount FROM"
                                                + " outpost_block_deliveries WHERE owner=? ORDER BY"
                                                + " created,id LIMIT 1")) {
                                s.setString(1, owner.toString());
                                try (var rs = s.executeQuery()) {
                                    return rs.next()
                                            ? new Delivery(
                                                    rs.getString(1),
                                                    Material.valueOf(rs.getString(2)),
                                                    rs.getInt(3))
                                            : null;
                                }
                            } catch (java.sql.SQLException e) {
                                throw new DatabaseManager.DatabaseException(e);
                            }
                        })
                .whenComplete(
                        (delivery, error) ->
                                sync(
                                        () -> {
                                            if (error != null
                                                    || delivery == null
                                                    || !fr.cliker.clikerv2.runtime.PlayerSessions
                                                            .isCurrent(player)
                                                    || player.getWorld() != world
                                                    || !WorldInventories.readyForBuilder(player)) {
                                                delivering.remove(owner);
                                                if (error != null)
                                                    plugin.getLogger()
                                                            .log(
                                                                    java.util.logging.Level.SEVERE,
                                                                    "Lecture d’achat de blocs"
                                                                            + " impossible",
                                                                    error);
                                                return;
                                            }
                                            var data = player.getPersistentDataContainer();
                                            String last =
                                                    data.get(receipt, PersistentDataType.STRING);
                                            var before =
                                                    Arrays.stream(
                                                                    player.getInventory()
                                                                            .getContents())
                                                            .map(
                                                                    item ->
                                                                            item == null
                                                                                    ? null
                                                                                    : item.clone())
                                                            .toArray(ItemStack[]::new);
                                            if (!delivery.id.equals(last)) {
                                                var item = new ItemStack(delivery.material);
                                                int capacity = 0;
                                                for (var stack :
                                                        player.getInventory().getStorageContents())
                                                    if (stack == null || stack.getType().isAir())
                                                        capacity += item.getMaxStackSize();
                                                    else if (stack.isSimilar(item))
                                                        capacity +=
                                                                Math.max(
                                                                        0,
                                                                        item.getMaxStackSize()
                                                                                - stack
                                                                                        .getAmount());
                                                if (capacity < delivery.count) {
                                                    delivering.remove(owner);
                                                    long now = System.nanoTime();
                                                    if (now
                                                                    - notices.getOrDefault(
                                                                            owner,
                                                                            now - 11_000_000_000L)
                                                            > 10_000_000_000L) {
                                                        notices.put(owner, now);
                                                        player.sendMessage(
                                                                "Fais de la place : tes blocs"
                                                                        + " achetés attendent dans"
                                                                        + " l’avant-poste.");
                                                    }
                                                    return;
                                                }
                                                item.setAmount(delivery.count);
                                                player.getInventory().addItem(item);
                                                data.set(
                                                        receipt,
                                                        PersistentDataType.STRING,
                                                        delivery.id);
                                            }
                                            try {
                                                player.saveData();
                                            } catch (RuntimeException failure) {
                                                player.getInventory().setContents(before);
                                                if (last == null) data.remove(receipt);
                                                else
                                                    data.set(
                                                            receipt,
                                                            PersistentDataType.STRING,
                                                            last);
                                                delivering.remove(owner);
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Livraison de blocs non"
                                                                        + " sauvegardée",
                                                                failure);
                                                return;
                                            }
                                            db.submit(
                                                            c -> {
                                                                try (var s =
                                                                        c.prepareStatement(
                                                                                "DELETE FROM"
                                                                                    + " outpost_block_deliveries"
                                                                                    + " WHERE id=?"
                                                                                    + " AND owner=?")) {
                                                                    s.setString(1, delivery.id);
                                                                    s.setString(
                                                                            2, owner.toString());
                                                                    s.executeUpdate();
                                                                    return null;
                                                                } catch (java.sql.SQLException e) {
                                                                    throw new DatabaseManager
                                                                            .DatabaseException(e);
                                                                }
                                                            })
                                                    .whenComplete(
                                                            (done, ackError) ->
                                                                    sync(
                                                                            () -> {
                                                                                delivering.remove(
                                                                                        owner);
                                                                                if (ackError
                                                                                        != null)
                                                                                    plugin.getLogger()
                                                                                            .log(
                                                                                                    java
                                                                                                            .util
                                                                                                            .logging
                                                                                                            .Level
                                                                                                            .SEVERE,
                                                                                                    "Acquittement"
                                                                                                        + " de blocs"
                                                                                                        + " à reprendre",
                                                                                                    ackError);
                                                                            }));
                                        }));
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }

    @EventHandler
    public void quit(org.bukkit.event.player.PlayerQuitEvent e) {
        moves.remove(e.getPlayer().getUniqueId());
        notices.remove(e.getPlayer().getUniqueId());
    }

    public void shutdown() {
        stopped = true;
        if (task != null) task.cancel();
        npcs.values().forEach(ZnpcsNpc::shutdown);
        npcs.clear();
        buying.clear();
        delivering.clear();
        HandlerList.unregisterAll(this);
    }

    private static final class View implements InventoryHolder {
        final UUID owner;
        final int page;
        final int category;
        final List<Material> products;
        Inventory inventory;

        View(UUID owner, int page, int category, List<Material> products) {
            this.category = category;
            this.products = products;
            this.owner = owner;
            this.page = page;
        }

        public Inventory getInventory() {
            return inventory;
        }
    }
}
