package fr.cliker.clikerv2.discord.game;

import com.google.gson.*;

import fr.cliker.clikerv2.rankings.*;

import java.time.*;
import java.util.*;

public final class DiscordGamePayload {
    private DiscordGamePayload() {}

    public static String plain(String value) {
        return value.replaceAll("(?i)[§&][0-9a-fk-or]", "");
    }

    private static String escape(String name) {
        return plain(name)
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("@", "＠");
    }

    public static String marker(GameDiscordConfig.Board board) {
        return "ClickHunter • TOP:" + board.key();
    }

    public static JsonObject board(
            GameDiscordConfig.Board board, RankingStore.Board ranking, LocalDate date) {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            var entry =
                    i < ranking.entries().size()
                            ? ranking.entries().get(i)
                            : new RankingStore.Entry("—", 0);
            lines.append("`#")
                    .append(i + 1)
                    .append("` **")
                    .append(escape(entry.name()))
                    .append("** — ")
                    .append(board.metric().format(entry.value()))
                    .append(' ')
                    .append(board.metric().unit)
                    .append('\n');
        }
        lines.append('\n').append(board.metric().meaning(board.period() == RankingPeriod.ALL));
        if (board.period() != RankingPeriod.ALL)
            lines.append("\nPériode : ").append(board.period().range(date));
        JsonObject body =
                embed(
                        "🏆 Top 10 • " + plain(board.metric().title) + " • " + board.period().label,
                        lines.toString(),
                        color(board.metric()),
                        marker(board));
        return body;
    }

    public static JsonObject event(
            GameDiscordConfig.Event event, Map<String, String> values, String marker) {
        String title = event.title(), text = event.text();
        for (var entry : values.entrySet()) {
            title = title.replace("{" + entry.getKey() + "}", escape(entry.getValue()));
            text = text.replace("{" + entry.getKey() + "}", escape(entry.getValue()));
        }
        return embed(title, text, event.color(), "ClickHunter • EVT:" + marker);
    }

    public static JsonObject embed(String title, String text, int color, String footer) {
        JsonObject body = new JsonObject(),
                embed = new JsonObject(),
                foot = new JsonObject(),
                mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        body.add("allowed_mentions", mentions);
        embed.addProperty("title", plain(title).substring(0, Math.min(256, plain(title).length())));
        embed.addProperty("description", text.substring(0, Math.min(4096, text.length())));
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());
        foot.addProperty("text", footer);
        embed.add("footer", foot);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        body.add("embeds", embeds);
        return body;
    }

    private static int color(RankingMetric metric) {
        return switch (metric) {
            case ECUS -> 0xf1c40f;
            case GEMS -> 0x9b59b6;
            case ASH -> 0x95a5a6;
            case KILLS -> 0xe74c3c;
            case LEVELS -> 0x3498db;
            case DONORS -> 0xe67e22;
        };
    }
}
