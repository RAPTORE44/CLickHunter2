package fr.cliker.clikerv2.bag;

import fr.cliker.clikerv2.essence.EssenceItem;
import fr.cliker.clikerv2.features.Menus;
import fr.cliker.clikerv2.rewards.ClaimService;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Plans calculés sur des copies ; aucun retrait avant la confirmation et les contrôles. */
public final class BagOrganizer {
    private final BagManager bags;
    private final Menus menus;

    public BagOrganizer(JavaPlugin plugin, BagManager bags) {
        this.bags = bags;
        menus = new Menus(plugin);
    }

    static ItemStack[] copy(ItemStack[] source) {
        return Arrays.stream(source)
                .map(i -> i == null ? null : i.clone())
                .toArray(ItemStack[]::new);
    }

    static int insert(ItemStack[] destination, ItemStack item) {
        int left = item.getAmount();
        for (var target : destination)
            if (target != null
                    && target.isSimilar(item)
                    && Bukkit.getItemFactory().equals(target.getItemMeta(), item.getItemMeta())) {
                int add =
                        Math.min(left, Math.max(0, target.getMaxStackSize() - target.getAmount()));
                target.setAmount(target.getAmount() + add);
                left -= add;
            }
        for (int i = 0; i < destination.length && left > 0; i++)
            if (destination[i] == null || destination[i].getType().isAir()) {
                var part = item.clone();
                int amount = Math.min(left, item.getMaxStackSize());
                part.setAmount(amount);
                destination[i] = part;
                left -= amount;
            }
        return left;
    }

    public static ItemStack[] sorted(ItemStack[] before) {
        var compact = new ItemStack[before.length];
        for (var item : before)
            if (item != null && !item.getType().isAir()) {
                if (insert(compact, item) != 0)
                    throw new IllegalStateException(
                            "Tri sans place : aucune modification effectuée");
            }
        Arrays.sort(
                compact,
                Comparator.nullsLast(
                        Comparator.comparing((ItemStack i) -> i.getType().name())
                                .thenComparing(
                                        i ->
                                                i.hasItemMeta() && i.getItemMeta().hasDisplayName()
                                                        ? net.kyori.adventure.text.serializer.plain
                                                                .PlainTextComponentSerializer
                                                                .plainText()
                                                                .serialize(
                                                                        i.getItemMeta()
                                                                                .displayName())
                                                        : "")));
        return compact;
    }

    public void open(Player p, int bag) {
        p.closeInventory();
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)
                || !bags.isLoaded(p.getUniqueId())
                || bag < 0
                || bag >= bags.capacity(p)) return;
        var view = menus.create(p, 27, "Gestion du sac " + (bag + 1));
        view.put(
                11,
                Menus.item(
                        Material.CHEST,
                        "Ranger et regrouper les piles",
                        "Aperçu puis confirmation ; aucun objet supprimé."),
                (who, r) -> preview(who, bag, false));
        view.put(
                15,
                Menus.item(
                        Material.HOPPER,
                        "Déposer mes ressources",
                        "Essences du monde clicker, y compris compressées.",
                        "Le surplus reste dans ton inventaire."),
                (who, r) -> preview(who, bag, true));
        view.put(
                22,
                Menus.item(Material.ARROW, "Retour aux sacs"),
                (who, r) -> who.performCommand("sac"));
        view.show(p);
    }

    private void preview(Player p, int bag, boolean deposit) {
        if (!bags.isLoaded(p.getUniqueId()) || bag >= bags.capacity(p)) return;
        var before = bags.contents(p.getUniqueId(), bag);
        var inventory = copy(p.getInventory().getStorageContents());
        var after = deposit ? copy(before) : sorted(before);
        var remaining = copy(inventory);
        int moved = 0;
        if (deposit)
            for (int i = 0; i < remaining.length; i++) {
                var item = remaining[i];
                if (item == null || EssenceItem.tierOf(item) == null) continue;
                int left = insert(after, item);
                moved += item.getAmount() - left;
                if (left == 0) remaining[i] = null;
                else remaining[i].setAmount(left);
            }
        var view = menus.create(p, 27, "Confirmer la gestion du sac");
        int total = moved;
        view.put(
                13,
                Menus.item(
                        Material.BOOK,
                        deposit ? "Dépôt de " + moved + " ressources" : "Tri des objets similaires",
                        "Les objets différents gardent leurs propriétés.",
                        "Confirme après avoir vérifié l’opération."),
                null);
        view.put(
                11,
                Menus.item(Material.LIME_DYE, "Confirmer"),
                (who, r) -> {
                    var claims = Bukkit.getServicesManager().load(ClaimService.class);
                    if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(who)
                            || !bags.isLoaded(who.getUniqueId())
                            || bag >= bags.capacity(who)
                            || claims != null && claims.isDelivering(who.getUniqueId())
                            || !Arrays.equals(before, bags.contents(who.getUniqueId(), bag))
                            || !Arrays.equals(inventory, who.getInventory().getStorageContents())) {
                        who.sendMessage(
                                "Les objets ont changé ou une livraison est en cours. Recommence"
                                        + " l’aperçu.");
                        open(who, bag);
                        return;
                    }
                    try {
                        bags.transfer(who, bag, after, deposit ? remaining : inventory);
                    } catch (RuntimeException error) {
                        who.sendMessage(
                                "Sauvegarde impossible : transfert annulé, tes objets sont"
                                        + " conservés.");
                        open(who, bag);
                        return;
                    }
                    who.sendMessage(
                            deposit
                                    ? total + " ressources déposées dans le sac " + (bag + 1) + "."
                                    : "Sac rangé.");
                    open(who, bag);
                });
        view.put(15, Menus.item(Material.RED_DYE, "Annuler"), (who, r) -> open(who, bag));
        view.show(p);
    }
}
