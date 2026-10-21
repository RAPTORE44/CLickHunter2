package fr.cliker.clikerv2.daily;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.*;
import java.util.*;
import java.util.logging.Level;

/** Module intégré à ClikerV2 ; toutes les interactions Bukkit restent sur le thread principal. */
public final class DailyModule implements CommandExecutor, TabCompleter, Listener {
    public static final String USE_PERMISSION = "cliker.daily.use";
    public static final String ADMIN_PERMISSION = "cliker.daily.admin";
    private final JavaPlugin plugin;
    private final DailySettings settings;
    private final RewardStore store;
    private final DailyMenu menu;
    private final DailyNpc npc;
    private final Set<UUID> pending = new HashSet<>();
    private final Map<UUID, Object> opening = new HashMap<>();
    private Clock clock;
    private BukkitTask ticker;
    private volatile boolean stopped;

    public DailyModule(JavaPlugin plugin) throws Exception {
        this.plugin = plugin;
        settings = new DailySettings(plugin);
        clock = Clock.system(settings.zone());
        store = new RewardStore(plugin.getDataFolder().toPath().resolve("data/daily.db"));
        menu = new DailyMenu(this);
        npc = new DailyNpc(this);
    }
    public void start() {
        var manager = plugin.getServer().getPluginManager();
        manager.registerEvents(menu, plugin);
        manager.registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("daily")).setExecutor(this);
        plugin.getCommand("daily").setTabCompleter(this);
        npc.start();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, menu::refreshOpenMenus, 20L, 20L);
    }
    public JavaPlugin plugin() { return plugin; }
    public DailySettings settings() { return settings; }
    public ZonedDateTime now() { return ZonedDateTime.now(clock); }
    public void cancelOpen(UUID uuid) { opening.remove(uuid); }
    public void open(Player player) {
        if (!player.hasPermission(USE_PERMISSION)) { send(player, "sans-permission"); return; }
        Object token = new Object();
        opening.put(player.getUniqueId(), token);
        send(player, "chargement");
        store.load(player.getUniqueId()).whenComplete((progress, error) -> sync(() -> {
            if (!player.isOnline() || opening.get(player.getUniqueId()) != token) return;
            opening.remove(player.getUniqueId());
            if (error != null) { failure(player, error); return; }
            menu.open(player, progress);
        }));
    }
    public void claim(Player player, int day) {
        if (!player.hasPermission(USE_PERMISSION)) { send(player, "sans-permission"); return; }
        UUID uuid = player.getUniqueId();
        if (!pending.add(uuid)) { send(player, "traitement"); return; }
        // Figer les commandes avant la transaction : une configuration vide ne consomme pas le jour.
        List<String> commands = settings.commands(day);
        if (commands.isEmpty()) { pending.remove(uuid); send(player, "erreur"); return; }
        store.claim(uuid, day, () -> LocalDate.now(clock)).whenComplete((claim, error) -> {
            if (stopped || !plugin.isEnabled()) {
                if (error != null || claim != null && claim.accepted()) plugin.getLogger().warning("Récompense à vérifier après arrêt : joueur=" + uuid + ", jour=" + day);
                return;
            }
            sync(() -> {
                pending.remove(uuid);
                if (error != null) { failure(player, error); return; }
                // Invalider aussi un chargement de menu ancien pour ne pas afficher une progression périmée.
                opening.remove(uuid);
                if (claim.accepted()) {
                    Map<String, String> values = Map.of("joueur", player.getName(), "uuid", uuid.toString(), "jour", Integer.toString(day));
                    boolean successful = true;
                    for (String raw : commands) {
                        String command = DailySettings.replace(raw, values);
                        if (command.startsWith("/")) command = command.substring(1);
                        try {
                            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) successful = false;
                        } catch (RuntimeException e) { successful = false; plugin.getLogger().log(Level.SEVERE, "Commande de récompense échouée pour " + uuid + ", jour " + day, e); }
                    }
                    if (!successful) {
                        plugin.getLogger().severe("Vérifier manuellement les commandes de récompense : " + uuid + ", jour=" + day + ". La récupération reste enregistrée pour éviter les doublons.");
                        if (player.isOnline()) send(player, "erreur-commande");
                    } else if (player.isOnline()) player.sendMessage(settings.message("recupere", values));
                } else if (player.isOnline()) send(player, "indisponible");
                if (player.isOnline()) menu.update(player, claim.progress());
            });
        });
    }
    private void sync(Runnable task) {
        if (!stopped && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);
    }
    private void failure(Player player, Throwable error) {
        plugin.getLogger().log(Level.SEVERE, "Erreur de progression journalière pour " + player.getUniqueId(), error);
        if (player.isOnline()) send(player, "erreur");
    }
    void send(CommandSender sender, String message) { sender.sendMessage(settings.message(message, Map.of())); }
    @EventHandler public void quit(PlayerQuitEvent event) { opening.remove(event.getPlayer().getUniqueId()); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) open(player); else send(sender, "joueur-uniquement");
            return true;
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) { send(sender, "sans-permission"); return true; }
        if (args.length == 2 && args[0].equalsIgnoreCase("pnj")) {
            if (!npc.available()) { send(sender, "citizens-absent"); return true; }
            try {
                if (args[1].equalsIgnoreCase("spawn")) {
                    if (sender instanceof Player player) send(sender, npc.spawn(player.getLocation()) ? "pnj-cree" : "pnj-existant");
                    else send(sender, "joueur-uniquement");
                    return true;
                }
                if (args[1].equalsIgnoreCase("remove")) { send(sender, npc.remove() ? "pnj-supprime" : "pnj-absent"); return true; }
            } catch (Exception e) { plugin.getLogger().log(Level.SEVERE, "Gestion du PNJ journalier impossible", e); send(sender, "erreur"); return true; }
        }
        send(sender, "usage");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) return List.of();
        List<String> options = args.length == 1 ? List.of("pnj") : args.length == 2 && args[0].equalsIgnoreCase("pnj") ? List.of("spawn", "remove") : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(prefix)).toList();
    }
    public void shutdown() {
        stopped = true;
        if (ticker != null) ticker.cancel();
        npc.shutdown();
        menu.shutdown();
        opening.clear();
        store.close();
    }
}
