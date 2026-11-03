package fr.cliker.clikerv2.discord.game;

import com.google.gson.*;

import fr.cliker.clikerv2.discord.sync.*;
import fr.cliker.clikerv2.rankings.*;

import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Édition des messages existants, récupération après redémarrage et protection des mentions. */
public final class DiscordGamePublisher {
    private final GameDiscordConfig config;
    private final DiscordGameStore store;
    private final RankingStore rankings;
    private final DiscordBotApi api;
    private final BooleanSupplier running;
    private String bot;
    private Set<String> forcedPending;

    public DiscordGamePublisher(
            GameDiscordConfig config,
            DiscordGameStore store,
            RankingStore rankings,
            DiscordBotApi api,
            BooleanSupplier running) {
        this.config = config;
        this.store = store;
        this.rankings = rankings;
        this.api = api;
        this.running = running;
    }

    public static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(15, TimeUnit.SECONDS);
    }

    private void check() throws java.io.InterruptedIOException {
        if (!running.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new java.io.InterruptedIOException("Publication arrêtée");
    }

    public void validate() throws Exception {
        check();
        var me = api.request("GET", "/users/@me").getAsJsonObject();
        if (!me.has("bot") || !me.get("bot").getAsBoolean())
            throw new IllegalArgumentException("Jeton de bot nécessaire");
        String id = me.get("id").getAsString();
        var own = api.roles(config.guild(), id);
        if (own == null) throw new IllegalArgumentException("Bot absent du serveur");
        BigInteger bits = BigInteger.ZERO;
        for (var element :
                api.request("GET", "/guilds/" + config.guild() + "/roles").getAsJsonArray()) {
            var role = element.getAsJsonObject();
            if (own.contains(role.get("id").getAsString())
                    || role.get("id").getAsString().equals(config.guild()))
                bits = bits.or(new BigInteger(role.get("permissions").getAsString()));
        }
        Set<String> channels = new HashSet<>();
        config.boards().forEach(b -> channels.add(b.channel()));
        config.events().values().forEach(e -> channels.add(e.channel()));
        for (String channelId : channels) {
            check();
            var channel = api.request("GET", "/channels/" + channelId).getAsJsonObject();
            if (!channel.has("guild_id")
                    || !channel.get("guild_id").getAsString().equals(config.guild())
                    || channel.get("type").getAsInt() != 0)
                throw new IllegalArgumentException("Salon textuel du serveur requis");
            BigInteger effective = effective(bits, own, id, config.guild(), channel);
            if (!effective.testBit(3)
                    && (!effective.testBit(10)
                            || !effective.testBit(11)
                            || !effective.testBit(14)
                            || !effective.testBit(16)))
                throw new IllegalArgumentException(
                        "Permissions Voir, Envoyer, Intégrer des liens et Historique nécessaires");
        }
        bot = id;
    }

    private static BigInteger effective(
            BigInteger bits, Set<String> own, String bot, String guild, JsonObject channel) {
        if (bits.testBit(3)) return bits;
        JsonArray overrides =
                channel.has("permission_overwrites")
                        ? channel.getAsJsonArray("permission_overwrites")
                        : new JsonArray();
        BigInteger permissions = bits, allow = BigInteger.ZERO, deny = BigInteger.ZERO;
        for (var e : overrides) {
            var o = e.getAsJsonObject();
            if (o.get("id").getAsString().equals(guild))
                permissions =
                        permissions
                                .andNot(new BigInteger(o.get("deny").getAsString()))
                                .or(new BigInteger(o.get("allow").getAsString()));
        }
        for (var e : overrides) {
            var o = e.getAsJsonObject();
            if (o.get("type").getAsInt() == 0 && own.contains(o.get("id").getAsString())) {
                deny = deny.or(new BigInteger(o.get("deny").getAsString()));
                allow = allow.or(new BigInteger(o.get("allow").getAsString()));
            }
        }
        permissions = permissions.andNot(deny).or(allow);
        for (var e : overrides) {
            var o = e.getAsJsonObject();
            if (o.get("type").getAsInt() == 1 && o.get("id").getAsString().equals(bot))
                permissions =
                        permissions
                                .andNot(new BigInteger(o.get("deny").getAsString()))
                                .or(new BigInteger(o.get("allow").getAsString()));
        }
        return permissions;
    }

    public int refresh(boolean force) throws Exception {
        int changed = 0;
        if (force && forcedPending == null) {
            forcedPending = new HashSet<>();
            config.boards().forEach(board -> forcedPending.add(board.key()));
        }
        LocalDate date = LocalDate.now(config.zone());
        for (var board : config.boards()) {
            check();
            var previous = await(store.message(bot, board.channel(), board.key()));
            boolean forceBoard = forcedPending != null && forcedPending.contains(board.key());
            if (!forceBoard
                    && previous != null
                    && System.currentTimeMillis() - previous.updated() < config.interval())
                continue;
            var ranking = await(rankings.board(board.metric(), board.period(), date));
            JsonObject body = DiscordGamePayload.board(board, ranking, date);
            String id =
                    previous == null
                            ? find(board.channel(), DiscordGamePayload.marker(board))
                            : previous.id();
            if (id != null)
                try {
                    api.request("PATCH", "/channels/" + board.channel() + "/messages/" + id, body);
                } catch (DiscordBotApi.Failure error) {
                    if (error.status != 404) throw error;
                    id = null;
                }
            if (id == null) {
                check();
                id =
                        create(
                                board.channel(),
                                body,
                                "board:"
                                        + board.key()
                                        + ":"
                                        + (System.currentTimeMillis() / config.interval()));
            }
            await(store.save(bot, board.channel(), board.key(), id, System.currentTimeMillis()));
            if (forcedPending != null) forcedPending.remove(board.key());
            changed++;
        }
        if (forcedPending != null && forcedPending.isEmpty()) forcedPending = null;
        return changed;
    }

    private String find(String channel, String marker) throws Exception {
        for (var e :
                api.request("GET", "/channels/" + channel + "/messages?limit=100")
                        .getAsJsonArray()) {
            var message = e.getAsJsonObject();
            if (!message.has("author")
                    || !message.getAsJsonObject("author").get("id").getAsString().equals(bot)
                    || !message.has("embeds")) continue;
            for (var embed : message.getAsJsonArray("embeds")) {
                var object = embed.getAsJsonObject();
                if (object.has("footer")
                        && object.getAsJsonObject("footer").has("text")
                        && marker.equals(
                                object.getAsJsonObject("footer").get("text").getAsString()))
                    return message.get("id").getAsString();
            }
        }
        return null;
    }

    private String create(String channel, JsonObject body, String key) throws Exception {
        check();
        var request = body.deepCopy();
        request.addProperty(
                "nonce", DiscordRankSync.hash(bot + ":" + channel + ":" + key).substring(0, 24));
        request.addProperty("enforce_nonce", true);
        return api.request("POST", "/channels/" + channel + "/messages", request)
                .getAsJsonObject()
                .get("id")
                .getAsString();
    }

    public boolean event(String name, String eventId, Map<String, String> values) throws Exception {
        check();
        var event = config.events().get(name);
        if (event == null) return false;
        String key =
                DiscordRankSync.hash(bot + ":" + event.channel() + ":" + name + ":" + eventId)
                        .substring(0, 24);
        if (await(store.sent(key))) return false;
        var substitutions = new HashMap<>(values);
        substitutions.put("adresse", config.address());
        substitutions.put("serveur", "ClickHunter");
        JsonObject body = DiscordGamePayload.event(event, substitutions, key);
        // Un succès HTTP sans acquittement SQL peut être retrouvé dans l'historique du salon.
        if (find(event.channel(), "ClickHunter • EVT:" + key) == null)
            create(event.channel(), body, "event:" + key);
        await(store.sent(key, System.currentTimeMillis()));
        return true;
    }
}
