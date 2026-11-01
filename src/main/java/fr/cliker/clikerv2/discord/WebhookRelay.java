package fr.cliker.clikerv2.discord;

import com.google.gson.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Un seul envoi en vol, file et déduplication bornées. Aucun appel Bukkit dans le worker. */
public final class WebhookRelay implements AutoCloseable {
    public record Response(int status, long retryMillis) {}

    @FunctionalInterface
    public interface Sender extends AutoCloseable {
        Response send(URI endpoint, String body) throws Exception;

        default void close() {}
    }

    private record Pending(String body, int attempts) {}

    private final URI endpoint;
    private final Sender sender;
    private final Consumer<String> log;
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private final LinkedHashMap<String, Long> recent = new LinkedHashMap<>();
    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "ClickHunter-Discord");
                        t.setDaemon(true);
                        return t;
                    });
    private final int capacity;
    private boolean running, closed;
    private long lastWarning;

    public WebhookRelay(URI endpoint, Sender sender, Consumer<String> log, int capacity) {
        this.endpoint = endpoint;
        this.sender = sender;
        this.log = log;
        this.capacity = Math.max(1, Math.min(256, capacity));
    }

    public static URI endpoint(String raw) {
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Webhook Discord invalide");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || !Set.of("discord.com", "discordapp.com").contains(uri.getHost())
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawPath() == null
                || !uri.getRawPath().matches("/api(?:/v[0-9]+)?/webhooks/[0-9]+/[A-Za-z0-9_-]+")
                || (uri.getRawQuery() != null
                        && !Set.of("wait=true", "wait=false").contains(uri.getRawQuery())))
            throw new IllegalArgumentException("Webhook Discord invalide");
        return uri;
    }

    public static Sender http() {
        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        return new Sender() {
            public Response send(URI uri, String body) throws Exception {
                var request =
                        HttpRequest.newBuilder(uri)
                                .timeout(Duration.ofSeconds(10))
                                .header("Content-Type", "application/json; charset=utf-8")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();
                var result = client.send(request, HttpResponse.BodyHandlers.discarding());
                long retry = 5000;
                if (result.statusCode() == 429) {
                    try {
                        retry =
                                (long)
                                        Math.ceil(
                                                Double.parseDouble(
                                                                result.headers()
                                                                        .firstValue("Retry-After")
                                                                        .orElse("5"))
                                                        * 1000);
                    } catch (NumberFormatException ignored) {
                    }
                }
                return new Response(result.statusCode(), Math.max(1000, retry));
            }

            public void close() {
                client.shutdownNow();
            }
        };
    }

    public synchronized boolean offer(String id, String body) {
        if (closed) return false;
        if (queue.size() + (running ? 1 : 0) >= capacity) {
            warn("Notification Discord ignorée : file pleine (limite configurée atteinte).");
            return false;
        }
        long now = System.currentTimeMillis();
        recent.entrySet().removeIf(e -> now - e.getValue() > 600_000);
        if (recent.containsKey(id)) return false;
        recent.put(id, now);
        while (recent.size() > 512) recent.remove(recent.keySet().iterator().next());
        queue.addLast(new Pending(body, 0));
        if (!running) {
            running = true;
            worker.execute(this::drain);
        }
        return true;
    }

    public synchronized int pending() {
        return queue.size() + (running ? 1 : 0);
    }

    private synchronized void warn(String text) {
        if (closed) return;
        long now = System.currentTimeMillis();
        if (now - lastWarning >= 60000) {
            lastWarning = now;
            log.accept(text);
        }
    }

    private void drain() {
        Pending job;
        synchronized (this) {
            if (closed || (job = queue.pollFirst()) == null) {
                running = false;
                return;
            }
        }
        long delay = 1500;
        try {
            Response response = sender.send(endpoint, job.body);
            if (response.status == 429 && job.attempts < 2 && response.retryMillis <= 60000) {
                synchronized (this) {
                    if (!closed) queue.addFirst(new Pending(job.body, job.attempts + 1));
                }
                delay = response.retryMillis;
            } else if (response.status < 200 || response.status >= 300)
                warn(
                        "Notification Discord non transmise (HTTP "
                                + response.status
                                + "). Vérifier le webhook et les permissions du salon.");
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            warn(
                    "Notification Discord interrompue ; résultat distant inconnu. Aucun renvoi"
                            + " automatique pour éviter un doublon.");
        }
        synchronized (this) {
            if (!closed) worker.schedule(this::drain, delay, TimeUnit.MILLISECONDS);
            else running = false;
        }
    }

    public static String payload(String username, String title, String description, int color) {
        JsonObject json = new JsonObject();
        json.addProperty("username", clip(username, 80));
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        json.add("allowed_mentions", mentions);
        JsonObject embed = new JsonObject();
        embed.addProperty("title", clip(title, 256));
        embed.addProperty("description", clip(description, 4000));
        embed.addProperty("color", Math.max(0, Math.min(0xffffff, color)));
        embed.addProperty("timestamp", java.time.Instant.now().toString());
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        json.add("embeds", embeds);
        return json.toString();
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    @Override
    public synchronized void close() {
        closed = true;
        queue.clear();
        recent.clear();
        worker.shutdownNow();
        sender.close();
    }
}
