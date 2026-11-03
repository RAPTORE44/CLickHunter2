package fr.cliker.clikerv2.discord.game;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.discord.sync.DiscordBotApi;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.rankings.RankingStore;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class DiscordGameBridge implements CommandExecutor, TabCompleter {
    private static volatile DiscordGameBridge current;
    public static final String ADMIN = "cliker.discord.game.admin";
    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final Function<String, DiscordBotApi> clients;
    private volatile Worker worker;

    public DiscordGameBridge(JavaPlugin plugin, DatabaseManager db) {
        this(plugin, db, token -> new DiscordBotApi(token, DiscordBotApi.http()));
    }

    DiscordGameBridge(
            JavaPlugin plugin, DatabaseManager db, Function<String, DiscordBotApi> clients) {
        this.plugin = plugin;
        this.db = db;
        this.clients = clients;
        reload();
        current = this;
    }

    public void start() {
        Objects.requireNonNull(plugin.getCommand("discordjeu")).setExecutor(this);
        plugin.getCommand("discordjeu").setTabCompleter(this);
    }

    private void reload() {
        var settings = new Settings(plugin, "integrations/discord-jeu.yml");
        var shared = new Settings(plugin, "integrations/discord-grades.yml");
        var config =
                GameDiscordConfig.read(
                        settings.yaml, shared.yaml, System.getenv("CLICKHUNTER_DISCORD_BOT_TOKEN"));
        Worker replacement = config.enabled() ? new Worker(config) : null;
        Worker old = worker;
        worker = replacement;
        if (old != null) old.close();
        if (replacement != null) replacement.start();
        try {
            java.nio.file.Files.setPosixFilePermissions(
                    settings.file.toPath(),
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (java.io.IOException | UnsupportedOperationException ignored) {
        }
    }

    /** true = route native configurée : ne pas dupliquer ensuite via le webhook historique. */
    public static boolean route(String event, String id, Map<String, String> values) {
        var service = current;
        Worker worker = service == null ? null : service.worker;
        if (worker == null || worker.closed || !worker.config.events().containsKey(event))
            return false;
        worker.offer(event, id, values);
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage("§cPermission insuffisante.");
            return true;
        }
        String sub = args.length == 0 ? "statut" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload") && args.length == 1) {
            try {
                reload();
                sender.sendMessage(
                        "§aConfiguration Discord du jeu rechargée. /discordjeu statut pour suivre"
                            + " la connexion.");
            } catch (RuntimeException error) {
                sender.sendMessage(
                        "§cConfiguration refusée : vérifie les identifiants, salons et classements."
                            + " L'ancien état est conservé.");
            }
            return true;
        }
        Worker active = worker;
        if (sub.equals("actualiser") && args.length == 1) {
            if (active == null)
                sender.sendMessage(
                        "§eIntégration désactivée. Configure integrations/discord-jeu.yml.");
            else {
                active.force.set(true);
                sender.sendMessage(
                        "§aMise à jour des Top 10 demandée ; les limites Discord restent"
                            + " respectées.");
            }
            return true;
        }
        if (sub.equals("statut")) {
            sender.sendMessage(
                    "§bDiscord jeu : "
                            + (active == null ? "désactivé" : active.status)
                            + " §8• §7"
                            + (active == null ? 0 : active.config.boards().size())
                            + " tableau(x)."
                            + (active == null
                                    ? ""
                                    : " File : " + active.events.size() + " événement(s)."));
            return true;
        }
        sender.sendMessage("§e/discordjeu <statut|reload|actualiser>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        return s.hasPermission(ADMIN) && a.length == 1
                ? List.of("statut", "reload", "actualiser").stream()
                        .filter(v -> v.startsWith(a[0].toLowerCase(Locale.ROOT)))
                        .toList()
                : List.of();
    }

    private record Event(String name, String id, Map<String, String> values, long created) {}

    private final class Worker implements AutoCloseable {
        final GameDiscordConfig config;
        final DiscordBotApi api;
        final DiscordGamePublisher publisher;
        final ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "ClickHunter-DiscordGame");
                            t.setDaemon(true);
                            return t;
                        });
        final ArrayBlockingQueue<Event> events = new ArrayBlockingQueue<>(64);
        final AtomicBoolean force = new AtomicBoolean();
        final Set<String> queued = ConcurrentHashMap.newKeySet();
        volatile boolean closed;
        volatile String status = "connexion en cours";
        boolean ready;
        long retryUntil, nextBoards, nextValidation, lastWarning;

        Worker(GameDiscordConfig config) {
            this.config = config;
            api = clients.apply(config.token());
            publisher =
                    new DiscordGamePublisher(
                            config,
                            new DiscordGameStore(db),
                            new RankingStore(db),
                            api,
                            () -> !closed);
        }

        void start() {
            executor.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.SECONDS);
        }

        void offer(String name, String id, Map<String, String> values) {
            String key = name + ":" + id;
            if (!queued.add(key)) return;
            if (!events.offer(
                    new Event(name, id, Map.copyOf(values), System.currentTimeMillis()))) {
                queued.remove(key);
                warning("file d'événements pleine");
            }
        }

        void tick() {
            if (closed || System.currentTimeMillis() < retryUntil) return;
            try {
                long now = System.currentTimeMillis();
                if (!ready || now >= nextValidation) {
                    publisher.validate();
                    ready = true;
                    nextValidation = System.currentTimeMillis() + 300_000;
                }
                Event event = events.peek();
                if (event != null) {
                    if (now - event.created() < 300_000)
                        publisher.event(event.name(), event.id(), event.values());
                    events.poll();
                    queued.remove(event.name() + ":" + event.id());
                }
                if (now >= nextBoards || force.get()) {
                    boolean forced = force.getAndSet(false);
                    int count;
                    try {
                        count = publisher.refresh(forced);
                    } catch (Exception error) {
                        if (forced) force.set(true);
                        throw error;
                    }
                    nextBoards = System.currentTimeMillis() + 60_000;
                    status = "connecté • " + count + " tableau(x) actualisé(s) au dernier passage";
                } else status = "connecté";
            } catch (Exception error) {
                if (closed) return;
                long retry = 10000;
                String reason = "connexion ou stockage indisponible";
                if (error instanceof DiscordBotApi.Failure f) {
                    retry = f.retryMillis;
                    reason = "HTTP " + f.status;
                    if (f.status == 401 || f.status == 403) ready = false;
                } else if (error instanceof IllegalArgumentException) {
                    retry = 60000;
                    reason = "bot, salons ou permissions à vérifier";
                    ready = false;
                }
                status = reason + " • nouvelle tentative automatique";
                retryUntil = System.currentTimeMillis() + Math.max(1000, retry);
                warning(reason);
            }
        }

        void warning(String reason) {
            synchronized (this) {
                if (System.currentTimeMillis() - lastWarning < 60000) return;
                lastWarning = System.currentTimeMillis();
            }
            plugin.getLogger().warning("Discord jeu : " + reason + ". /discordjeu statut");
        }

        public void close() {
            closed = true;
            events.clear();
            queued.clear();
            executor.shutdownNow();
            api.close();
        }
    }

    public void shutdown() {
        if (current == this) current = null;
        Worker active = worker;
        if (active != null) active.close();
    }
}
