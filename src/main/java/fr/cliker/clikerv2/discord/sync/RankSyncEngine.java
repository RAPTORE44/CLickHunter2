package fr.cliker.clikerv2.discord.sync;

import com.google.gson.*;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;

/**
 * Réconciliation idempotente, hors du thread Minecraft. Les opérations inachevées sont
 * journalisées.
 */
public final class RankSyncEngine {
    public enum Outcome {
        SYNCHRONIZED,
        MEMBER_ABSENT,
        RETRY,
        UNLINKED,
        OTHER_GUILD
    }

    private final RankSyncConfig config;
    private final RankLinkStore store;
    private final RankPermissions permissions;
    private final DiscordBotApi api;
    private String bot;
    private final java.util.function.BooleanSupplier running;

    public RankSyncEngine(
            RankSyncConfig config,
            RankLinkStore store,
            RankPermissions permissions,
            DiscordBotApi api) {
        this(config, store, permissions, api, () -> true);
    }

    public RankSyncEngine(
            RankSyncConfig config,
            RankLinkStore store,
            RankPermissions permissions,
            DiscordBotApi api,
            java.util.function.BooleanSupplier running) {
        this.config = config;
        this.store = store;
        this.permissions = permissions;
        this.api = api;
        this.running = running;
    }

    private void checkRunning() throws java.io.InterruptedIOException {
        if (!running.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new java.io.InterruptedIOException("Synchronisation arrêtée");
    }

    static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(15, TimeUnit.SECONDS);
    }

    public String bot() {
        return bot;
    }

    public void validate() throws Exception {
        checkRunning();
        permissions.validate(config.groups().keySet());
        var self = api.request("GET", "/users/@me").getAsJsonObject();
        if (!self.has("bot") || !self.get("bot").getAsBoolean())
            throw new IllegalArgumentException("Le jeton doit appartenir à un bot Discord.");
        String id = self.get("id").getAsString();
        var own = api.roles(config.guild(), id);
        if (own == null)
            throw new IllegalArgumentException(
                    "Le bot n'est pas membre du serveur Discord configuré.");
        Map<String, JsonObject> roles = catalog();
        int top = -1;
        BigInteger bits = BigInteger.ZERO;
        for (var entry : roles.entrySet())
            if (own.contains(entry.getKey()) || entry.getKey().equals(config.guild())) {
                top = Math.max(top, entry.getValue().get("position").getAsInt());
                bits = bits.or(new BigInteger(entry.getValue().get("permissions").getAsString()));
            }
        if (!bits.testBit(3) && !bits.testBit(28))
            throw new IllegalArgumentException("Le bot doit pouvoir gérer les rôles.");
        for (String target : config.groups().values()) {
            JsonObject role = roles.get(target);
            if (role == null
                    || role.get("managed").getAsBoolean()
                    || role.get("position").getAsInt() >= top)
                throw new IllegalArgumentException(
                        "Un rôle configuré est absent, géré automatiquement ou au-dessus du bot.");
        }
        var channel = api.request("GET", "/channels/" + config.channel()).getAsJsonObject();
        if (!channel.has("guild_id")
                || !channel.get("guild_id").getAsString().equals(config.guild())
                || channel.get("type").getAsInt() != 0)
            throw new IllegalArgumentException(
                    "Le salon de liaison doit être un salon textuel du serveur configuré.");
        if (!bits.testBit(3)) {
            BigInteger effective = bits, roleAllow = BigInteger.ZERO, roleDeny = BigInteger.ZERO;
            JsonArray overwrites =
                    channel.has("permission_overwrites")
                            ? channel.getAsJsonArray("permission_overwrites")
                            : new JsonArray();
            for (var element : overwrites) {
                var overwrite = element.getAsJsonObject();
                if (overwrite.get("id").getAsString().equals(config.guild()))
                    effective =
                            effective
                                    .andNot(new BigInteger(overwrite.get("deny").getAsString()))
                                    .or(new BigInteger(overwrite.get("allow").getAsString()));
            }
            for (var element : overwrites) {
                var overwrite = element.getAsJsonObject();
                if (overwrite.get("type").getAsInt() == 0
                        && own.contains(overwrite.get("id").getAsString())) {
                    roleDeny = roleDeny.or(new BigInteger(overwrite.get("deny").getAsString()));
                    roleAllow = roleAllow.or(new BigInteger(overwrite.get("allow").getAsString()));
                }
            }
            effective = effective.andNot(roleDeny).or(roleAllow);
            for (var element : overwrites) {
                var overwrite = element.getAsJsonObject();
                if (overwrite.get("type").getAsInt() == 1
                        && overwrite.get("id").getAsString().equals(id))
                    effective =
                            effective
                                    .andNot(new BigInteger(overwrite.get("deny").getAsString()))
                                    .or(new BigInteger(overwrite.get("allow").getAsString()));
            }
            if (!effective.testBit(10) || !effective.testBit(16))
                throw new IllegalArgumentException(
                        "Le bot doit voir le salon et lire son historique.");
        }
        bot = id;
    }

    private Map<String, JsonObject> catalog() throws Exception {
        Map<String, JsonObject> result = new HashMap<>();
        for (var element :
                api.request("GET", "/guilds/" + config.guild() + "/roles").getAsJsonArray()) {
            var role = element.getAsJsonObject();
            result.put(role.get("id").getAsString(), role);
        }
        return result;
    }

    public Outcome sync(RankLinkStore.Link link) throws Exception {
        checkRunning();
        if (!link.guild().equals(config.guild())) {
            if (link.unlinking()) {
                await(store.delete(link));
                return Outcome.UNLINKED;
            }
            return Outcome.OTHER_GUILD;
        }
        Set<String> remote = api.roles(config.guild(), link.discord());
        if (link.unlinking()) {
            if (remote != null)
                for (String role : config.groups().values())
                    if (remote.contains(role)) {
                        checkRunning();
                        api.role(config.guild(), link.discord(), role, false);
                    }
            await(store.delete(link));
            return Outcome.UNLINKED;
        }
        // Quitter Discord / un 404 n'est pas assimilé à un retrait de grade Minecraft.
        if (remote == null) {
            // Discord retire les rôles quand quelqu'un quitte le serveur. À son retour,
            // restaurer ses grades Minecraft plutôt que confondre ce départ avec un retrait staff.
            for (var mapping : config.groups().entrySet()) {
                var state = await(store.state(link, mapping.getKey(), mapping.getValue()));
                if (state.pending() == null || state.toMinecraft())
                    await(
                            store.pending(
                                    link,
                                    mapping.getKey(),
                                    mapping.getValue(),
                                    Boolean.TRUE.equals(state.baseline()),
                                    false));
            }
            return Outcome.MEMBER_ABSENT;
        }
        Map<String, RankPermissions.Membership> local =
                permissions.read(link.owner(), config.groups().keySet());
        for (var mapping : config.groups().entrySet()) {
            checkRunning();
            String group = mapping.getKey(), role = mapping.getValue();
            var membership = local.get(group);
            boolean minecraft = membership.present(), discord = remote.contains(role);
            var state = await(store.state(link, group, role));
            if (minecraft == discord) {
                if (state.pending() != null
                        && state.toMinecraft()
                        && !permissions.apply(link.owner(), group, membership, minecraft))
                    return Outcome.RETRY;
                if (state.baseline() == null
                        || state.pending() != null
                        || state.baseline() != minecraft) {
                    if (!await(store.pending(link, group, role, minecraft, false)))
                        return Outcome.RETRY;
                    await(store.settle(link, group, role, minecraft));
                }
                continue;
            }
            boolean toMinecraft =
                    state.pending() != null
                            ? state.toMinecraft()
                            : state.baseline() == null
                                    ? !config.initialMinecraft()
                                    : minecraft == state.baseline();
            boolean target = toMinecraft ? discord : minecraft;
            if (!await(store.pending(link, group, role, target, toMinecraft))) return Outcome.RETRY;
            checkRunning();
            if (toMinecraft) {
                // Un rôle supprimé du catalogue ne doit pas retirer en masse les achats en jeu.
                var currentRole = catalog().get(role);
                if (currentRole == null || currentRole.get("managed").getAsBoolean())
                    throw new IllegalArgumentException("Rôle Discord configuré indisponible.");
                if (!permissions.apply(link.owner(), group, membership, target))
                    return Outcome.RETRY;
            } else {
                if (!permissions.read(link.owner(), Set.of(group)).get(group).equals(membership))
                    return Outcome.RETRY;
                api.role(config.guild(), link.discord(), role, target);
                if (!permissions.read(link.owner(), Set.of(group)).get(group).equals(membership))
                    return Outcome.RETRY;
            }
            checkRunning();
            await(store.settle(link, group, role, target));
        }
        return Outcome.SYNCHRONIZED;
    }
}
