package fr.cliker.clikerv2.raid;

import fr.cliker.clikerv2.config.YamlFiles;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.npc.ZnpcsNpc;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Configuration des arènes dédiées et de leurs accès, sans modifier une carte existante. */
public final class RaidSetup {
    private final JavaPlugin plugin;
    private final Settings settings;
    private final Map<Integer, String> effectiveWorlds = new HashMap<>();
    private final String effectiveBossWorld;
    private final Map<String, ZnpcsNpc> npcs = new HashMap<>();
    private final java.util.function.BiConsumer<Player, Boolean> observe;

    public RaidSetup(JavaPlugin plugin, java.util.function.BiConsumer<Player, Boolean> observe) {
        this.plugin = plugin;
        this.observe = observe;
        settings = new Settings(plugin, "activites/arenes.yml");
        var used = new HashSet<String>();
        for (int i = 0; i < 4; i++) {
            String name = configuredWorld(false, i);
            validateWorldName(name);
            if (!used.add(name))
                throw new IllegalArgumentException(
                        "Chaque arène doit utiliser un monde distinct : " + name);
            effectiveWorlds.put(i, name);
        }
        effectiveBossWorld = configuredWorld(true, 0);
        validateWorldName(effectiveBossWorld);
        if (!used.add(effectiveBossWorld))
            throw new IllegalArgumentException("Le boss serveur doit utiliser son propre monde.");
        Bukkit.getServicesManager()
                .register(RaidSetup.class, this, plugin, org.bukkit.plugin.ServicePriority.Normal);
        for (String mode : List.of("raid", "boss"))
            for (String role : List.of("entree", "sortie", "observation")) restoreNpc(mode, role);
    }

    private void validateWorldName(String name) {
        if (!name.matches("[A-Za-z0-9_-]+")
                || name.equals("clickhunter_avantpostes")
                || name.equals("clickhunter_afk")
                || !Bukkit.getWorlds().isEmpty()
                        && Bukkit.getWorlds().getFirst().getName().equals(name))
            throw new IllegalArgumentException("Monde dédié invalide : " + name);
    }

    public String world(boolean boss, int slot) {
        return boss ? effectiveBossWorld : effectiveWorlds.get(slot);
    }

    private String configuredWorld(boolean boss, int slot) {
        return settings.yaml.getString(
                boss ? "boss.monde" : "raid.mondes." + (slot + 1),
                boss ? "clickhunter_boss_serveur" : "clickhunter_raid_groupe_" + slot);
    }

    public long threshold(long fallback) {
        return settings.yaml.getLong("boss.seuil", fallback);
    }

    public int difficulty(int fallback) {
        var zone = fr.cliker.clikerv2.MobTier.parse(settings.yaml.getString("boss.zone", ""));
        return zone == null ? fallback : zone.ordinal() + 1;
    }

    public void applyBossParameters(YamlConfiguration config) {
        var section = settings.yaml.getConfigurationSection("boss.parametres");
        if (section != null)
            for (var entry : section.getValues(true).entrySet())
                if (parameters().containsKey(entry.getKey()))
                    config.set(entry.getKey(), entry.getValue());
    }

    public Location point(World world, String kind, Location fallback) {
        var point = YamlFiles.location(settings.yaml, "arenes." + world.getName() + "." + kind);
        return point != null && point.getWorld() == world ? point : fallback.clone();
    }

    public Location exit(World world) {
        var at = YamlFiles.location(settings.yaml, "arenes." + world.getName() + ".sortie");
        return at != null && at.getWorld() != world ? at : null;
    }

    public double radius(World world) {
        return Math.max(
                8,
                Math.min(256, settings.yaml.getDouble("arenes." + world.getName() + ".rayon", 31)));
    }

    private void restoreNpc(String mode, String role) {
        String key = mode + ".pnj." + role;
        var section = settings.yaml.getConfigurationSection(key);
        var yaml = new YamlConfiguration();
        if (section != null)
            for (var entry : section.getValues(true).entrySet())
                yaml.set(entry.getKey(), entry.getValue());
        yaml.addDefault("pnj.type", "villager");
        yaml.addDefault("pnj.titre", mode.equals("boss") ? "&c&lBoss serveur" : "&6&lRaids");
        yaml.addDefault("pnj.sous-titre", "&7Clic droit • " + role);
        yaml.options().copyDefaults(true);
        var npc =
                new ZnpcsNpc(
                        plugin,
                        yaml,
                        () -> {
                            settings.yaml.set(key, yaml);
                            settings.save();
                        },
                        player -> {
                            if (role.equals("observation"))
                                observe.accept(player, mode.equals("boss"));
                            else
                                player.performCommand(
                                        mode + (role.equals("sortie") ? " quitter" : ""));
                        },
                        "arena-" + mode + "-" + role,
                        mode);
        npcs.put(mode + ":" + role, npc);
        npc.start();
    }

    public boolean command(Player player, boolean boss, String[] args) {
        String mode = boss ? "boss" : "raid";
        if (!player.hasPermission(boss ? "cliker.boss.admin" : "cliker.raid.admin")) {
            player.sendMessage("§cPermission administrateur requise.");
            return true;
        }
        try {
            if (args.length == 1 || args[1].equalsIgnoreCase("info")) {
                if (boss)
                    player.sendMessage(
                            "§7/boss admin seuil <kills> §8• §7/boss admin zone <zone> §8• après"
                                + " redémarrage");
                player.sendMessage("§bArènes dédiées :");
                for (int i = 0; i < (boss ? 1 : 4); i++)
                    player.sendMessage("§7" + (i + 1) + " §f" + world(boss, i));
                player.sendMessage(
                        "§7/"
                                + mode
                                + " admin map "
                                + (boss ? "" : "<1-4> ")
                                + "<monde> §8• changement après redémarrage");
                player.sendMessage(
                        "§7/"
                                + mode
                                + " admin set "
                                + (boss ? "" : "<1-4> ")
                                + "<entree|centre|observation|sortie>");
                player.sendMessage(
                        "§7/" + mode + " admin rayon " + (boss ? "" : "<1-4> ") + "<8-256>");
                player.sendMessage(
                        "§7/" + mode + " admin pnj <entree|sortie|observation> <spawn|remove>");
                player.sendMessage(
                        "§7/"
                                + mode
                                + " admin param <cle> <valeur> §8• prochains combats ; /"
                                + mode
                                + " admin param pour la liste");
                return true;
            }
            if (boss && args[1].equalsIgnoreCase("seuil") && args.length == 3) {
                long threshold = Long.parseLong(args[2]);
                if (threshold < 1 || threshold > 1_000_000_000_000_000L)
                    throw new IllegalArgumentException(
                            "Seuil : 1 à 1 000 000 000 000 000 éliminations.");
                settings.yaml.set("boss.seuil", threshold);
                settings.save();
                player.sendMessage(
                        "§aSeuil enregistré. Redémarre pour appliquer le nouveau cycle naturel.");
                return true;
            }
            if (boss && args[1].equalsIgnoreCase("zone") && args.length == 3) {
                var zone = fr.cliker.clikerv2.MobTier.parse(args[2]);
                if (zone == null) throw new IllegalArgumentException("Zone inconnue.");
                settings.yaml.set("boss.zone", zone.id());
                settings.save();
                player.sendMessage(
                        "§aBoss serveur lié à la zone "
                                + zone.label()
                                + ". Application au redémarrage.");
                return true;
            }
            if (args[1].equalsIgnoreCase("pnj") && args.length == 4) {
                var npc = npcs.get(mode + ":" + args[2].toLowerCase(Locale.ROOT));
                if (npc == null)
                    throw new IllegalArgumentException("Rôle : entree, sortie ou observation.");
                if (args[3].equalsIgnoreCase("spawn"))
                    player.sendMessage(
                            npc.spawn(player.getLocation())
                                    ? "§aPNJ placé."
                                    : "§ePNJ déjà présent : retire-le avant de le déplacer.");
                else if (args[3].equalsIgnoreCase("remove")) {
                    npc.remove();
                    player.sendMessage("§aPNJ retiré.");
                } else throw new IllegalArgumentException("Choisis spawn ou remove.");
                return true;
            }
            if (args[1].equalsIgnoreCase("param")) {
                Map<String, double[]> allowed = parameters();
                if (args.length == 2) {
                    player.sendMessage(
                            "§eClés : " + String.join(", ", new TreeSet<>(allowed.keySet())));
                    return true;
                }
                if (args.length != 4 || !allowed.containsKey(args[2]))
                    throw new IllegalArgumentException(
                            "Utilise /" + mode + " admin param pour les clés disponibles.");
                double value = Double.parseDouble(args[3]);
                var range = allowed.get(args[2]);
                if (!Double.isFinite(value)
                        || value < range[0]
                        || value > range[1]
                        || range[2] == 1 && value != Math.rint(value))
                    throw new IllegalArgumentException(
                            "Valeur autorisée : "
                                    + range[0]
                                    + " à "
                                    + range[1]
                                    + (range[2] == 1 ? ", entière." : "."));
                var file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "raid.yml");
                var config = YamlConfiguration.loadConfiguration(file);
                Number stored =
                        range[2] == 1 ? Long.valueOf((long) value) : (Number) Double.valueOf(value);
                if (boss) {
                    settings.yaml.set("boss.parametres." + args[2], stored);
                    settings.save();
                } else {
                    config.set(args[2], stored);
                    config.save(file);
                }
                player.sendMessage(
                        "§aParamètre sauvegardé : "
                                + args[2]
                                + " = "
                                + args[3]
                                + ". Prochains raids ; boss serveur après redémarrage.");
                return true;
            }
            int offset = boss ? 0 : 1;
            int slot = boss ? 0 : Integer.parseInt(args[2]) - 1;
            if (slot < 0 || slot > 3) throw new IllegalArgumentException("Arène : 1 à 4.");
            String selected = world(boss, slot);
            if (args[1].equalsIgnoreCase("map") && args.length == 3 + offset) {
                String name = args[2 + offset];
                World target = Bukkit.getWorld(name);
                if (target == null || name.contains(".") || !name.matches("[A-Za-z0-9_-]+"))
                    throw new IllegalArgumentException(
                            "Monde chargé requis ; nom composé de lettres, chiffres, _ et -.");
                if (target == Bukkit.getWorlds().getFirst()
                        || name.equals("clickhunter_avantpostes")
                        || name.equals("clickhunter_afk"))
                    throw new IllegalArgumentException(
                            "Utilise un monde dédié, jamais le spawn ou un monde de joueurs.");
                if (!target.getPlayers().isEmpty())
                    throw new IllegalArgumentException(
                            "Le monde doit être vide avant de l’affecter à une arène.");
                for (int i = 0; i < 4; i++)
                    if (!(!boss && slot == i) && configuredWorld(false, i).equals(name))
                        throw new IllegalArgumentException("Monde déjà affecté à un raid.");
                if (!boss && configuredWorld(true, 0).equals(name))
                    throw new IllegalArgumentException("Monde déjà affecté au boss serveur.");
                settings.yaml.set(boss ? "boss.monde" : "raid.mondes." + (slot + 1), name);
                settings.save();
                player.sendMessage(
                        "§aCarte enregistrée. Redémarre pour réaffecter les instances sans"
                                + " interrompre les combats actifs.");
                return true;
            }
            if (args[1].equalsIgnoreCase("set") && args.length == 3 + offset) {
                String kind = args[2 + offset].toLowerCase(Locale.ROOT);
                if (!List.of("entree", "centre", "observation", "sortie").contains(kind))
                    throw new IllegalArgumentException(
                            "Point : entree, centre, observation ou sortie.");
                if (!kind.equals("sortie") && !player.getWorld().getName().equals(selected))
                    throw new IllegalArgumentException(
                            "Place-toi dans " + selected + " pour définir ce point.");
                if (kind.equals("sortie") && player.getWorld().getName().equals(selected))
                    throw new IllegalArgumentException(
                            "La sortie doit être dans un autre monde, par exemple au spawn.");
                YamlFiles.location(
                        settings.yaml, "arenes." + selected + "." + kind, player.getLocation());
                settings.save();
                player.sendMessage(
                        "§aPoint "
                                + kind
                                + " enregistré (position et orientation). Effet immédiat.");
                return true;
            }
            if (args[1].equalsIgnoreCase("rayon") && args.length == 3 + offset) {
                int radius = Integer.parseInt(args[2 + offset]);
                if (radius < 8 || radius > 256)
                    throw new IllegalArgumentException("Rayon : 8 à 256 blocs.");
                settings.yaml.set("arenes." + selected + ".rayon", radius);
                settings.save();
                player.sendMessage("§aRayon enregistré autour du centre de l’arène.");
                return true;
            }
            throw new IllegalArgumentException("Utilise /" + mode + " admin info.");
        } catch (Exception error) {
            player.sendMessage(
                    "§c"
                            + (error instanceof IndexOutOfBoundsException
                                            || error instanceof NumberFormatException
                                    ? "Arguments invalides : /" + mode + " admin info."
                                    : error.getMessage()));
            return true;
        }
    }

    public static Map<String, double[]> parameters() {
        var map = new LinkedHashMap<String, double[]>();
        map.put("mobs.maximum", new double[] {1, 60, 1});
        map.put("mobs.pv", new double[] {1, 1e15, 1});
        map.put("boss.pv", new double[] {1, 1e15, 1});
        map.put("boss.tous-les-mobs", new double[] {1, 1e6, 1});
        map.put("mobs.degats", new double[] {0, 2048, 0});
        map.put("boss.degats", new double[] {0, 2048, 0});
        map.put("boss.portee", new double[] {1, 30, 0});
        map.put("boss.echelle", new double[] {1, 8, 0});
        map.put("boss.vitesse", new double[] {0.1, 3, 0});
        for (String group : List.of("mobs", "boss"))
            for (String currency : List.of("ecus", "gemmes-sombres", "cendres", "xp"))
                map.put(group + ".recompenses." + currency, new double[] {0, 1e12, 1});
        return map;
    }

    public void shutdown() {
        npcsShutdown();
        Bukkit.getServicesManager().unregister(RaidSetup.class, this);
    }

    private void npcsShutdown() {
        npcs.values().forEach(ZnpcsNpc::shutdown);
        npcs.clear();
    }
}
