package fr.cliker.clikerv2.upgrade;

import fr.cliker.clikerv2.economy.CurrencyType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Consumer;

public final class UpgradeQuantityMenu implements Listener {
    private static final int[] QUANTITIES = {1, 5, 10, 50, 0};
    private static final int[] SLOTS = {10, 11, 12, 13, 14};
    private final JavaPlugin plugin;
    private final UpgradeManager manager;
    private final Consumer<Player> back;
    private final fr.cliker.clikerv2.ui.ViewRequests requests;

    public UpgradeQuantityMenu(JavaPlugin plugin, UpgradeManager manager, Consumer<Player> back) {
        this.plugin = plugin;
        this.manager = manager;
        this.back = back;
        requests = new fr.cliker.clikerv2.ui.ViewRequests(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, UpgradeType type) {
        var request = requests.begin(player);
        manager.gems(player)
                .whenComplete(
                        (balance, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (error != null
                                                    || !requests.complete(player, request)
                                                    || !player.hasPermission("cliker.upgrade.use"))
                                                return;
                                            var view = new View(player.getUniqueId(), type);
                                            view.inventory =
                                                    Bukkit.createInventory(
                                                            view,
                                                            27,
                                                            Component.text(
                                                                    "Quantité — " + type.label(),
                                                                    NamedTextColor.DARK_GREEN));
                                            refresh(player, view, balance);
                                            player.openInventory(view.inventory);
                                        }));
    }

    static long cost(int level, int quantity) {
        if (quantity <= 0 || level + quantity > UpgradeType.MAX_LEVEL) return -1;
        long total = 0;
        for (int next = level + 1; next <= level + quantity; next++)
            total = Math.addExact(total, Math.multiplyExact(UpgradeType.costForLevel(next), 10));
        return total;
    }

    private void refresh(Player player, View view, long balance) {
        int level = manager.getLevel(player.getUniqueId(), view.type);
        view.allowed.clear();
        for (int i = 0; i < QUANTITIES.length; i++) {
            int q = QUANTITIES[i], count = q;
            long price;
            if (q == 0) {
                count = 0;
                price = 0;
                for (int next = level + 1; next <= UpgradeType.MAX_LEVEL; next++) {
                    long step = UpgradeType.costForLevel(next) * 10;
                    if (step > balance - price) break;
                    price += step;
                    count++;
                }
            } else price = cost(level, q);
            boolean allowed = count > 0 && price >= 0 && price <= balance;
            if (allowed) view.allowed.add(SLOTS[i]);
            var item = new ItemStack(allowed ? Material.LIME_DYE : Material.BARRIER);
            var meta = item.getItemMeta();
            meta.displayName(
                    Component.text(
                            q == 0 ? "Maximum abordable" : "X" + q,
                            allowed ? NamedTextColor.GREEN : NamedTextColor.RED));
            var lore = new ArrayList<Component>();
            lore.add(
                    Component.text(
                            "Niveau : " + level + "/" + UpgradeType.MAX_LEVEL,
                            NamedTextColor.GRAY));
            if (price >= 0)
                lore.add(
                        Component.text(
                                "Coût : "
                                        + CurrencyType.GEMMES_SOMBRES.format(price)
                                        + " Gemmes Sombres",
                                NamedTextColor.DARK_PURPLE));
            lore.add(
                    Component.text(
                            "Solde : "
                                    + CurrencyType.GEMMES_SOMBRES.format(balance)
                                    + " Gemmes Sombres",
                            NamedTextColor.GRAY));
            lore.add(
                    Component.text(
                            allowed
                                    ? "Nouveau niveau : " + (level + count)
                                    : price < 0
                                            ? "Pas assez de niveaux restants."
                                            : "Solde insuffisant ou niveau maximal.",
                            allowed ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(
                    Component.text(
                            allowed
                                    ? "Clic gauche pour acheter " + count + " niveau(x)."
                                    : "Achat bloqué.",
                            allowed ? NamedTextColor.YELLOW : NamedTextColor.RED));
            meta.lore(lore);
            item.setItemMeta(meta);
            view.inventory.setItem(SLOTS[i], item);
        }
        var arrow = new ItemStack(Material.ARROW);
        var meta = arrow.getItemMeta();
        meta.displayName(Component.text("Retour aux améliorations", NamedTextColor.GREEN));
        arrow.setItemMeta(meta);
        view.inventory.setItem(22, arrow);
        fr.cliker.clikerv2.ui.MenuIcons.decorate(view.inventory);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !view.owner.equals(player.getUniqueId())
                || !player.hasPermission("cliker.upgrade.use")
                || event.getClick() != ClickType.LEFT
                || event.getClickedInventory() != view.inventory
                || view.pending) return;
        if (event.getRawSlot() == 22) {
            fr.cliker.clikerv2.runtime.ServerTasks.run(
                    plugin,
                    () -> {
                        if (player.isOnline()
                                && player.getOpenInventory().getTopInventory() == view.inventory)
                            back.accept(player);
                    });
            return;
        }
        if (!view.allowed.contains(event.getRawSlot())) return;
        int quantity = -1;
        for (int i = 0; i < SLOTS.length; i++)
            if (SLOTS[i] == event.getRawSlot()) quantity = QUANTITIES[i];
        if (quantity < 0) return;
        view.pending = true;
        manager.purchase(
                player,
                view.type,
                quantity,
                () ->
                        manager.gems(player)
                                .whenComplete(
                                        (balance, error) ->
                                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                                        plugin,
                                                        () -> {
                                                            view.pending = false;
                                                            if (error == null
                                                                    && player.isOnline()
                                                                    && player.getOpenInventory()
                                                                                    .getTopInventory()
                                                                            == view.inventory)
                                                                refresh(player, view, balance);
                                                        })));
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true);
    }

    private static final class View implements InventoryHolder {
        final UUID owner;
        final UpgradeType type;
        final Set<Integer> allowed = new HashSet<>();
        Inventory inventory;
        boolean pending;

        View(UUID owner, UpgradeType type) {
            this.owner = owner;
            this.type = type;
        }

        public Inventory getInventory() {
            return inventory;
        }
    }
}
