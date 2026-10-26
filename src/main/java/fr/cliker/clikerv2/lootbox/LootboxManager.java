package fr.cliker.clikerv2.lootbox;

import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.hunting.*;
import fr.cliker.clikerv2.rewards.ClaimService;
import fr.cliker.clikerv2.rewards.OutgoingItems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Une pile ouverte = un crédit atomique, un message et une livraison d'XP regroupée. */
public final class LootboxManager implements Listener {
    private final JavaPlugin plugin;
    private final RewardLedger ledger;
    private final ClaimService claims;
    private final Set<UUID> pending = new HashSet<>();

    public LootboxManager(JavaPlugin plugin, RewardLedger ledger, ClaimService claims) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.claims = claims;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                        && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        if (event.useItemInHand() == Event.Result.DENY) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        LootboxTier tier = LootboxItem.tierOf(hand);
        if (tier == null) return;
        event.setCancelled(true);
        UUID owner = player.getUniqueId();
        if (claims.isDelivering(owner) || !pending.add(owner)) return;
        int amount = player.isSneaking() ? hand.getAmount() : 1;
        Gains gains;
        try {
            gains =
                    Gains.of(
                            Math.multiplyExact(tier.ecus(), amount),
                            Math.multiplyExact(tier.gemmesSombresTenths(), amount),
                            Math.multiplyExact(tier.cendres(), amount),
                            Math.multiplyExact(tier.xp(), amount));
        } catch (IllegalArgumentException | ArithmeticException error) {
            pending.remove(owner);
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Récompense de coffre invalide : " + tier.id(),
                            error);
            player.sendMessage(
                    Component.text(
                            "Ce coffre est temporairement indisponible. Contacte un"
                                    + " administrateur.",
                            NamedTextColor.RED));
            return;
        }
        ItemStack consumed = hand.clone();
        consumed.setAmount(amount);
        var outgoing = org.bukkit.Bukkit.getServicesManager().load(OutgoingItems.class);
        if (outgoing == null) {
            pending.remove(owner);
            player.sendMessage(
                    Component.text(
                            "Ouverture temporairement indisponible. Tes coffres sont conservés.",
                            NamedTextColor.RED));
            return;
        }
        String name = player.getName();
        String original = ClaimService.serialize(consumed);
        boolean started =
                outgoing.consumeHeld(
                        player,
                        consumed,
                        receipt ->
                                new LootboxStore(ledger)
                                        .consume(owner, name, receipt, original, gains),
                        error -> {
                            pending.remove(owner);
                            Player current = org.bukkit.Bukkit.getPlayer(owner);
                            if (error != null) {
                                // OutgoingItems reprend l’intention et rend l’original
                                // une seule fois, même après reconnexion/redémarrage.
                                plugin.getLogger()
                                        .log(
                                                java.util.logging.Level.WARNING,
                                                "Ouverture de coffre non validée ; reprise du"
                                                        + " retrait sauvegardé",
                                                error);
                                if (current != null)
                                    current.sendMessage(
                                            Component.text(
                                                    "Ouverture impossible. Tes coffres sont"
                                                            + " conservés ou seront rendus dans"
                                                            + " /claim.",
                                                    NamedTextColor.RED));
                                return;
                            }
                            ledger.flushXP(owner);
                            if (current != null)
                                current.sendMessage(
                                        Component.text(
                                                        (amount > 1
                                                                        ? amount
                                                                                + " coffres"
                                                                                + " ouverts"
                                                                        : "Coffre" + " ouvert")
                                                                + " ! ",
                                                        NamedTextColor.GREEN)
                                                .append(
                                                        Component.text(
                                                                "+"
                                                                        + Numbers.compact(
                                                                                gains.amount(
                                                                                        CurrencyType
                                                                                                .ECUS))
                                                                        + " Écus · +"
                                                                        + CurrencyType
                                                                                .GEMMES_SOMBRES
                                                                                .format(
                                                                                        gains
                                                                                                .amount(
                                                                                                        CurrencyType
                                                                                                                .GEMMES_SOMBRES))
                                                                        + " Gemmes"
                                                                        + " Sombres · +"
                                                                        + Numbers.compact(
                                                                                gains.amount(
                                                                                        CurrencyType
                                                                                                .CENDRES))
                                                                        + " Cendres · +"
                                                                        + Numbers.compact(
                                                                                gains.xp())
                                                                        + " XP",
                                                                NamedTextColor.GRAY)));
                        });
        if (!started) {
            pending.remove(owner);
            player.sendMessage(
                    Component.text(
                            "Un transfert est en cours ou cet objet est protégé. Réessaie dans un"
                                    + " instant.",
                            NamedTextColor.YELLOW));
        }
    }
}
