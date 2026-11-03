package fr.cliker.clikerv2.discord.sync;

import com.google.gson.*;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** API REST du bot, utilisée uniquement depuis son worker. Aucun token dans les erreurs. */
public final class DiscordBotApi implements AutoCloseable {
    public record Reply(int status, String body, long retryMillis) {}

    @FunctionalInterface
    public interface Transport extends AutoCloseable {
        Reply request(String method, String path, String token) throws Exception;

        default void close() {}
    }

    public static final class Failure extends Exception {
        public final int status;
        public final long retryMillis;

        public Failure(int status, long retryMillis) {
            super("Discord HTTP " + status);
            this.status = status;
            this.retryMillis = retryMillis;
        }
    }

    private final String token;
    private final Transport transport;
    private long nextRequest;
    private volatile boolean closed;
    private final long spacing;

    public DiscordBotApi(String token, Transport transport) {
        this(token, transport, 250);
    }

    DiscordBotApi(String token, Transport transport, long spacing) {
        this.token = token;
        this.transport = transport;
        this.spacing = spacing;
    }

    public JsonElement request(String method, String path) throws Exception {
        if (closed || Thread.currentThread().isInterrupted())
            throw new java.io.InterruptedIOException("Client Discord arrêté");
        long delay = nextRequest - System.currentTimeMillis();
        if (delay > 1500) throw new Failure(429, delay);
        if (delay > 0) Thread.sleep(delay);
        if (closed || Thread.currentThread().isInterrupted())
            throw new java.io.InterruptedIOException("Client Discord arrêté");
        Reply reply = transport.request(method, path, token);
        nextRequest = System.currentTimeMillis() + spacing;
        if (reply.status() == 429) {
            long retry = Math.max(1000, Math.min(3_600_000, reply.retryMillis()));
            nextRequest = System.currentTimeMillis() + retry;
            throw new Failure(429, retry);
        }
        if (reply.status() < 200 || reply.status() >= 300)
            throw new Failure(reply.status(), reply.status() >= 500 ? 10_000 : 60_000);
        return reply.body() == null || reply.body().isBlank()
                ? JsonNull.INSTANCE
                : JsonParser.parseString(reply.body());
    }

    public Set<String> roles(String guild, String user) throws Exception {
        try {
            JsonObject member =
                    request("GET", "/guilds/" + guild + "/members/" + user).getAsJsonObject();
            Set<String> roles = new HashSet<>();
            member.getAsJsonArray("roles").forEach(r -> roles.add(r.getAsString()));
            return Set.copyOf(roles);
        } catch (Failure failure) {
            if (failure.status == 404) return null;
            throw failure;
        }
    }

    public void role(String guild, String user, String role, boolean add) throws Exception {
        request(add ? "PUT" : "DELETE", "/guilds/" + guild + "/members/" + user + "/roles/" + role);
    }

    public static Transport http() {
        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        return new Transport() {
            public Reply request(String method, String path, String token) throws Exception {
                if (!path.matches("/[A-Za-z0-9_/@?=&.-]+"))
                    throw new IllegalArgumentException("Route Discord invalide");
                var request =
                        HttpRequest.newBuilder(URI.create("https://discord.com/api/v10" + path))
                                .timeout(Duration.ofSeconds(8))
                                .header("Authorization", "Bot " + token)
                                .header("User-Agent", "DiscordBot (https://clickhunter.fr, 1.0.12)")
                                .method(method, HttpRequest.BodyPublishers.noBody())
                                .build();
                var future = client.sendAsync(request, info -> new LimitedBody(2_097_152));
                HttpResponse<byte[]> response;
                try {
                    response = future.get(9, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException | InterruptedException error) {
                    future.cancel(true);
                    throw error;
                }
                String body = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
                long retry = 5000;
                if (response.statusCode() == 429)
                    try {
                        retry =
                                (long)
                                        Math.ceil(
                                                JsonParser.parseString(body)
                                                                .getAsJsonObject()
                                                                .get("retry_after")
                                                                .getAsDouble()
                                                        * 1000);
                    } catch (RuntimeException ignored) {
                    }
                return new Reply(response.statusCode(), body, retry);
            }

            public void close() {
                client.shutdownNow();
            }
        };
    }

    /** Annule le flux dès qu'il dépasse la limite, au lieu de mettre tout le corps en mémoire. */
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final java.util.concurrent.CompletableFuture<byte[]> body =
                new java.util.concurrent.CompletableFuture<>();
        private java.util.concurrent.Flow.Subscription subscription;

        LimitedBody(int maximum) {
            this.maximum = maximum;
        }

        public java.util.concurrent.CompletionStage<byte[]> getBody() {
            return body;
        }

        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            if (body.isDone()) return;
            for (var buffer : buffers) {
                if (buffer.remaining() > maximum - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Réponse Discord trop volumineuse"));
                    return;
                }
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }

        public void onError(Throwable error) {
            body.completeExceptionally(error);
        }

        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }

    public void close() {
        closed = true;
        transport.close();
    }
}
