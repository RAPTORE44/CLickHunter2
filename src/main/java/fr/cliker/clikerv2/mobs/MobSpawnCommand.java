package fr.cliker.clikerv2.mobs;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.Numbers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MobSpawnCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUB =
            List.of("setspawn", "bossspawn", "remove", "removeall", "list");

    private final CustomMobManager manager;

    public MobSpawnCommand(CustomMobManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) {
            sender.sendMessage(
                    Component.text("Permission administrateur requise.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            for (MobTier tier : MobTier.byId().values()) {
                sender.sendMessage(
                        Component.text(tier.id() + " ", NamedTextColor.YELLOW)
                                .append(
                                        Component.text(
                                                "(" + Numbers.compact(tier.maxHealth()) + " HP) : ",
                                                NamedTextColor.GRAY))
                                .append(
                                        Component.text(
                                                manager.countSpawns(tier.id()) + " zone(s)",
                                                NamedTextColor.WHITE)));
            }
            return true;
        }
        if (args.length != 2
                || !(args[0].equalsIgnoreCase("setspawn")
                        || args[0].equalsIgnoreCase("bossspawn")
                        || args[0].equalsIgnoreCase("remove")
                        || args[0].equalsIgnoreCase("removeall"))) {
            sender.sendMessage(
                    Component.text(
                            "/"
                                    + label
                                    + " <setspawn|bossspawn|remove|removeall> <mob> ou /"
                                    + label
                                    + " list",
                            NamedTextColor.YELLOW));
            return true;
        }
        MobTier tier = MobTier.parse(args[1]);
        if (tier == null) {
            sender.sendMessage(
                    Component.text(
                            "Mob inconnu. Utilisez /" + label + " list.", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("removeall")) {
            int removed = manager.removeAllSpawns(tier.id());
            sender.sendMessage(
                    removed > 0
                            ? Component.text(
                                    removed
                                            + " zone(s) de spawn de "
                                            + tier.label()
                                            + " supprimée(s).",
                                    NamedTextColor.GREEN)
                            : Component.text(
                                    "Aucune zone de spawn pour " + tier.label() + ".",
                                    NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    Component.text("Commande réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            boolean removed = manager.removeNearestSpawn(tier.id(), player.getLocation());
            sender.sendMessage(
                    removed
                            ? Component.text(
                                    "Zone de spawn de "
                                            + tier.label()
                                            + " la plus proche supprimée.",
                                    NamedTextColor.GREEN)
                            : Component.text(
                                    "Aucune zone de spawn de " + tier.label() + " à proximité.",
                                    NamedTextColor.RED));
            return true;
        }

        boolean boss = args[0].equalsIgnoreCase("bossspawn");
        manager.addSpawn(tier.id(), centeredFacingPlayer(player.getLocation()), boss);
        long hp = boss ? manager.bossHealth(tier) : Math.round(tier.maxHealth());
        sender.sendMessage(
                Component.text(
                        (boss ? "Golem " : "Zone de spawn de ")
                                + tier.label()
                                + " ("
                                + Numbers.compact(hp)
                                + " HP) ajouté"
                                + (boss ? "" : "e")
                                + " ici. Total : "
                                + manager.countSpawns(tier.id())
                                + ".",
                        NamedTextColor.GREEN));
        return true;
    }

    /**
     * Centre le spawn sur le bloc où se tient le joueur, et fait regarder le mob vers lui (yaw
     * opposé au sien).
     */
    private Location centeredFacingPlayer(Location playerLocation) {
        return new Location(
                playerLocation.getWorld(),
                Math.floor(playerLocation.getX()) + 0.5,
                Math.floor(playerLocation.getY()),
                Math.floor(playerLocation.getZ()) + 0.5,
                playerLocation.getYaw() + 180f,
                0f);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cliker.mob.admin")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) options.addAll(SUB);
        else if (args.length == 2
                && (args[0].equalsIgnoreCase("setspawn")
                        || args[0].equalsIgnoreCase("bossspawn")
                        || args[0].equalsIgnoreCase("remove")
                        || args[0].equalsIgnoreCase("removeall")))
            options.addAll(MobTier.byId().keySet());
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(current));
        return options;
    }
}
