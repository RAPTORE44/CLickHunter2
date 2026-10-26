package fr.cliker.clikerv2.features;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.*;

import java.util.*;

/** Aide contextuelle qui n'expose que les commandes permises au joueur. */
public final class HelpCommand implements CommandExecutor, TabCompleter {
    private record Entry(String description, String usage, String permission, String group) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    public HelpCommand() {
        add("menu", "Ouvrir le menu principal.", "", "Navigation");
        add("warp", "Voyager vers les zones débloquées.", "cliker.navigation.use", "Navigation");
        add("spawn", "Revenir au spawn.", "cliker.spawn.use", "Navigation");
        add(
                "guide",
                "Voir le prochain palier d'amélioration abordable.",
                "cliker.upgrade.use",
                "Progression");
        add("upgrade", "Acheter des améliorations.", "cliker.upgrade.use", "Progression");
        add(
                "bestiaire",
                "Créatures découvertes, gains et maîtrises.",
                "cliker.bestiaire",
                "Progression");
        add(
                "contrats",
                "Accepter, suivre et réclamer des contrats.",
                "cliker.contrats",
                "Progression");
        add("tallisement", "Consulter les talismans et leurs bonus.", "", "Progression");
        add(
                "prestige",
                "Consulter et effectuer un prestige.",
                "cliker.prestige.use",
                "Progression");
        add("stats", "Consulter tes statistiques.", "cliker.stats.use", "Progression");
        add("session", "Résumé de ta session de chasse.", "cliker.session", "Progression");
        add(
                "ah",
                "Acheter et gérer les annonces de l'hôtel des ventes.",
                "cliker.ah.use",
                "Économie");
        add("sell", "Vendre tes essences.", "cliker.sell.use", "Économie");
        add("ecus", "Consulter ton solde d'Écus.", "cliker.money.use", "Économie");
        add("gemmes", "Consulter ton solde de Gemmes Sombres.", "cliker.money.use", "Économie");
        add("cendres", "Consulter ton solde de Cendres.", "cliker.money.use", "Économie");
        add("sac", "Gérer tes sacs personnels.", "cliker.sac.use", "Inventaire");
        add("claim", "Récupérer tes objets en attente.", "cliker.claim", "Inventaire");
        add(
                "lock",
                "Protéger ou déverrouiller ton équipement.",
                "cliker.comfort.use",
                "Inventaire");
        add("raid", "Rejoindre un raid ou gérer ton groupe.", "cliker.raid", "Activités");
        add("boss", "Rejoindre l'arène du boss serveur.", "cliker.navigation.use", "Activités");
        add("daily", "Ouvrir le calendrier journalier.", "cliker.daily.use", "Activités");
        add("enchere", "Rejoindre l'enchère en cours.", "cliker.enchere.use", "Activités");
        add("avantposte", "Gérer ou visiter les avant-postes.", "cliker.avantposte", "Activités");
        aliases.put("is", "avantposte");
        aliases.put("z", "zone");
        aliases.put("talismans", "tallisement");
        aliases.put("encheres", "enchere");
    }

    private void add(String name, String description, String permission, String group) {
        entries.put(name, new Entry(description, "/" + name, permission, group));
    }

    private boolean visible(CommandSender sender, Entry entry) {
        return entry.permission().isBlank() || sender.hasPermission(entry.permission());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1) {
            sender.sendMessage("Usage : /aide [commande]");
            return true;
        }
        if (args.length == 1) {
            String requested = args[0].toLowerCase(Locale.ROOT).replaceFirst("^/", "");
            String canonical = aliases.getOrDefault(requested, requested);
            Entry entry = entries.get(canonical);
            if (entry == null || !visible(sender, entry)) {
                sender.sendMessage(
                        Component.text(
                                "Commande inconnue ou inaccessible. Essaie /menu.",
                                NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(
                    Component.text(
                                    entry.usage() + " — " + entry.description(),
                                    NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.suggestCommand(entry.usage())));
            return true;
        }
        sender.sendMessage(
                Component.text(
                        "Aide ClickHunter — clique une commande pour la saisir",
                        NamedTextColor.GOLD));
        Map<String, List<String>> groups = new LinkedHashMap<>();
        entries.forEach(
                (name, entry) -> {
                    if (visible(sender, entry))
                        groups.computeIfAbsent(entry.group(), ignored -> new ArrayList<>())
                                .add("/" + name);
                });
        groups.forEach(
                (group, commands) ->
                        sender.sendMessage(
                                Component.text(group + " : ", NamedTextColor.AQUA)
                                        .append(
                                                Component.text(
                                                        String.join("  ", commands),
                                                        NamedTextColor.WHITE))));
        sender.sendMessage(Component.text("Détail : /aide <commande>", NamedTextColor.GRAY));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT).replaceFirst("^/", "");
        return entries.entrySet().stream()
                .filter(entry -> visible(sender, entry.getValue()))
                .map(Map.Entry::getKey)
                .filter(name -> name.startsWith(prefix))
                .toList();
    }
}
