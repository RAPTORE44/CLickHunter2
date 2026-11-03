package fr.cliker.clikerv2.discord.game;

import fr.cliker.clikerv2.discord.sync.RankSyncConfig;
import fr.cliker.clikerv2.rankings.*;

import org.bukkit.configuration.file.YamlConfiguration;

import java.time.ZoneId;
import java.util.*;

public record GameDiscordConfig(
        boolean enabled,
        String token,
        String guild,
        long interval,
        ZoneId zone,
        List<Board> boards,
        Map<String, Event> events,
        String address) {
    public record Board(String key, RankingMetric metric, RankingPeriod period, String channel) {}

    public record Event(String channel, String title, String text, int color) {}

    public GameDiscordConfig {
        boards = List.copyOf(boards);
        events = Map.copyOf(events);
    }

    public static GameDiscordConfig read(
            YamlConfiguration yaml, YamlConfiguration shared, String environmentToken) {
        boolean active = yaml.getBoolean("actif", false);
        String token =
                environmentToken == null || environmentToken.isBlank()
                        ? yaml.getString("bot-token", "").trim()
                        : environmentToken.trim();
        if (token.isBlank()) token = shared.getString("bot-token", "").trim();
        String guild = yaml.getString("serveur-id", "").trim();
        if (guild.isBlank()) guild = shared.getString("serveur-id", "").trim();
        List<Board> boards = new ArrayList<>();
        var section = yaml.getConfigurationSection("classements.tableaux");
        String common = yaml.getString("classements.salon-id", "").trim();
        if (yaml.getBoolean("classements.actif", true) && section != null)
            for (String key : section.getKeys(false)) {
                String path = "classements.tableaux." + key + ".";
                if (!yaml.getBoolean(path + "actif", true)) continue;
                String channel = yaml.getString(path + "salon-id", common).trim();
                if (channel.isEmpty()) channel = common;
                if (channel.isEmpty()) continue;
                RankingMetric metric = RankingMetric.parse(yaml.getString(path + "type", key));
                RankingPeriod period = period(yaml.getString(path + "periode", "all"));
                if (!key.matches("[a-z0-9_-]{1,40}")
                        || metric == null
                        || !RankSyncConfig.snowflake(channel))
                    throw new IllegalArgumentException("Classement ou salon invalide");
                boards.add(new Board(key, metric, period, channel));
            }
        Map<String, Event> events = new HashMap<>();
        section = yaml.getConfigurationSection("evenements.types");
        String eventChannel = yaml.getString("evenements.salon-id", "").trim();
        if (yaml.getBoolean("evenements.actif", true) && section != null)
            for (String key : section.getKeys(false)) {
                String path = "evenements.types." + key + ".";
                if (!yaml.getBoolean(path + "actif", true)) continue;
                String channel = yaml.getString(path + "salon-id", eventChannel).trim();
                if (channel.isEmpty()) channel = eventChannel;
                if (channel.isEmpty()) continue;
                if (!RankSyncConfig.snowflake(channel) || !key.matches("[a-z0-9-]{1,64}"))
                    throw new IllegalArgumentException("Salon d'événements invalide");
                events.put(
                        key,
                        new Event(
                                channel,
                                yaml.getString(path + "titre", key),
                                yaml.getString(path + "texte", ""),
                                Math.max(
                                        0,
                                        Math.min(
                                                0xffffff,
                                                yaml.getInt(path + "couleur", 0x55ff55)))));
            }
        if (active
                && (token.isBlank()
                        || token.chars().anyMatch(Character::isWhitespace)
                        || !RankSyncConfig.snowflake(guild)
                        || (boards.isEmpty() && events.isEmpty())))
            throw new IllegalArgumentException(
                    "Renseigner le bot, le serveur et au moins un salon.");
        return new GameDiscordConfig(
                active,
                token,
                guild,
                Math.max(1, Math.min(1440, yaml.getInt("classements.intervalle-minutes", 60)))
                        * 60_000L,
                ZoneId.of(yaml.getString("fuseau", "Europe/Paris")),
                boards,
                events,
                yaml.getString("adresse", "play.clickhunter.fr"));
    }

    static RankingPeriod period(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "all", "toujours" -> RankingPeriod.ALL;
            case "day", "jour" -> RankingPeriod.DAY;
            case "week", "semaine" -> RankingPeriod.WEEK;
            case "month", "mois" -> RankingPeriod.MONTH;
            case "year", "annee", "année" -> RankingPeriod.YEAR;
            default -> throw new IllegalArgumentException("Période de classement invalide");
        };
    }

    @Override
    public String toString() {
        return "GameDiscordConfig[enabled="
                + enabled
                + ",token=<masqué>,boards="
                + boards.size()
                + ",events="
                + events.size()
                + "]";
    }
}
