package fr.cliker.clikerv2.discord.sync;

import com.google.gson.*;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.features.Settings;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** Bot natif sans gateway ni plugin Discord additionnel ; une seule file réseau bornée. */
public final class DiscordRankSync implements CommandExecutor, TabCompleter, Listener {
    public static final String LINK = "cliker.discord.link", ADMIN = "cliker.discord.sync.admin";
    private final JavaPlugin plugin;
    private final RankLinkStore store;
    private Map<String, String> messages = Map.of();
    private Worker worker;
    private final java.util.function.Supplier<RankPermissions> permissionsFactory;
    private final java.util.function.Function<String, DiscordBotApi> apiFactory;
    private boolean stopped;
    private final Set<UUID> statusRequests = new HashSet<>();
    private final SecureRandom random = new SecureRandom();

    public DiscordRankSync(JavaPlugin plugin, DatabaseManager database) {
        this(
                plugin,
                database,
                () -> {
                    if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms"))
                        throw new IllegalStateException("LuckPerms est requis.");
                    return new LuckPermsRanks(plugin);
                },
                token -> new DiscordBotApi(token, DiscordBotApi.http()));
    }

    DiscordRankSync(
            JavaPlugin plugin,
            DatabaseManager database,
            java.util.function.Supplier<RankPermissions> permissionsFactory,
            java.util.function.Function<String, DiscordBotApi> apiFactory) {
        this.plugin = plugin;
        this.store = new RankLinkStore(database);
        this.permissionsFactory = permissionsFactory;
        this.apiFactory = apiFactory;
        reload();
    }

    public void start() {
        Objects.requireNonNull(plugin.getCommand("discordsync")).setExecutor(this);
        plugin.getCommand("discordsync").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void reload() {
        Settings settings = new Settings(plugin, "integrations/discord-grades.yml");
        RankSyncConfig next =
                RankSyncConfig.read(settings.yaml, System.getenv("CLICKHUNTER_DISCORD_BOT_TOKEN"));
        Worker replacement = null;
        if (next.enabled()) {
            replacement = new Worker(next, permissionsFactory.get());
        }
        var section = settings.yaml.getConfigurationSection("messages");
        Map<String, String> text = new HashMap<>();
        if (section != null)
            for (String key : section.getKeys(false)) text.put(key, section.getString(key, key));
        Worker old = worker;
        worker = replacement;
        messages = Map.copyOf(text);
        if (old != null) old.close();
        if (replacement != null) replacement.start();
        try {
            java.nio.file.Files.setPosixFilePermissions(
                    settings.file.toPath(),
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (java.io.IOException | UnsupportedOperationException ignored) {
        }
    }

    private void message(CommandSender sender, String key, Map<String, String> values) {
        String text = messages.getOrDefault(key, key);
        for (var entry : values.entrySet())
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        sender.sendMessage(DailySettings.color(text));
    }

    private void message(CommandSender sender, String key) {
        message(sender, key, Map.of());
    }

    private void notify(UUID id, String key) {
        main(
                () -> {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) message(player, key);
                });
    }

    private void main(Runnable action) {
        if (!stopped && plugin.isEnabled())
            fr.cliker.clikerv2.runtime.ServerTasks.run(
                    plugin,
                    () -> {
                        if (!stopped) action.run();
                    });
    }

    public boolean handles(String sub) {
        return Set.of("lier", "statut", "delier").contains(sub.toLowerCase(Locale.ROOT));
    }

    public void playerCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || !handles(args[0])) {
            sender.sendMessage("§e/discord <lier|statut|delier confirmer>");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande réservée aux joueurs.");
            return;
        }
        if (!player.hasPermission(LINK) || !player.hasPermission("cliker.discord.use")) {
            sender.sendMessage("§cPermission insuffisante.");
            return;
        }
        Worker active = worker;
        if (active == null) {
            message(player, "desactive");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("delier") && (args.length != 2 || !args[1].equalsIgnoreCase("confirmer"))) {
            message(player, "delier-confirmation");
            return;
        }
        if (!sub.equals("delier") && args.length != 1) {
            player.sendMessage(
                    "§e/discord lier §7• §e/discord statut §7• §e/discord delier confirmer");
            return;
        }
        UUID id = player.getUniqueId();
        if (sub.equals("statut")) {
            if (statusRequests.size() >= 128 || !statusRequests.add(id)) {
                message(player, "file-pleine");
                return;
            }
            store.link(id)
                    .whenComplete(
                            (link, error) ->
                                    main(
                                            () -> {
                                                statusRequests.remove(id);
                                                if (!fr.cliker.clikerv2.runtime.PlayerSessions
                                                        .isCurrent(player)) return;
                                                if (error != null) {
                                                    message(player, "erreur");
                                                    return;
                                                }
                                                if (link == null) message(player, "non-lie");
                                                else
                                                    player.sendMessage(
                                                            "§bCompte Discord : §f"
                                                                    + link.discord()
                                                                    + (link.unlinking()
                                                                            ? " §e• déliaison en"
                                                                                  + " attente"
                                                                            : " §a• lié"));
                                                player.sendMessage(
                                                        "§7Bot : "
                                                                + active.status
                                                                + " §8• §7Vérification par passages"
                                                                + " toutes les "
                                                                + active.config.interval()
                                                                + " s.");
                                            }));
            return;
        }
        if (!active.ready) {
            message(player, "attente");
            return;
        }
        boolean queued =
                active.enqueue(
                        id,
                        () -> {
                            var link = RankSyncEngine.await(store.link(id));
                            if (sub.equals("lier")) {
                                if (link != null) {
                                    notify(id, "deja-lie");
                                    return;
                                }
                                if (active.pendingUntil < System.currentTimeMillis())
                                    active.seedCursor();
                                String code = code(random);
                                long now = System.currentTimeMillis(),
                                        expires = now + active.config.codeMinutes() * 60_000L;
                                if (!RankSyncEngine.await(
                                        store.issue(
                                                id,
                                                hash(active.config.guild() + ":" + code),
                                                now,
                                                expires))) {
                                    notify(id, "deja-lie");
                                    return;
                                }
                                active.pendingUntil = Math.max(active.pendingUntil, expires);
                                main(
                                        () -> {
                                            if (fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(
                                                    player)) {
                                                String copy =
                                                        "<@"
                                                                + active.engine.bot()
                                                                + "> lier "
                                                                + code;
                                                player.sendMessage(
                                                        DailySettings.color(
                                                                        messages.getOrDefault(
                                                                                        "code",
                                                                                        "&bCode :"
                                                                                            + " &f{code}"
                                                                                            + " &a[Copier]")
                                                                                .replace(
                                                                                        "{code}",
                                                                                        code)
                                                                                .replace(
                                                                                        "{bot}",
                                                                                        active
                                                                                                .engine
                                                                                                .bot()))
                                                                .clickEvent(
                                                                        net.kyori.adventure.text
                                                                                .event.ClickEvent
                                                                                .copyToClipboard(
                                                                                        copy))
                                                                .hoverEvent(
                                                                        net.kyori.adventure.text
                                                                                .Component.text(
                                                                                "Copier le message"
                                                                                    + " à coller"
                                                                                    + " dans"
                                                                                    + " Discord")));
                                                player.sendMessage(
                                                        DailySettings.color(
                                                                        messages.getOrDefault(
                                                                                "salon",
                                                                                "&9[Ouvrir le salon"
                                                                                    + " de liaison]"))
                                                                .clickEvent(
                                                                        net.kyori.adventure.text
                                                                                .event.ClickEvent
                                                                                .openUrl(
                                                                                        "https://discord.com/channels/"
                                                                                                + active
                                                                                                        .config
                                                                                                        .guild()
                                                                                                + "/"
                                                                                                + active
                                                                                                        .config
                                                                                                        .channel())));
                                                message(
                                                        player,
                                                        "expiration",
                                                        Map.of(
                                                                "minutes",
                                                                Integer.toString(
                                                                        active.config
                                                                                .codeMinutes())));
                                            }
                                        });
                            } else {
                                if (link == null) {
                                    notify(id, "non-lie");
                                    return;
                                }
                                RankSyncEngine.await(store.beginUnlink(id));
                                notify(id, "delier-attente");
                                active.sync(RankSyncEngine.await(store.link(id)));
                            }
                        });
        if (!queued) message(player, "file-pleine");
    }

    public static String code(SecureRandom random) {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 12; i++)
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return code.toString();
    }

    public static String hash(String code) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            code.toUpperCase(Locale.ROOT)
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Worker active = worker;
        if (active != null) active.dirty(event.getPlayer().getUniqueId());
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
                        "§aConfiguration des grades Discord rechargée. /discordsync statut pour"
                            + " suivre la connexion.");
            } catch (RuntimeException error) {
                sender.sendMessage(
                        "§cConfiguration refusée : vérifie LuckPerms, les identifiants et les"
                            + " associations. L'ancien état est conservé.");
            }
            return true;
        }
        Worker active = worker;
        if (sub.equals("statut")) {
            sender.sendMessage(
                    "§bGrades Discord : "
                            + (active == null ? "désactivés" : active.status)
                            + " §8• §7"
                            + (active == null ? 0 : active.config.groups().size())
                            + " associations.");
            return true;
        }
        if (sub.equals("sync") && args.length <= 2) {
            if (active == null || !active.ready) {
                message(sender, active == null ? "desactive" : "attente");
                return true;
            }
            UUID id;
            if (args.length == 1 && sender instanceof Player player) id = player.getUniqueId();
            else if (args.length == 2) {
                Player player = Bukkit.getPlayerExact(args[1]);
                try {
                    id = player != null ? player.getUniqueId() : UUID.fromString(args[1]);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cIndique un joueur connecté ou un UUID.");
                    return true;
                }
            } else {
                sender.sendMessage("§e/discordsync sync <joueur connecté|UUID>");
                return true;
            }
            active.dirty(id);
            sender.sendMessage(
                    "§aVérification demandée ; seules les associations configurées seront"
                        + " synchronisées.");
            return true;
        }
        sender.sendMessage("§e/discordsync <statut|reload|sync [joueur connecté|UUID]>");
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) return List.of();
        if (args.length == 1)
            return List.of("statut", "sync", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("sync"))
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(
                            s ->
                                    s.toLowerCase(Locale.ROOT)
                                            .startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        return List.of();
    }

    @FunctionalInterface
    private interface Job {
        void run() throws Exception;
    }

    private final class Worker implements AutoCloseable {
        final RankSyncConfig config;
        final RankPermissions permissions;
        final DiscordBotApi api;
        final RankSyncEngine engine;
        final ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "ClickHunter-DiscordRanks");
                            t.setDaemon(true);
                            return t;
                        });
        final ArrayBlockingQueue<Runnable> jobs = new ArrayBlockingQueue<>(128);
        final Set<UUID> queued = ConcurrentHashMap.newKeySet(),
                dirty = ConcurrentHashMap.newKeySet();
        volatile boolean ready, closed;
        volatile String status = "connexion en cours";
        long retryUntil, nextMessages, nextPoll, nextValidation, pendingUntil, lastWarning;
        String page = "", cursor;

        Worker(RankSyncConfig config, RankPermissions permissions) {
            this.config = config;
            this.permissions = permissions;
            api = apiFactory.apply(config.token());
            engine = new RankSyncEngine(config, store, permissions, api, () -> !closed);
            permissions.subscribe(this::dirty);
        }

        void start() {
            executor.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.SECONDS);
        }

        void dirty(UUID id) {
            if (!closed && dirty.size() < 2000) dirty.add(id);
        }

        boolean enqueue(UUID owner, Job action) {
            if (closed || !queued.add(owner)) return false;
            if (!jobs.offer(
                    () -> {
                        try {
                            if (!closed) action.run();
                        } catch (Exception e) {
                            DiscordRankSync.this.notify(owner, "erreur");
                            failure(e);
                        } finally {
                            queued.remove(owner);
                        }
                    })) {
                queued.remove(owner);
                return false;
            }
            return true;
        }

        void seedCursor() throws Exception {
            var messages =
                    api.request("GET", "/channels/" + config.channel() + "/messages?limit=1")
                            .getAsJsonArray();
            cursor =
                    messages.isEmpty()
                            ? "0"
                            : messages.get(0).getAsJsonObject().get("id").getAsString();
            RankSyncEngine.await(store.cursor(config.channel(), cursor));
        }

        void tick() {
            if (closed || System.currentTimeMillis() < retryUntil) return;
            try {
                long now = System.currentTimeMillis();
                if (!ready) {
                    engine.validate();
                    cursor = RankSyncEngine.await(store.cursor(config.channel()));
                    pendingUntil = RankSyncEngine.await(store.pendingUntil());
                    if (cursor == null) seedCursor();
                    ready = true;
                    status = "connecté";
                    nextValidation = now + 300_000;
                }
                Runnable job = jobs.poll();
                if (job != null) job.run();
                if (closed || System.currentTimeMillis() < retryUntil) return;
                if (now >= nextMessages && pendingUntil >= now) {
                    pollMessages();
                    nextMessages = System.currentTimeMillis() + 5000;
                }
                if (now >= nextValidation) {
                    engine.validate();
                    nextValidation = System.currentTimeMillis() + 300_000;
                }
                int count = 0;
                for (UUID owner : dirty) {
                    if (count++ >= config.batch()) break;
                    dirty.remove(owner);
                    var link = RankSyncEngine.await(store.link(owner));
                    if (link != null) sync(link);
                }
                if (now >= nextPoll) {
                    var links =
                            RankSyncEngine.await(store.page(config.guild(), page, config.batch()));
                    if (links.isEmpty()) {
                        page = "";
                        links =
                                RankSyncEngine.await(
                                        store.page(config.guild(), page, config.batch()));
                    }
                    for (var link : links) {
                        if (closed) return;
                        sync(link);
                        page = link.owner().toString();
                    }
                    nextPoll = System.currentTimeMillis() + config.interval() * 1000L;
                }
                status = "connecté";
            } catch (Exception e) {
                failure(e);
            }
        }

        void sync(RankLinkStore.Link link) throws Exception {
            if (closed) return;
            var outcome = engine.sync(link);
            if (outcome == RankSyncEngine.Outcome.UNLINKED)
                DiscordRankSync.this.notify(link.owner(), "delie");
            if (outcome == RankSyncEngine.Outcome.RETRY) dirty(link.owner());
        }

        void pollMessages() throws Exception {
            Pattern pattern =
                    Pattern.compile(
                            "^<@!?"
                                    + Pattern.quote(engine.bot())
                                    + ">\\s+lier\\s+([A-Z2-9]{12})\\s*$",
                            Pattern.CASE_INSENSITIVE);
            for (int pass = 0; pass < 2; pass++) {
                var array =
                        api.request(
                                        "GET",
                                        "/channels/"
                                                + config.channel()
                                                + "/messages?limit=50&after="
                                                + cursor)
                                .getAsJsonArray();
                List<JsonObject> messages = new ArrayList<>();
                array.forEach(e -> messages.add(e.getAsJsonObject()));
                messages.sort(Comparator.comparing(m -> new BigInteger(m.get("id").getAsString())));
                for (var m : messages) {
                    if (closed) return;
                    String id = m.get("id").getAsString();
                    var author = m.getAsJsonObject("author");
                    if (author != null
                            && !m.has("webhook_id")
                            && (!author.has("bot") || !author.get("bot").getAsBoolean())
                            && m.has("content")) {
                        var match = pattern.matcher(m.get("content").getAsString());
                        if (match.matches()) {
                            String hash = hash(config.guild() + ":" + match.group(1));
                            long now = System.currentTimeMillis();
                            long messageTime =
                                    new BigInteger(id).shiftRight(22).longValueExact()
                                            + 1420070400000L;
                            if (RankSyncEngine.await(store.validCode(hash, messageTime, now))
                                    && api.roles(config.guild(), author.get("id").getAsString())
                                            != null) {
                                var link =
                                        RankSyncEngine.await(
                                                store.redeem(
                                                        hash,
                                                        author.get("id").getAsString(),
                                                        config.guild(),
                                                        messageTime,
                                                        now));
                                if (link != null) {
                                    DiscordRankSync.this.notify(link.owner(), "lie");
                                    dirty(link.owner());
                                }
                            }
                        }
                    }
                    cursor = id;
                }
                if (!messages.isEmpty())
                    RankSyncEngine.await(store.cursor(config.channel(), cursor));
                if (array.size() < 50) break;
            }
        }

        void failure(Exception error) {
            if (closed) return;
            long retry = 10_000;
            String detail = "erreur de connexion ou de stockage";
            if (error instanceof DiscordBotApi.Failure f) {
                retry = f.retryMillis;
                detail = "HTTP " + f.status;
                if (f.status == 401 || f.status == 403) ready = false;
            } else if (error instanceof IllegalArgumentException) {
                retry = 60_000;
                detail = "configuration ou rôles à vérifier";
                ready = false;
            }
            status = detail + " (nouvelle tentative automatique)";
            retryUntil = System.currentTimeMillis() + Math.max(1000, retry);
            if (System.currentTimeMillis() - lastWarning > 60_000) {
                lastWarning = System.currentTimeMillis();
                plugin.getLogger()
                        .warning(
                                "Grades Discord : "
                                        + detail
                                        + ". Aucun jeton n'est affiché. /discordsync statut");
            }
        }

        public void close() {
            closed = true;
            ready = false;
            jobs.clear();
            queued.clear();
            dirty.clear();
            permissions.close();
            executor.shutdownNow();
            api.close();
        }
    }

    public void shutdown() {
        stopped = true;
        statusRequests.clear();
        if (worker != null) worker.close();
        HandlerList.unregisterAll(this);
    }
}
