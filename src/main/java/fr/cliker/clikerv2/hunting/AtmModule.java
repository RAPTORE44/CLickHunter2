package fr.cliker.clikerv2.hunting;

import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.npc.ZnpcsNpc;
import fr.cliker.clikerv2.runtime.ServerTasks;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class AtmModule {
    private final JavaPlugin plugin;
    private final AtmStore store;
    private final Settings settings;
    private final ZnpcsNpc npc;
    private final Set<UUID> pending = new HashSet<>();
    private final long interval, reward;
    private boolean stopped;

    public AtmModule(JavaPlugin plugin, RewardLedger ledger) {
        this.plugin = plugin;
        store = new AtmStore(ledger);
        settings = new Settings(plugin, "boutiques/atm.yml");
        interval = Math.multiplyExact(settings.yaml.getLong("minutes-par-tranche", 10), 1200L);
        reward = settings.yaml.getLong("ecus-par-tranche", 1000);
        if (interval < 1200 || interval > 86400000 || reward < 1 || reward > 1000000000)
            throw new IllegalArgumentException("Tarif ATM invalide");
        npc = new ZnpcsNpc(plugin, settings.yaml, settings::save, this::open, "atm", "Marketus");
        npc.start();
        Objects.requireNonNull(plugin.getCommand("atm"))
                .setExecutor(
                        (sender, cmd, label, args) -> {
                            if (!(sender instanceof Player p)) {
                                sender.sendMessage("Commande réservée aux joueurs.");
                                return true;
                            }
                            if (args.length == 0) {
                                open(p);
                                return true;
                            }
                            if (!p.hasPermission("cliker.atm.admin")) {
                                p.sendMessage("§cPermission administrateur requise.");
                                return true;
                            }
                            try {
                                if (args.length == 2 && args[0].equalsIgnoreCase("pnj")) {
                                    if (!npc.available())
                                        p.sendMessage("§cZNPCsPlus 2.0.0 est nécessaire.");
                                    else if (args[1].equalsIgnoreCase("spawn"))
                                        p.sendMessage(
                                                npc.spawn(p.getLocation())
                                                        ? "§aMarketus placé."
                                                        : "§eMarketus existe déjà.");
                                    else if (args[1].equalsIgnoreCase("remove"))
                                        p.sendMessage(
                                                npc.remove()
                                                        ? "§aMarketus retiré."
                                                        : "§7Aucun Marketus placé.");
                                    else p.sendMessage("§e/atm pnj spawn|remove");
                                } else p.sendMessage("§e/atm pnj spawn|remove");
                            } catch (Exception error) {
                                plugin.getLogger()
                                        .log(
                                                java.util.logging.Level.SEVERE,
                                                "PNJ ATM indisponible",
                                                error);
                                p.sendMessage(
                                        "§cImpossible de modifier Marketus. Consulte les logs.");
                            }
                            return true;
                        });
        plugin.getCommand("atm")
                .setTabCompleter(
                        (s, c, l, a) ->
                                s.hasPermission("cliker.atm.admin")
                                        ? (a.length == 1
                                                ? List.of("pnj")
                                                : a.length == 2
                                                        ? List.of("spawn", "remove")
                                                        : List.of())
                                        : List.of());
    }

    private void open(Player p) {
        if (stopped || !p.hasPermission("cliker.atm.use")) return;
        var view =
                HuntUI.view(
                        p,
                        "&6ATM &8• &eMarketus",
                        27,
                        slot -> {
                            if (slot == 13) collect(p);
                        });
        var inventory = view.getInventory();
        store.summary(p.getUniqueId(), p.getStatistic(Statistic.PLAY_ONE_MINUTE), interval, reward)
                .whenComplete(
                        (summary, error) -> {
                            if (stopped || !plugin.isEnabled()) return;
                            ServerTasks.run(
                                    plugin,
                                    () -> {
                                        if (!fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(p)
                                                || p.getOpenInventory().getTopInventory()
                                                        != inventory) return;
                                        inventory.setItem(
                                                11,
                                                HuntUI.item(
                                                        Material.CHEST,
                                                        "&6Total récolté",
                                                        List.of(
                                                                error == null
                                                                        ? "&e"
                                                                                + summary.total()
                                                                                + " Écus"
                                                                        : "&cChargement"
                                                                              + " impossible")));
                                        inventory.setItem(
                                                15,
                                                HuntUI.item(
                                                        Material.EMERALD,
                                                        "&aÀ récupérer",
                                                        List.of(
                                                                error == null
                                                                        ? "&e"
                                                                                + summary
                                                                                        .available()
                                                                                + " Écus"
                                                                        : "&cChargement"
                                                                              + " impossible")));
                                    });
                        });
        view.getInventory()
                .setItem(
                        13,
                        HuntUI.item(
                                Material.GOLD_INGOT,
                                "&eRetirer mes Écus",
                                List.of(
                                        "&7Chaque tranche de &f" + interval / 1200 + " minutes",
                                        "&7de jeu rapporte &e" + reward + " Écus&7.",
                                        "&7Les tranches déjà payées ne sont pas recomptées.",
                                        "",
                                        "&aClique pour retirer les tranches disponibles.")));
        p.openInventory(view.getInventory());
    }

    private void collect(Player p) {
        if (stopped || !p.hasPermission("cliker.atm.use") || !pending.add(p.getUniqueId())) return;
        var requestedInventory = p.getOpenInventory().getTopInventory();
        long ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        store.collect(p.getUniqueId(), p.getName(), ticks, interval, reward)
                .whenComplete(
                        (amount, error) -> {
                            if (stopped || !plugin.isEnabled()) return;
                            ServerTasks.run(
                                    plugin,
                                    () -> {
                                        pending.remove(p.getUniqueId());
                                        if (error != null)
                                            plugin.getLogger()
                                                    .log(
                                                            java.util.logging.Level.SEVERE,
                                                            "Retrait ATM impossible",
                                                            error);
                                        if (!fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(p))
                                            return;
                                        p.sendMessage(
                                                error != null
                                                        ? "§cRetrait impossible. Réessaie plus"
                                                                + " tard."
                                                        : amount == 0
                                                                ? "§7Aucune nouvelle tranche"
                                                                        + " complète à retirer."
                                                                : "§aATM : §e+"
                                                                        + amount
                                                                        + " Écus §acrédités !");
                                        if (p.getOpenInventory().getTopInventory()
                                                == requestedInventory) open(p);
                                    });
                        });
    }

    public void shutdown() {
        stopped = true;
        npc.shutdown();
        pending.clear();
    }
}
