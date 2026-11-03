package fr.cliker.clikerv2.achievements;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

/** Catalogue stable : 380 paliers de créatures/boss et 120 objectifs transversaux. */
public final class AchievementCatalog {
    public record Entry(
            String id,
            String metric,
            String title,
            String category,
            String description,
            Material icon,
            long target,
            MobTier tier) {}

    private AchievementCatalog() {}

    public static List<Entry> create(YamlConfiguration config) {
        List<Entry> result = new ArrayList<>();
        add(
                result,
                config,
                "kills",
                "Chasseur infatigable",
                "Chasse",
                "Éliminer des créatures et boss de zone",
                Material.IRON_SWORD,
                null,
                new long[] {10, 100, 1000, 10000, 100000, 1000000});
        add(
                result,
                config,
                "bosses",
                "Tombeur de boss",
                "Chasse",
                "Vaincre des boss de zone",
                Material.WITHER_SKELETON_SKULL,
                null,
                new long[] {1, 10, 50, 100, 500, 2500});
        add(
                result,
                config,
                "clicks",
                "Lame assidue",
                "Chasse",
                "Porter des coups aux créatures du clicker",
                Material.IRON_SWORD,
                null,
                new long[] {100, 1000, 10000, 100000, 1000000, 10000000});
        add(
                result,
                config,
                "minutes",
                "Fidèle aventurier",
                "Progression",
                "Cumuler des minutes de jeu",
                Material.CLOCK,
                null,
                new long[] {60, 300, 1500, 6000, 30000, 60000});
        add(
                result,
                config,
                "level",
                "Toujours plus haut",
                "Progression",
                "Atteindre un niveau",
                Material.EXPERIENCE_BOTTLE,
                null,
                new long[] {10, 25, 50, 100, 500, 1000});
        add(
                result,
                config,
                "prestige",
                "Nouvelle ascension",
                "Progression",
                "Atteindre un prestige",
                Material.BEACON,
                null,
                new long[] {1, 2, 5, 10, 25, 50});
        add(
                result,
                config,
                "zones",
                "Grand explorateur",
                "Progression",
                "Avoir accès à ce nombre de zones",
                Material.COMPASS,
                null,
                new long[] {2, 4, 7, 10, 15, 19});
        add(
                result,
                config,
                "talismans",
                "Collectionneur permanent",
                "Collection",
                "Enregistrer des talismans par clic droit",
                Material.NAUTILUS_SHELL,
                null,
                new long[] {1, 5, 10, 20, 40, 60});
        add(
                result,
                config,
                "contracts",
                "Parole tenue",
                "Activités",
                "Réclamer des contrats terminés",
                Material.PAPER,
                null,
                new long[] {1, 5, 25, 100, 250, 1000});
        add(
                result,
                config,
                "raids",
                "Compagnon de raid",
                "Activités",
                "Contribuer aux victoires de raid",
                Material.DIAMOND_SWORD,
                null,
                new long[] {1, 5, 10, 25, 100, 500});
        add(
                result,
                config,
                "serverboss",
                "Défenseur du serveur",
                "Activités",
                "Contribuer aux victoires sur le boss serveur",
                Material.DRAGON_HEAD,
                null,
                new long[] {1, 3, 10, 25, 50, 100});
        add(
                result,
                config,
                "ecus",
                "Fortune de chasse",
                "Économie",
                "Cumuler des Écus gagnés en chasse",
                Material.GOLD_INGOT,
                null,
                new long[] {1000, 10000, 100000, 1000000, 100000000, 1000000000});
        add(
                result,
                config,
                "gems",
                "Éclats de richesse",
                "Économie",
                "Cumuler des Gemmes Sombres gagnées en chasse",
                Material.AMETHYST_SHARD,
                null,
                new long[] {10, 100, 1000, 10000, 100000, 1000000});
        add(
                result,
                config,
                "xp",
                "Soif de savoir",
                "Progression",
                "Cumuler de l’XP gagnée",
                Material.EXPERIENCE_BOTTLE,
                null,
                new long[] {1000, 10000, 100000, 1000000, 10000000, 100000000});
        add(
                result,
                config,
                "mail",
                "Messager attentionné",
                "Communauté",
                "Envoyer des courriers à un autre joueur",
                Material.WRITABLE_BOOK,
                null,
                new long[] {1, 5, 10, 25, 50, 100});
        add(
                result,
                config,
                "afk",
                "Pause méritée",
                "Activités",
                "Cumuler des minutes dans la zone AFK",
                Material.CLOCK,
                null,
                new long[] {10, 60, 300, 600, 3000, 6000});
        add(
                result,
                config,
                "pinata",
                "Pluie de cadeaux",
                "Activités",
                "Porter des coups validés aux piñatas",
                Material.EMERALD,
                null,
                new long[] {1, 25, 100, 500, 1000, 5000});
        add(
                result,
                config,
                "atm",
                "Temps bien investi",
                "Économie",
                "Cumuler des Écus retirés à l’ATM",
                Material.GOLD_BLOCK,
                null,
                new long[] {100, 1000, 10000, 100000, 1000000, 10000000});
        add(
                result,
                config,
                "outpost",
                "Bâtisseur ambitieux",
                "Construction",
                "Atteindre un palier d’avant-poste (création incluse)",
                Material.GRASS_BLOCK,
                null,
                new long[] {1, 2, 3, 4, 5, 6});
        add(
                result,
                config,
                "welcomes",
                "Accueil chaleureux",
                "Communauté",
                "Souhaiter la bienvenue avec /bvn",
                Material.OAK_SIGN,
                null,
                new long[] {1, 5, 10, 25, 50, 100});
        for (var tier : MobTier.values()) {
            Material egg = Material.matchMaterial(tier.entityType().name() + "_SPAWN_EGG");
            if (egg == null) egg = Material.EGG;
            add(
                    result,
                    config,
                    "mob_" + tier.id(),
                    "Chasseur — " + tier.label(),
                    "Créatures",
                    "Éliminer des créatures " + tier.label(),
                    egg,
                    tier,
                    new long[] {1, 25, 100, 500, 1000, 5000, 10000, 50000, 100000, 1000000});
            add(
                    result,
                    config,
                    "boss_" + tier.id(),
                    "Némésis — " + tier.label(),
                    "Boss de zone",
                    "Vaincre le boss " + tier.label(),
                    egg,
                    tier,
                    new long[] {1, 5, 10, 25, 50, 100, 250, 500, 1000, 2500});
        }
        if (result.size() != 500)
            throw new IllegalStateException("Catalogue des 500 succès incomplet");
        return List.copyOf(result);
    }

    private static void add(
            List<Entry> result,
            YamlConfiguration cfg,
            String metric,
            String name,
            String category,
            String description,
            Material icon,
            MobTier tier,
            long[] targets) {
        long previous = 0;
        for (int i = 0; i < targets.length; i++) {
            String id = metric + "_" + (i + 1), path = "succes." + id;
            long target = cfg.getLong(path + ".objectif", targets[i]);
            if (target <= previous)
                throw new IllegalArgumentException("Objectifs non croissants : " + id);
            previous = target;
            result.add(
                    new Entry(
                            id,
                            metric,
                            cfg.getString(path + ".nom", name + " " + (i + 1)),
                            category,
                            description,
                            icon,
                            target,
                            tier));
        }
    }
}
