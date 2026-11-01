package fr.cliker.clikerv2.discord;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class WebhookRelayTest {
    private final URI endpoint =
            WebhookRelay.endpoint("https://discord.com/api/webhooks/123/test-token");

    @Test
    void onlyOfficialHttpsWebhookEndpointsAreAccepted() {
        for (String bad :
                List.of(
                        "http://discord.com/api/webhooks/1/token",
                        "https://discord.com.evil/api/webhooks/1/token",
                        "https://127.0.0.1/api/webhooks/1/token",
                        "https://user@discord.com/api/webhooks/1/token",
                        "https://discord.com:8080/api/webhooks/1/token",
                        "https://discord.com/api/webhooks/1/token#secret",
                        "https://discord.com/other",
                        "https:foo"))
            assertThrows(IllegalArgumentException.class, () -> WebhookRelay.endpoint(bad));
        assertEquals(endpoint, WebhookRelay.endpoint(endpoint.toString()));
    }

    @Test
    void messagesAreEscapedClippedAndCannotPingEveryone() {
        var body =
                JsonParser.parseString(
                                WebhookRelay.payload(
                                        "ClickHunter",
                                        "Titre \"test\"",
                                        "@everyone <@123>\n" + "a".repeat(5000),
                                        0x55ff55))
                        .getAsJsonObject();
        assertTrue(body.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty());
        var embed = body.getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertEquals("Titre \"test\"", embed.get("title").getAsString());
        assertEquals(4000, embed.get("description").getAsString().length());
    }

    @Test
    void deliveryRunsOffTheCallerAndRejectsDuplicatesAndOverflow() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<String> threads = new CopyOnWriteArrayList<>();
        try (var relay =
                new WebhookRelay(
                        endpoint,
                        (uri, body) -> {
                            threads.add(Thread.currentThread().getName());
                            entered.countDown();
                            release.await();
                            return new WebhookRelay.Response(204, 0);
                        },
                        s -> {},
                        2)) {
            assertTrue(relay.offer("one", "{}"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertFalse(relay.offer("one", "{}"));
            assertTrue(relay.offer("two", "{}"));
            assertFalse(relay.offer("three", "{}"));
            assertTrue(relay.pending() <= 2);
            assertTrue(threads.getFirst().startsWith("ClickHunter-Discord"));
            release.countDown();
        }
    }

    @Test
    void rateLimitIsRetriedWithoutRequeueingTheWholeEvent() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch sent = new CountDownLatch(1);
        try (var relay =
                new WebhookRelay(
                        endpoint,
                        (uri, body) -> {
                            if (calls.incrementAndGet() == 1)
                                return new WebhookRelay.Response(429, 10);
                            sent.countDown();
                            return new WebhookRelay.Response(204, 0);
                        },
                        s -> {},
                        4)) {
            assertTrue(relay.offer("event", "{}"));
            assertTrue(sent.await(3, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
            assertFalse(relay.offer("event", "{}"));
        }
    }

    @Test
    void aLongRateLimitIsNotRetriedTooEarly() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch logged = new CountDownLatch(1);
        try (var relay =
                new WebhookRelay(
                        endpoint,
                        (uri, body) -> {
                            calls.incrementAndGet();
                            return new WebhookRelay.Response(429, 120000);
                        },
                        message -> logged.countDown(),
                        4)) {
            relay.offer("limited", "{}");
            assertTrue(logged.await(2, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void failuresNeverPrintTheWebhookSecretAndClosedRelayRefusesNewEvents() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        CountDownLatch logged = new CountDownLatch(1);
        var relay =
                new WebhookRelay(
                        endpoint,
                        (uri, body) -> {
                            throw new IllegalStateException("SECRET " + uri);
                        },
                        s -> {
                            logs.add(s);
                            logged.countDown();
                        },
                        4);
        try {
            assertTrue(relay.offer("one", "{}"));
            assertTrue(logged.await(2, TimeUnit.SECONDS));
            assertFalse(logs.toString().contains("test-token"));
            assertFalse(logs.toString().contains("https://"));
        } finally {
            relay.close();
        }
        assertFalse(relay.offer("two", "{}"));
    }
}
