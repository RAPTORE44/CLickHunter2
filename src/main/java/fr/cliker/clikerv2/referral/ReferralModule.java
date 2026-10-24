package fr.cliker.clikerv2.referral;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.daily.DailySettings;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.lootbox.*;
import fr.cliker.clikerv2.rewards.*;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Clock;
import java.util.*;
import java.util.logging.Level;

public final class ReferralModule implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final ClaimService claims;
    private final ReferralStore store;
    private final YamlConfiguration config;
    private final Clock clock;
    private final long window;
    private final Map<UUID, ReferralStore.PlayerState> states = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Object> sessions = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>(), notifying = new HashSet<>();
    private BukkitTask ticker;
    private volatile boolean stopped;

    public ReferralModule(JavaPlugin plugin, DatabaseManager database, ClaimService claims) { this(plugin, database, claims, Clock.systemUTC()); }
    ReferralModule(JavaPlugin plugin, DatabaseManager database, ClaimService claims, Clock clock) {
        this.plugin = plugin; this.claims = claims; this.clock = clock;
        File file = new File(plugin.getDataFolder(), "parrainage.yml");
        if (!file.exists()) plugin.saveResource("parrainage.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        boolean corrected = false;
        for (String key : List.of("barre.titre", "messages.invitation")) {
            String previous = config.getString(key, "");
            String updated = previous.replaceAll("/parrein(?=\\s|$)", "/parrain");
            if (!updated.equals(previous)) { config.set(key, updated); corrected = true; }
        }
        if (corrected) {
            try { config.save(file); }
            catch (java.io.IOException error) { throw new IllegalStateException("Correction de la commande de parrainage impossible", error); }
        }
        long minutes = config.getLong("delai-minutes", 30); if (minutes < 1 || minutes > 10080) throw new IllegalArgumentException("Délai de parrainage invalide");
        window = minutes * 60000;
        Map<CurrencyType, Long> initial = new EnumMap<>(CurrencyType.class);
        for (CurrencyType currency : CurrencyType.values()) initial.put(currency, Math.max(0, plugin.getConfig().getLong("starting-balances." + currency.key(), 0)));
        store = new ReferralStore(database, initial);
        // Vérifier les deux récompenses au démarrage avant de pouvoir choisir un parrain.
        reward("filleul"); reward("parrain");
        BarColor.valueOf(config.getString("barre.couleur", "PURPLE"));
    }
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("parrain")).setExecutor(this); plugin.getCommand("parrain").setTabCompleter(this);
        for (Player player : Bukkit.getOnlinePlayers()) load(player);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }
    ReferralStore.Reward reward(String who) {
        String path = "recompenses." + who;
        Map<CurrencyType, Long> money = new EnumMap<>(CurrencyType.class);
        for (CurrencyType currency : CurrencyType.values()) {
            long amount = config.getLong(path + ".monnaies." + currency.key(), 0);
            if (amount < 0) throw new IllegalArgumentException("Récompense monétaire négative : " + who);
            money.put(currency, amount);
        }
        List<String> items = new ArrayList<>();
        for (Map<?, ?> row : config.getMapList(path + ".objets")) {
            int quantity = Integer.parseInt(String.valueOf(row.get("quantite"))); if (quantity < 1 || quantity > 4096) throw new IllegalArgumentException("Quantité d'objet invalide");
            ItemStack item;
            if ("lootbox".equals(row.get("type"))) {
                LootboxTier tier = LootboxTier.parse(String.valueOf(row.get("id"))); if (tier == null) throw new IllegalArgumentException("Lootbox inconnue");
                item = LootboxItem.create(tier);
            } else if ("minecraft".equals(row.get("type"))) {
                Material material = Material.matchMaterial(String.valueOf(row.get("materiau"))); if (material == null || material.isAir() || !material.isItem()) throw new IllegalArgumentException("Objet inconnu");
                item = new ItemStack(material);
            } else throw new IllegalArgumentException("Type de récompense inconnu");
            while (quantity > 0) { ItemStack part = item.clone(); int n = Math.min(quantity, item.getMaxStackSize()); part.setAmount(n); items.add(ClaimService.serialize(part)); quantity -= n; }
        }
        return new ReferralStore.Reward(money, items);
    }
    @EventHandler public void join(PlayerJoinEvent event) { load(event.getPlayer()); }
    private void load(Player player) {
        UUID owner = player.getUniqueId(); Object session = new Object(); sessions.put(owner, session);
        store.join(owner, player.getName(), player.getFirstPlayed(), clock.millis(), window).whenComplete((state, error) -> sync(() -> {
            if (!player.isOnline() || sessions.get(owner) != session) return;
            if (error != null) { failure(player, error); return; }
            states.put(owner, state); notifyPrivate(owner);
            if (!state.registered() && clock.millis() < state.deadline()) {
                message(player, "invitation", Map.of("defaut", config.getString("parrain-par-defaut", "RAPTORE44"), "temps", remaining(state.deadline() - clock.millis())));
                showBar(player, state);
            }
        }));
    }
    public static String remaining(long millis) { long seconds = Math.max(0, (millis + 999) / 1000); return (seconds / 60) + " min " + String.format(Locale.ROOT, "%02d s", seconds % 60); }
    private void showBar(Player player, ReferralStore.PlayerState state) {
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> Bukkit.createBossBar("", BarColor.valueOf(config.getString("barre.couleur", "PURPLE")), BarStyle.SOLID));
        bar.setTitle(ChatColor.translateAlternateColorCodes('&', config.getString("barre.titre", "&d/parrain <pseudo> • {temps}").replace("{temps}", remaining(state.deadline() - clock.millis()))));
        bar.setProgress(Math.max(0, Math.min(1, (state.deadline() - clock.millis()) / (double)window))); bar.addPlayer(player);
    }
    private void removeBar(UUID owner) { BossBar bar = bars.remove(owner); if (bar != null) bar.removeAll(); }
    private void tick() {
        for (UUID owner : new ArrayList<>(bars.keySet())) {
            Player player = Bukkit.getPlayer(owner); ReferralStore.PlayerState state = states.get(owner);
            if (player == null || state == null || state.registered() || clock.millis() >= state.deadline()) { removeBar(owner); continue; }
            showBar(player, state);
        }
    }
    public void notifyPrivate(UUID owner) {
        Player player = Bukkit.getPlayer(owner); if (player == null || !notifying.add(owner)) return;
        store.notices(owner).whenComplete((notices, error) -> sync(() -> {
            if (!player.isOnline() || error != null) { notifying.remove(owner); return; }
            List<java.util.concurrent.CompletableFuture<Void>> writes = new ArrayList<>();
            for (var notice : notices) { player.sendMessage(DailySettings.color(notice.message())); writes.add(store.readNotice(owner, notice.id())); }
            java.util.concurrent.CompletableFuture.allOf(writes.toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((v,e) -> sync(() -> {
                notifying.remove(owner); if (e != null) plugin.getLogger().log(Level.WARNING, "Notification privée à revérifier", e);
            }));
        }));
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Commande réservée aux joueurs."); return true; }
        if (!player.hasPermission("cliker.parrein")) { player.sendMessage(DailySettings.color("&cTu n'as pas la permission.")); return true; }
        UUID owner = player.getUniqueId(); ReferralStore.PlayerState state = states.get(owner);
        if (state == null) { message(player, "chargement", Map.of()); return true; }
        if (state.registered()) { message(player, "deja", Map.of()); return true; }
        if (clock.millis() >= state.deadline()) { removeBar(owner); message(player, "expire", Map.of()); return true; }
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_]{1,16}")) { message(player, "invitation", Map.of("defaut", config.getString("parrain-par-defaut", "RAPTORE44"), "temps", remaining(state.deadline() - clock.millis()))); return true; }
        if (!pending.add(owner)) { message(player, "traitement", Map.of()); return true; }
        String name = args[0];
        claims.resolve(name).whenComplete((sponsor, error) -> sync(() -> {
            if (error != null) { pending.remove(owner); failure(player, error); return; }
            if (sponsor == null) { pending.remove(owner); message(player, "inconnu", Map.of()); return; }
            var recruitReward = reward("filleul"); var sponsorReward = reward("parrain");
            String recruitNotice = DailySettings.replace(config.getString("messages.filleul", "&aTon parrain {parrain} a été enregistré !"), Map.of("parrain", name));
            String sponsorNotice = DailySettings.replace(config.getString("messages.parrain", "&a{filleul} t’a choisi comme parrain. Ta récompense est attribuée !"), Map.of("filleul", player.getName()));
            store.register(owner, player.getName(), sponsor, name, clock::millis, recruitReward, sponsorReward, recruitNotice, sponsorNotice).whenComplete((result, failure) -> sync(() -> {
                pending.remove(owner);
                if (failure != null) { failure(player, failure); return; }
                switch (result) {
                    case ACCEPTED -> {
                        if (player.isOnline()) states.put(owner, new ReferralStore.PlayerState(state.deadline(), true)); removeBar(owner);
                        notifyPrivate(owner); notifyPrivate(sponsor); claims.notifyAndDeliver(owner); claims.notifyAndDeliver(sponsor);
                    }
                    case ALREADY_REGISTERED -> { states.put(owner, new ReferralStore.PlayerState(state.deadline(), true)); removeBar(owner); message(player, "deja", Map.of()); }
                    case EXPIRED -> { removeBar(owner); message(player, "expire", Map.of()); }
                    case SELF -> message(player, "soi", Map.of());
                    case CYCLE -> message(player, "cycle", Map.of());
                    case UNKNOWN -> message(player, "inconnu", Map.of());
                }
            }));
        }));
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("cliker.parrein")) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT); Set<String> names = new TreeSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) if (!player.getName().equalsIgnoreCase(sender.getName())) names.add(player.getName());
        names.add(config.getString("parrain-par-defaut", "RAPTORE44")); return names.stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
    @EventHandler public void quit(PlayerQuitEvent event) { UUID owner = event.getPlayer().getUniqueId(); removeBar(owner); sessions.remove(owner); states.remove(owner); }
    private void message(Player player, String key, Map<String,String> values) { if (player.isOnline()) player.sendMessage(DailySettings.color(DailySettings.replace(config.getString("messages." + key, key), values))); }
    private void failure(Player player, Throwable error) { plugin.getLogger().log(Level.SEVERE, "Parrainage impossible pour " + player.getUniqueId(), error); message(player, "erreur", Map.of()); }
    private void sync(Runnable task) { if (!stopped && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task); }
    public void shutdown() { stopped = true; if (ticker != null) ticker.cancel(); for (UUID owner : new ArrayList<>(bars.keySet())) removeBar(owner); HandlerList.unregisterAll(this); states.clear(); sessions.clear(); }
}
