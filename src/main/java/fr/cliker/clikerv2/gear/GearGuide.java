package fr.cliker.clikerv2.gear;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Consumer;

/** Documentation calculée depuis les mêmes tables que les tirages. Aucun objet déposé ici. */
public final class GearGuide implements Listener {
    private final JavaPlugin plugin;
    private final String kind;
    private final Consumer<Player> back;
    private final List<Double> weights;

    public GearGuide(JavaPlugin plugin, String kind, Consumer<Player> back, List<Double> weights) {
        this.plugin = plugin;
        this.kind = kind;
        this.back = back;
        this.weights = List.copyOf(weights);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private static String category(EnchantType.Category c) {
        return switch (c) {
            case SWORD -> "Épée";
            case ARMOR -> "Armure";
            case BOTH -> "Épée et armure";
        };
    }

    public static ItemStack book() {
        return item(
                Material.BOOK,
                "Guide des effets",
                List.of(
                        "Types, niveaux, probabilités et particularités",
                        "Clic gauche pour consulter"));
    }

    private static ItemStack item(Material m, String title, List<String> lore) {
        var i = new ItemStack(m);
        var meta = i.getItemMeta();
        meta.displayName(Component.text(title));
        meta.lore(lore.stream().map(Component::text).toList());
        i.setItemMeta(meta);
        return i;
    }

    public void open(Player p) {
        var view = new View(p.getUniqueId(), this);
        view.inventory = Bukkit.createInventory(view, 54, Component.text("Guide — " + kind));
        int slot = 0;
        if (kind.equals("Enchantements"))
            for (var type : EnchantType.values()) {
                var lore = new ArrayList<String>();
                lore.add(type.description());
                lore.add("Équipement : " + category(type.category()));
                lore.add("Type tiré uniformément parmi les effets compatibles.");
                if (type.category() != EnchantType.Category.ARMOR)
                    lore.add(
                            String.format(
                                    Locale.FRANCE,
                                    "Épée : %.2f %%",
                                    100.0 / EnchantType.of(EnchantType.Category.SWORD).length));
                if (type.category() != EnchantType.Category.SWORD)
                    lore.add(
                            String.format(
                                    Locale.FRANCE,
                                    "Armure : %.2f %%",
                                    100.0 / EnchantType.of(EnchantType.Category.ARMOR).length));
                for (int level = 1; level <= 5; level++)
                    lore.add(
                            "Niveau "
                                    + level
                                    + " : "
                                    + weights.get(level - 1)
                                    + " % (après tirage du type)");
                lore.add("Remplace l’enchantement précédent.");
                lore.add("Le bonus dépend du tier de l’équipement.");
                view.inventory.setItem(
                        slot++,
                        item(
                                Material.ENCHANTED_BOOK,
                                type.label() + " — " + category(type.category()),
                                lore));
            }
        else if (kind.equals("Reforges"))
            for (var type : ReforgeType.values()) {
                long count =
                        Arrays.stream(ReforgeType.values())
                                .filter(t -> t.category() == type.category())
                                .count();
                view.inventory.setItem(
                        slot++,
                        item(
                                Material.ANVIL,
                                type.label() + " — " + category(type.category()),
                                List.of(
                                        type.description(),
                                        String.format(
                                                Locale.FRANCE,
                                                "Probabilité : %.2f %%",
                                                100.0 / count),
                                        "Un seul préfixe actif, sans niveau.",
                                        "Remplace la reforge précédente.",
                                        "Universel cumule tous les effets compatibles.")));
            }
        else
            for (var type : RuneType.values()) {
                var lore = new ArrayList<String>();
                lore.add(type.description());
                lore.add("Équipement : " + category(type.category()));
                if (type.category() != EnchantType.Category.ARMOR)
                    lore.add(
                            String.format(
                                    Locale.FRANCE,
                                    "Type sur épée : %.2f %%",
                                    100.0 / RuneType.of(EnchantType.Category.SWORD).length));
                if (type.category() != EnchantType.Category.SWORD)
                    lore.add(
                            String.format(
                                    Locale.FRANCE,
                                    "Type sur armure : %.2f %%",
                                    100.0 / RuneType.of(EnchantType.Category.ARMOR).length));
                lore.add("Niveaux 1 à 15 : chacun 6,67 % après tirage du type.");
                lore.add("Remplace les runes précédentes.");
                lore.add("Universel applique les effets compatibles au même niveau.");
                view.inventory.setItem(
                        slot++,
                        item(
                                Material.AMETHYST_SHARD,
                                type.label() + " — " + category(type.category()),
                                lore));
            }
        view.inventory.setItem(49, item(Material.ARROW, "Retour à la table", List.of()));
        p.openInventory(view.inventory);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof View v) || v.guide != this)
            return;
        e.setCancelled(true);
        if (e.getWhoClicked() instanceof Player p
                && p.getUniqueId().equals(v.owner)
                && e.getRawSlot() == 49
                && e.getClick() == ClickType.LEFT)
            fr.cliker.clikerv2.runtime.ServerTasks.run(
                    plugin,
                    () -> {
                        if (p.getOpenInventory().getTopInventory() == v.inventory) back.accept(p);
                    });
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }

    private static class View implements InventoryHolder {
        final UUID owner;
        final GearGuide guide;
        Inventory inventory;

        View(UUID id, GearGuide guide) {
            owner = id;
            this.guide = guide;
        }

        public Inventory getInventory() {
            return inventory;
        }
    }
}
