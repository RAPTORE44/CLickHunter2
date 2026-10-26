package fr.cliker.clikerv2.social;

import fr.cliker.clikerv2.features.Settings;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.*;
import java.util.*;

public final class SocialSettings {
    public record Rank(long threshold, String name) {}

    public final YamlConfiguration yaml;
    public final ZoneId zone;
    public final long divorceCost,
            requestSeconds,
            recommendationCooldown,
            requiredTicks,
            recommendationXP;
    public final Material ringMaterial;
    public final List<Rank> ranks;

    public SocialSettings(JavaPlugin plugin) {
        this(new Settings(plugin, "communaute/social.yml").yaml);
    }

    SocialSettings(YamlConfiguration config) {
        yaml = config;
        zone = ZoneId.of(config.getString("mariage.fuseau", "Europe/Paris"));
        divorceCost = number("mariage.divorce-ecus", 100_000_000, 0, 1_000_000_000_000_000L);
        requestSeconds = number("mariage.demande-secondes", 60, 1, 600);
        recommendationCooldown =
                Math.multiplyExact(number("popularite.delai-secondes", 3600, 1, 31_536_000), 1000);
        requiredTicks =
                Math.multiplyExact(
                        number("popularite.temps-jeu-secondes", 18000, 0, 31_536_000), 20);
        recommendationXP = number("popularite.xp", 500, 0, 1_000_000);
        ringMaterial =
                Material.matchMaterial(
                        config.getString("mariage.objet.material", "AMETHYST_SHARD"));
        if (ringMaterial == null || !ringMaterial.isItem() || ringMaterial.isAir())
            throw new IllegalArgumentException("Matériau de l’objet de mariage invalide");
        List<Rank> list = new ArrayList<>();
        for (var entry : config.getMapList("popularite.echelons")) {
            Object threshold = entry.get("seuil"), label = entry.get("nom");
            if (!(threshold instanceof Number n)
                    || !(label instanceof String name)
                    || name.isBlank()
                    || name.length() > 80
                    || n.longValue() < 0
                    || n.doubleValue() != n.longValue())
                throw new IllegalArgumentException("Échelon invalide");
            list.add(new Rank(n.longValue(), name));
        }
        if (list.isEmpty() || list.size() > 45 || list.getFirst().threshold() != 0)
            throw new IllegalArgumentException("Il faut de 1 à 45 échelons, à partir du seuil 0");
        for (int i = 1; i < list.size(); i++)
            if (list.get(i).threshold() <= list.get(i - 1).threshold())
                throw new IllegalArgumentException(
                        "Les seuils doivent être strictement croissants");
        ranks = List.copyOf(list);
    }

    private long number(String key, long value, long min, long max) {
        Object raw = yaml.get(key, value);
        if (!(raw instanceof Number n)
                || n.doubleValue() != n.longValue()
                || n.longValue() < min
                || n.longValue() > max)
            throw new IllegalArgumentException("Valeur invalide : " + key);
        return n.longValue();
    }

    public boolean season(Clock clock) {
        return !yaml.getBoolean("mariage.fevrier-uniquement", true)
                || LocalDate.now(clock.withZone(zone)).getMonth() == Month.FEBRUARY;
    }

    public Rank rank(long points) {
        Rank result = ranks.getFirst();
        for (var rank : ranks) {
            if (points < rank.threshold()) break;
            result = rank;
        }
        return result;
    }
}
