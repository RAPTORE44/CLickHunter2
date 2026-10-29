package fr.cliker.clikerv2.features;

import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.lootbox.*;
import fr.cliker.clikerv2.rewards.ClaimService;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Réponses lues en asynchrone ; arbitrage, annonces et récompense sur le thread serveur. */
public final class ChatActivities implements Listener {
    record Question(String text, Set<String> answers, String displayedAnswer) {}

    record Round(UUID id, Question question, long until) {}

    private final JavaPlugin plugin;
    private final Settings settings;
    private final Map<UUID, Long> attempts = new HashMap<>();
    private BukkitTask task;
    private volatile Round round;
    private long nextGame, nextHelp;
    private int helpIndex, categoryIndex;
    private volatile boolean stopped;

    public ChatActivities(JavaPlugin plugin) {
        this.plugin = plugin;
        settings = new Settings(plugin, "activites/tchat.yml");
        simplifyDefaultAnswers();
        if (settings.yaml.getInt("jeux.intervalle-secondes", 900) != 900) {
            settings.yaml.set("jeux.intervalle-secondes", 900);
            settings.save();
        }
        nextGame = System.nanoTime() + interval() * 1_000_000_000L;
        nextHelp = System.nanoTime() + 30_000_000_000L;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        bindCommands();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    private void bindCommands() {
        var command = plugin.getCommand("jeutchat");
        if (command == null) return;
        command.setExecutor(
                (sender, cmd, label, args) -> {
                    if (args.length == 0) {
                        sender.sendMessage(
                                round == null
                                        ? "Aucun jeu en cours. Les jeux démarrent"
                                                + " automatiquement dans le chat."
                                        : "Jeu : "
                                                + round.question.text
                                                + " — "
                                                + Math.max(
                                                        0,
                                                        (round.until - System.nanoTime())
                                                                / 1_000_000_000)
                                                + " s restantes. Réponds dans le chat !");
                        return true;
                    }
                    if (!sender.hasPermission("cliker.jeutchat.admin")) {
                        sender.sendMessage("Permission administrateur requise.");
                        return true;
                    }
                    if (args[0].equalsIgnoreCase("stop")) {
                        boolean active = round != null;
                        round = null;
                        attempts.clear();
                        nextGame = System.nanoTime() + interval() * 1_000_000_000L;
                        sender.sendMessage(
                                active
                                        ? "Jeu interrompu. Les récompenses déjà gagnées sont"
                                                + " conservées."
                                        : "Aucun jeu n'est en cours. Les récompenses déjà gagnées"
                                                + " sont conservées.");
                        return true;
                    }
                    if (args[0].equalsIgnoreCase("lancer")) {
                        if (round != null) {
                            sender.sendMessage("Un jeu est déjà en cours.");
                            return true;
                        }
                        String type = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "calcul";
                        if (!Set.of("calcul", "devinette", "pays").contains(type)) {
                            sender.sendMessage("/jeutchat lancer [calcul|devinette|pays]");
                            return true;
                        }
                        start(type);
                        return true;
                    }
                    sender.sendMessage(
                            "/jeutchat ou /jeutchat lancer [calcul|devinette|pays] ou"
                                    + " /jeutchat stop");
                    return true;
                });
        command.setTabCompleter(
                (sender, cmd, label, args) -> {
                    if (!sender.hasPermission("cliker.jeutchat.admin")) return List.of();
                    List<String> values =
                            args.length == 1
                                    ? List.of("lancer", "stop")
                                    : args.length == 2 && args[0].equalsIgnoreCase("lancer")
                                            ? List.of("calcul", "devinette", "pays")
                                            : List.of();
                    String prefix =
                            args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
                    return values.stream().filter(v -> v.startsWith(prefix)).toList();
                });
    }

    Question currentQuestion() {
        return round == null ? null : round.question;
    }

    Round currentRound() {
        return round;
    }

    void clearRound() {
        round = null;
    }

    static String normalize(String answer) {
        return Normalizer.normalize(answer, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("œ", "oe")
                .replace("æ", "ae")
                .trim()
                .replaceFirst("^(le|la|les|un|une)\\s+", "")
                .replaceAll("[^a-z0-9+-]", "");
    }

    /** Ne jamais proposer une question dont la seule réponse exige une phrase. */
    static Question configuredQuestion(String wording, List<?> answers) {
        Set<String> accepted = new HashSet<>();
        String displayed = null;
        for (Object answer : answers) {
            if (!(answer instanceof String value)) continue;
            value = value.strip();
            if (!value.matches("[\\p{L}\\p{M}]+|-?[0-9]+")) continue;
            String normalized = normalize(value);
            if (normalized.isBlank()) continue;
            accepted.add(normalized);
            if (displayed == null) displayed = value;
        }
        return accepted.isEmpty() ? null : new Question(wording, Set.copyOf(accepted), displayed);
    }

    /** Adapter les anciennes questions livrées sans remplacer les questions personnalisées. */
    private void simplifyDefaultAnswers() {
        List<Map<?, ?>> updated = new ArrayList<>();
        boolean changed = false;
        for (var entry : settings.yaml.getMapList("questions.devinette")) {
            Map<Object, Object> copy = new LinkedHashMap<>(entry);
            Object answers = entry.get("reponses");
            if (Objects.equals(
                            entry.get("question"),
                            "Je transporte un coffre et glisse sur des rails. Qui suis-je ?")
                    && Objects.equals(
                            answers,
                            List.of(
                                    "wagonnet à coffre",
                                    "wagonnet avec coffre",
                                    "minecart coffre",
                                    "chest minecart"))) {
                copy.put("reponses", List.of("wagonnet", "minecart"));
                changed = true;
            } else if (Objects.equals(
                            entry.get("question"),
                            "On me fabrique avec neuf lingots de fer. Quel bloc suis-je ?")
                    && Objects.equals(answers, List.of("bloc de fer", "iron block"))) {
                copy.put("question", "Quel métal compose le golem qui protège les villages ?");
                copy.put("reponses", List.of("fer", "iron"));
                changed = true;
            }
            updated.add(copy);
        }
        if (changed) {
            settings.yaml.set("questions.devinette", updated);
            settings.save();
        }
    }

    private int interval() {
        return Math.max(30, Math.min(86400, settings.yaml.getInt("jeux.intervalle-secondes", 900)));
    }

    private void announce(String key, Map<String, String> variables) {
        String template = settings.yaml.getString("messages." + key, "");
        Bukkit.broadcast(DailySettings.color(DailySettings.replace(template, variables)));
    }

    private void tick() {
        long now = System.nanoTime();
        if (round != null && now >= round.until) {
            announce("expire", Map.of("reponse", round.question.displayedAnswer));
            round = null;
            attempts.clear();
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        if (settings.yaml.getBoolean("aides.actives", true) && now >= nextHelp) {
            List<String> messages = settings.yaml.getStringList("aides.messages");
            if (!messages.isEmpty()) {
                Component message =
                        DailySettings.color(
                                "&b[Aide] &f" + messages.get(helpIndex++ % messages.size()));
                var match =
                        java.util.regex.Pattern.compile(
                                        "/(daily|warp|sac|ah|session|avantposte|jeutchat|menu|sell|claim)\\b")
                                .matcher(messages.get((helpIndex - 1) % messages.size()));
                if (match.find())
                    message =
                            message.clickEvent(
                                    net.kyori.adventure.text.event.ClickEvent.suggestCommand(
                                            match.group()));
                Bukkit.broadcast(message);
            }
            nextHelp =
                    now
                            + Math.max(60, settings.yaml.getInt("aides.intervalle-secondes", 180))
                                    * 1_000_000_000L;
        }
        if (round == null && settings.yaml.getBoolean("jeux.actifs", true) && now >= nextGame) {
            String[] types = {"calcul", "devinette", "pays"};
            start(types[categoryIndex++ % types.length]);
        }
    }

    private Question question(String type) {
        if (type.equals("calcul")) {
            int a = ThreadLocalRandom.current().nextInt(2, 51),
                    b = ThreadLocalRandom.current().nextInt(2, 21),
                    operator = ThreadLocalRandom.current().nextInt(3);
            int result = operator == 0 ? a + b : operator == 1 ? a - b : a * b;
            String symbol = operator == 0 ? "+" : operator == 1 ? "-" : "×";
            return new Question(
                    "Combien font " + a + " " + symbol + " " + b + " ?",
                    Set.of(Integer.toString(result)),
                    Integer.toString(result));
        }
        List<Question> questions = new ArrayList<>();
        for (var entry : settings.yaml.getMapList("questions." + type)) {
            Object text = entry.get("question"), raw = entry.get("reponses");
            if (!(text instanceof String wording)
                    || !(raw instanceof List<?> answers)
                    || answers.isEmpty()) continue;
            Question parsed = configuredQuestion(wording, answers);
            if (parsed != null) questions.add(parsed);
        }
        return questions.isEmpty()
                ? question("calcul")
                : questions.get(ThreadLocalRandom.current().nextInt(questions.size()));
    }

    void start(String type) {
        Question question = question(type);
        int duration = Math.max(10, Math.min(300, settings.yaml.getInt("jeux.duree-secondes", 45)));
        round =
                new Round(
                        UUID.randomUUID(), question, System.nanoTime() + duration * 1_000_000_000L);
        attempts.clear();
        nextGame = System.nanoTime() + interval() * 1_000_000_000L;
        announce(
                "debut",
                Map.of(
                        "question",
                        question.text
                                + " "
                                + settings.yaml.getString(
                                        "messages.consigne",
                                        "&7Un mot ou un nombre suffit ; accents facultatifs."),
                        "secondes",
                        Integer.toString(duration)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chat(AsyncChatEvent event) {
        Round observedRound = round;
        if (observedRound == null || stopped) return;
        Player player = event.getPlayer();
        String answer = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (answer.length() > 128) return;
        fr.cliker.clikerv2.runtime.ServerTasks.run(
                plugin, () -> answer(player, answer, observedRound));
    }

    void answer(Player player, String answer) {
        answer(player, answer, round);
    }

    void answer(Player player, String answer, Round observedRound) {
        if (stopped
                || observedRound == null
                || round != observedRound
                || System.nanoTime() >= round.until
                || !fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(player)) return;
        long now = System.nanoTime(), last = attempts.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 500_000_000L) return;
        attempts.put(player.getUniqueId(), now);
        if (!round.question.answers.contains(normalize(answer))) return;
        Round won = round;
        round = null;
        attempts.clear();
        var prize = LootboxItem.create(LootboxTier.DIVIN);
        String payload = ClaimService.serialize(prize);
        reward(
                won.id,
                player.getUniqueId(),
                player.getName(),
                payload,
                won.question.displayedAnswer);
    }

    private void reward(UUID source, UUID owner, String name, String payload, String answer) {
        var claims = Bukkit.getServicesManager().load(ClaimService.class);
        if (claims == null) {
            plugin.getLogger().severe("Récompense de jeu indisponible : service /claim absent.");
            return;
        }
        claims.store()
                .enqueue(owner, "chat-game:" + source, payload)
                .whenComplete(
                        (entry, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            if (stopped) return;
                                            if (error != null) {
                                                plugin.getLogger()
                                                        .log(
                                                                java.util.logging.Level.SEVERE,
                                                                "Récompense de chat à réessayer",
                                                                error);
                                                Bukkit.getScheduler()
                                                        .runTaskLater(
                                                                plugin,
                                                                () ->
                                                                        reward(
                                                                                source, owner, name,
                                                                                payload, answer),
                                                                100);
                                                return;
                                            }
                                            announce(
                                                    "gagnant",
                                                    Map.of("joueur", name, "reponse", answer));
                                            claims.notifyAndDeliver(owner);
                                        }));
    }

    public void shutdown() {
        stopped = true;
        if (task != null) task.cancel();
        round = null;
        attempts.clear();
        HandlerList.unregisterAll(this);
    }
}
