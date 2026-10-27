package fr.cliker.clikerv2.comfort;

import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.upgrade.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Conseil fondé sur les niveaux et le solde réel, sans inventer de rentabilité. */
public final class UpgradeGuide implements CommandExecutor {
    private final JavaPlugin plugin;
    private final UpgradeManager upgrades;
    private final Set<UUID> pending = new HashSet<>();

    public UpgradeGuide(JavaPlugin plugin, UpgradeManager upgrades) {
        this.plugin = plugin;
        this.upgrades = upgrades;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (!player.hasPermission("cliker.upgrade.use")) {
            player.sendMessage("Tu n’as pas la permission.");
            return true;
        }
        if (args.length != 0) {
            player.sendMessage("Usage : /guide");
            return true;
        }
        UUID id = player.getUniqueId();
        if (!upgrades.isLoaded(id)) {
            player.sendMessage("Tes améliorations sont en cours de chargement.");
            return true;
        }
        if (!pending.add(id)) return true;
        upgrades.gems(player)
                .whenComplete(
                        (balance, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            pending.remove(id);
                                            if (!player.isOnline()
                                                    || Bukkit.getPlayer(id) != player
                                                    || !player.hasPermission("cliker.upgrade.use"))
                                                return;
                                            if (error != null) {
                                                player.sendMessage(
                                                        "Le guide est temporairement"
                                                                + " indisponible.");
                                                return;
                                            }
                                            UpgradeType candidate = null;
                                            long cheapest = Long.MAX_VALUE;
                                            for (var type : UpgradeType.values()) {
                                                int level = upgrades.getLevel(id, type);
                                                if (level >= UpgradeType.MAX_LEVEL) continue;
                                                long price =
                                                        UpgradeType.costForLevel(level + 1) * 10;
                                                if (price < cheapest) {
                                                    candidate = type;
                                                    cheapest = price;
                                                }
                                            }
                                            if (candidate == null) {
                                                player.sendMessage(
                                                        Component.text(
                                                                "Toutes tes améliorations sont au"
                                                                    + " maximum. Consulte /prestige"
                                                                    + " ou relève un contrat dans"
                                                                    + " /contrats !",
                                                                NamedTextColor.GREEN));
                                                return;
                                            }
                                            String next =
                                                    candidate.label()
                                                            + " au niveau "
                                                            + (upgrades.getLevel(id, candidate)
                                                                    + 1);
                                            String advice =
                                                    balance >= cheapest
                                                            ? "Tu peux acheter "
                                                                    + next
                                                                    + " pour "
                                                                    + CurrencyType.GEMMES_SOMBRES
                                                                            .format(cheapest)
                                                                    + " Gemmes Sombres dans"
                                                                    + " /upgrade."
                                                            : "Prochain palier le moins cher : "
                                                                    + next
                                                                    + ". Il manque "
                                                                    + CurrencyType.GEMMES_SOMBRES
                                                                            .format(
                                                                                    cheapest
                                                                                            - balance)
                                                                    + " Gemmes Sombres. Consulte"
                                                                    + " /contrats pour poursuivre"
                                                                    + " ta progression.";
                                            player.sendMessage(
                                                    Component.text(advice, NamedTextColor.GREEN));
                                            player.sendMessage(
                                                    Component.text("Tes prochaines pistes : ")
                                                            .color(NamedTextColor.GRAY)
                                                            .append(
                                                                    link(
                                                                            "/bestiaire",
                                                                            "Découvrir les"
                                                                                + " maîtrises",
                                                                            NamedTextColor.GREEN))
                                                            .append(
                                                                    Component.text(" • ")
                                                                            .color(
                                                                                    NamedTextColor
                                                                                            .DARK_GRAY))
                                                            .append(
                                                                    link(
                                                                            "/contrats",
                                                                            "Contrats de chasse",
                                                                            NamedTextColor.GOLD))
                                                            .append(
                                                                    Component.text(" • ")
                                                                            .color(
                                                                                    NamedTextColor
                                                                                            .DARK_GRAY))
                                                            .append(
                                                                    link(
                                                                            "/warp",
                                                                            "Explorer les zones",
                                                                            NamedTextColor.AQUA)));
                                            player.sendMessage(
                                                    Component.text(
                                                                    "Pour revoir tes gains récents"
                                                                        + " : ")
                                                            .color(NamedTextColor.GRAY)
                                                            .append(
                                                                    link(
                                                                            "/session",
                                                                            "Résumé de session",
                                                                            NamedTextColor.YELLOW))
                                                            .append(
                                                                    Component.text(" • ")
                                                                            .color(
                                                                                    NamedTextColor
                                                                                            .DARK_GRAY))
                                                            .append(
                                                                    link(
                                                                            "/raid",
                                                                            "Raids de groupe",
                                                                            NamedTextColor
                                                                                    .LIGHT_PURPLE)));
                                        }));
        return true;
    }

    private static Component link(String command, String label, NamedTextColor color) {
        return Component.text(label)
                .color(color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text("Cliquer pour ouvrir " + command)));
    }
}
