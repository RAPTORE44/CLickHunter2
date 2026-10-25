package fr.cliker.clikerv2.gear;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.rewards.ClaimService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Consumer;

/** Un objet est réservé par opération, puis rendu par la même filière persistante que /claim. */
final class GearTransactions {
    private static final Set<Inventory> BUSY = Collections.newSetFromMap(new WeakHashMap<>());

    private GearTransactions() {}

    static boolean busy(Inventory inventory) {
        return BUSY.contains(inventory);
    }

    static void apply(
            JavaPlugin plugin,
            EconomyService economy,
            Player player,
            Inventory inventory,
            int slot,
            CurrencyType currency,
            long price,
            Consumer<ItemStack> change,
            Runnable refresh) {
        ItemStack input = inventory.getItem(slot);
        if (input == null || BUSY.contains(inventory)) return;
        var delivery = Bukkit.getServicesManager().load(ClaimService.class);
        if (delivery == null) {
            player.sendMessage(
                    Component.text(
                            "Transformation temporairement indisponible. Ton objet est conservé.",
                            NamedTextColor.RED));
            return;
        }
        ItemStack original = input.clone(), output = input.clone();
        String before, after;
        try {
            change.accept(output);
            before = ClaimService.serialize(original);
            after = ClaimService.serialize(output);
        } catch (RuntimeException error) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Préparation de transformation impossible",
                            error);
            player.sendMessage(
                    Component.text(
                            "Transformation impossible. Ton objet est conservé.",
                            NamedTextColor.RED));
            return;
        }
        BUSY.add(inventory);
        inventory.setItem(slot, null);
        UUID owner = player.getUniqueId();
        String name = player.getName();
        java.util.concurrent.CompletableFuture<Boolean> purchase;
        try {
            purchase =
                    economy.transformItem(
                            owner,
                            name,
                            "gear:" + UUID.randomUUID(),
                            currency,
                            price,
                            before,
                            after);
        } catch (RuntimeException error) {
            inventory.setItem(slot, original);
            BUSY.remove(inventory);
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Démarrage de transformation impossible",
                            error);
            player.sendMessage(
                    Component.text(
                            "Transformation indisponible. Ton objet est conservé.",
                            NamedTextColor.RED));
            return;
        }
        purchase.whenComplete(
                (paid, error) -> {
                    if (!plugin.isEnabled()) {
                        return;
                    }
                    fr.cliker.clikerv2.runtime.ServerTasks.run(
                            plugin,
                            () -> {
                                BUSY.remove(inventory);
                                if (error != null) {
                                    delivery.give(owner, name, original);
                                    plugin.getLogger()
                                            .log(
                                                    java.util.logging.Level.SEVERE,
                                                    "Transformation impossible :" + " objet rendu",
                                                    error);
                                } else delivery.notifyAndDeliver(owner);
                                if (player.isOnline()) {
                                    boolean success = error == null && Boolean.TRUE.equals(paid);
                                    String status =
                                            success
                                                    ? "Transformation effectuée !"
                                                            + " Survole ce message"
                                                            + " pour voir le"
                                                            + " résultat."
                                                    : error == null
                                                            ? "Solde insuffisant."
                                                                    + " Ton objet est"
                                                                    + " rendu."
                                                            : "Transformation"
                                                                    + " impossible."
                                                                    + " Ton objet est"
                                                                    + " rendu.";
                                    player.sendMessage(
                                            Component.text(
                                                            status,
                                                            success
                                                                    ? NamedTextColor.GREEN
                                                                    : NamedTextColor.RED)
                                                    .hoverEvent(
                                                            (success ? output : original)
                                                                    .asHoverEvent()));
                                    player.sendMessage(
                                            Component.text(
                                                    "Objet livré dans ton"
                                                            + " inventaire ou"
                                                            + " disponible dans"
                                                            + " /claim.",
                                                    NamedTextColor.GRAY));
                                }
                                if (player.isOnline()
                                        && player.getOpenInventory().getTopInventory() == inventory)
                                    refresh.run();
                            });
                });
    }
}
