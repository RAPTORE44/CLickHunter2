package fr.cliker.clikerv2.features;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.mobs.CustomMobManager;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;

/** Diagnostic à la demande : aucune mutation des données ni réparation implicite. */
public final class AdminDiagnostic {
    private final JavaPlugin plugin;
    private final CustomMobManager mobs;
    private final Menus menus;

    public AdminDiagnostic(
            JavaPlugin plugin, DatabaseManager db, CustomMobManager mobs, Menus menus) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.menus = menus;
        plugin.getCommand("diagnostic")
                .setExecutor(
                        (sender, cmd, label, args) -> {
                            if (!sender.hasPermission("cliker.diagnostic.admin")) {
                                sender.sendMessage("Permission administrateur requise.");
                                return true;
                            }
                            sender.sendMessage("Diagnostic en cours…");
                            db.submit(AdminDiagnostic::readDatabase)
                                    .whenComplete(
                                            (lines, error) ->
                                                    fr.cliker.clikerv2.runtime.ServerTasks.run(
                                                            plugin,
                                                            () -> complete(sender, lines, error)));
                            return true;
                        });
    }

    private static List<String> readDatabase(Connection connection) {
        List<String> lines = new ArrayList<>();
        try (var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("PRAGMA quick_check")) {
                while (result.next()) lines.add("SQLite : " + result.getString(1));
            }
            try (var result =
                    statement.executeQuery("SELECT COUNT(*) FROM claim_items WHERE state<>2")) {
                if (result.next())
                    lines.add("Objets en attente de livraison : " + result.getLong(1));
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM mob_spawns")) {
                if (result.next()) lines.add("Points de mobs sauvegardés : " + result.getLong(1));
            }
        } catch (SQLException error) {
            throw new DatabaseManager.DatabaseException(error);
        }
        return lines;
    }

    private void complete(CommandSender sender, List<String> lines, Throwable error) {
        if (!sender.hasPermission("cliker.diagnostic.admin")) return;
        if (sender instanceof Player p && !fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(p))
            return;
        if (error != null) {
            sender.sendMessage("Diagnostic SQLite indisponible ; consulte les journaux.");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Diagnostic SQL", error);
            return;
        }
        List<String> results = new ArrayList<>(lines);
        results.addAll(mobs.diagnostic());
        results.addAll(fr.cliker.clikerv2.npc.ZnpcsNpc.diagnostics());
        for (String dependency :
                List.of("ZNPCsPlus", "HolographicDisplays", "ItemsAdder", "LuckPerms", "spark"))
            results.add(
                    dependency
                            + " : "
                            + (Bukkit.getPluginManager().isPluginEnabled(dependency)
                                    ? "actif"
                                    : "absent ou désactivé"));
        var provider = fr.cliker.clikerv2.hologram.SharedHolograms.service();
        results.add(
                "Rendu des hologrammes : "
                        + (provider != null && provider.usesHolographicDisplays()
                                ? "Holographic Displays"
                                : "secours natif ou désactivé"));
        if (!Bukkit.getPluginManager().isPluginEnabled("spark"))
            results.add(
                    "Profilage TPS absent : installe Spark puis utilise /spark profiler --timeout"
                        + " 60");
        Settings warps = new Settings(plugin, "navigation/warps.yml");
        for (MobTier tier : MobTier.values())
            for (boolean boss : new boolean[] {false, true}) {
                String path = "zones." + tier.id() + "." + (boss ? "boss" : "entree");
                if (fr.cliker.clikerv2.config.YamlFiles.location(warps.yaml, path) == null
                        && mobs.travelPoint(tier, boss).isEmpty())
                    results.add(
                            "Destination manquante : "
                                    + tier.id()
                                    + " "
                                    + (boss ? "boss" : "entrée"));
                if (fr.cliker.clikerv2.config.YamlFiles.location(warps.yaml, path) == null
                        && mobs.travelPoint(tier, boss).isEmpty())
                    results.add(
                            "Correction : /warp set " + tier.id() + (boss ? " boss" : " entree"));
            }
        if (sender instanceof Player p) show(p, results, 0);
        else results.forEach(sender::sendMessage);
    }

    private void show(Player player, List<String> results, int requested) {
        if (!player.hasPermission("cliker.diagnostic.admin")) return;
        int page = Math.max(0, Math.min(requested, Math.max(0, (results.size() - 1) / 45)));
        var view = menus.create(player, 54, "Diagnostic administrateur — " + (page + 1));
        for (int i = 0; i < 45 && page * 45 + i < results.size(); i++)
            view.put(i, Menus.item(Material.PAPER, results.get(page * 45 + i)), null);
        view.put(
                45,
                Menus.item(Material.ARROW, "Page précédente"),
                (who, r) -> show(who, results, page - 1));
        view.put(
                49,
                Menus.item(Material.SUNFLOWER, "Actualiser le diagnostic"),
                (who, r) -> who.performCommand("diagnostic"));
        view.put(
                53,
                Menus.item(Material.ARROW, "Page suivante"),
                (who, r) -> show(who, results, page + 1));
        view.show(player);
    }
}
