package fr.cliker.clikerv2.hunting;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.mobs.CustomMobManager;
import fr.cliker.clikerv2.npc.CitizensNpc;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class HuntingModule implements CommandExecutor, TabCompleter, Listener {
    private final JavaPlugin plugin;
    private final HuntingStore store;
    private final RewardLedger ledger;
    private final CustomMobManager mobs;
    private final ContractStore contracts;
    private final List<ContractStore.Contract> definitions = new ArrayList<>();
    private final File file;
    private final YamlConfiguration config;
    private final ZoneId zone;
    private final CitizensNpc npc;
    private final Set<UUID> busy = new HashSet<>();

    private record OpenRequest(long id, org.bukkit.inventory.Inventory inventory, UUID world) {}

    private final Map<UUID, OpenRequest> requests = new HashMap<>();
    private long requestId;
    private boolean stopped;

    public HuntingModule(
            JavaPlugin plugin, HuntingStore store, RewardLedger ledger, CustomMobManager mobs) {
        this.plugin = plugin;
        this.store = store;
        this.ledger = ledger;
        this.mobs = mobs;
        contracts = new ContractStore(ledger);
        file = new File(plugin.getDataFolder(), "chasse.yml");
        if (!file.exists()) plugin.saveResource("chasse.yml", false);
        config = fr.cliker.clikerv2.config.YamlFiles.load(file);
        zone = ZoneId.of(config.getString("fuseau", "Europe/Paris"));
        boolean added = false;
        for (MobTier mob : MobTier.values())
            for (boolean boss : new boolean[] {false, true}) {
                String id = mob.id() + (boss ? "-boss" : "-mobs"), p = "contrats." + id + ".";
                int index = mob.ordinal() + 1;
                int multiplier = boss ? 10 : 1;
                if (!config.contains(p + "objectif")) {
                    config.set(p + "objectif", boss ? 1 : 50);
                    config.set(p + "ecus", 1000L * index * multiplier);
                    config.set(p + "gemmes-sombres", 10L * index * multiplier);
                    config.set(p + "cendres", 5L * index * multiplier);
                    config.set(p + "xp", 100L * index * multiplier);
                    added = true;
                }
                definitions.add(
                        new ContractStore.Contract(
                                id,
                                mob.id(),
                                boss,
                                Math.max(1, config.getLong(p + "objectif")),
                                Gains.of(
                                        config.getLong(p + "ecus"),
                                        config.getLong(p + "gemmes-sombres"),
                                        config.getLong(p + "cendres"),
                                        config.getLong(p + "xp"))));
            }
        if (added) save();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        npc =
                new CitizensNpc(
                        plugin,
                        config,
                        this::save,
                        this::openContracts,
                        "clikerv2-contracts-npc",
                        "ClickHunterContrats");
    }

    public void start() {
        for (String command : List.of("bestiaire", "contrats")) {
            Objects.requireNonNull(plugin.getCommand(command)).setExecutor(this);
            plugin.getCommand(command).setTabCompleter(this);
        }
        npc.start();
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String cycle() {
        return LocalDate.now(zone).toString();
    }

    private void message(Player player, String key) {
        player.sendMessage(
                fr.cliker.clikerv2.daily.DailySettings.color(
                        config.getString("messages." + key, key)));
    }

    private <T> void result(Player player, CompletableFuture<T> future, Consumer<T> action) {
        future.whenComplete(
                (value, error) -> {
                    if (stopped || !plugin.isEnabled()) return;
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {
                                        if (error != null) {
                                            busy.remove(player.getUniqueId());
                                            plugin.getLogger()
                                                    .log(
                                                            java.util.logging.Level.SEVERE,
                                                            "Chasse : erreur SQLite",
                                                            error);
                                            if (player.isOnline()) message(player, "erreur");
                                        } else if (player.isOnline()) action.accept(value);
                                    });
                });
    }

    private long request(Player p) {
        long id = ++requestId;
        requests.put(
                p.getUniqueId(),
                new OpenRequest(id, p.getOpenInventory().getTopInventory(), p.getWorld().getUID()));
        return id;
    }

    private boolean current(Player p, long id) {
        OpenRequest request = requests.get(p.getUniqueId());
        return request != null
                && request.id() == id
                && request.inventory() == p.getOpenInventory().getTopInventory()
                && request.world().equals(p.getWorld().getUID());
    }

    public void openBestiary(Player player) {
        long request = request(player);
        result(
                player,
                store.stats(player.getUniqueId()),
                stats -> {
                    if (!current(player, request)) return;
                    var view = HuntUI.view(player, "&2Bestiaire &8• &7Maîtrises", 54, slot -> {});
                    int index = 0;
                    for (MobTier mob : MobTier.values()) {
                        int slot = 10 + (index / 7) * 9 + index % 7;
                        index++;
                        var stat = stats.get(mob.id());
                        if (stat == null || stat.kills() == 0) {
                            view.inventory.setItem(
                                    slot,
                                    HuntUI.item(
                                            Material.GRAY_DYE,
                                            "&8???",
                                            List.of(
                                                    "&7Créature inconnue",
                                                    "&8Élimine-la une première fois pour la"
                                                            + " découvrir.")));
                            continue;
                        }
                        List<String> lore = new ArrayList<>();
                        lore.add("&8────────────────────");
                        lore.add("&7Éliminations : &f" + stat.kills());
                        lore.add("&7Boss associés vaincus : &c" + stat.bosses());
                        long bossMastery =
                                config.getLongList("maitrise.paliers-boss").stream()
                                        .filter(n -> stat.bosses() >= n)
                                        .count();
                        lore.add("&cMaîtrise du boss : &f" + bossMastery);
                        int mastery = 0;
                        long next = 0;
                        for (long threshold : config.getLongList("maitrise.paliers")) {
                            if (stat.kills() >= threshold) mastery++;
                            else {
                                next = threshold;
                                break;
                            }
                        }
                        lore.add(
                                "&6Maîtrise : &e"
                                        + mastery
                                        + (next > 0
                                                ? " &7(« " + next + " éliminations »)"
                                                : " &aMAX"));
                        lore.add("");
                        lore.add("&aGain par mob avec ton équipement :");
                        var unit = mobs.normalRewards(player, mob);
                        lore.addAll(HuntUI.gains(unit));
                        int tierIndex = mob.ordinal() + 1, divisor = tierIndex <= 7 ? 2 : 1;
                        lore.add("&cBonus par boss associé :");
                        lore.add(
                                "&6+"
                                        + (1000L * tierIndex / divisor)
                                        + " à "
                                        + (5000L * tierIndex / divisor)
                                        + " Écus");
                        lore.add(
                                "&5+"
                                        + (10L * tierIndex / divisor)
                                        + " à "
                                        + (20L * tierIndex / divisor)
                                        + " Gemmes Sombres &7+ essences");
                        lore.add("");
                        lore.add("&6Total gagné sur ce mob et son boss :");
                        lore.addAll(HuntUI.gains(stat.gains()));
                        Material icon =
                                Material.matchMaterial(mob.entityType().name() + "_SPAWN_EGG");
                        view.inventory.setItem(
                                slot,
                                HuntUI.item(
                                        icon == null ? Material.BOOK : icon,
                                        "&a" + mob.label(),
                                        lore));
                    }
                    view.inventory.setItem(
                            49,
                            HuntUI.item(
                                    Material.BOOK,
                                    "&eInformations",
                                    List.of(
                                            "&7Seules tes éliminations révèlent les créatures.",
                                            "&7Les maîtrises suivent tes éliminations normales.",
                                            "&7Statistiques suivies depuis cette mise à jour.",
                                            "&7Les gains affichés sont réellement crédités.")));
                    player.openInventory(view.inventory);
                });
    }

    public void openContracts(Player player) {
        if (!player.hasPermission("cliker.contrats")) {
            message(player, "interdit");
            return;
        }
        long request = request(player);
        String cycle = cycle();
        var stats = store.stats(player.getUniqueId());
        var progress = contracts.progress(player.getUniqueId(), definitions, cycle);
        result(
                player,
                CompletableFuture.allOf(stats, progress),
                ignored -> {
                    if (!current(player, request)) return;
                    var known = stats.join();
                    var view =
                            HuntUI.view(
                                    player,
                                    "&6Contrats &8• &7" + cycle,
                                    54,
                                    slot -> {
                                        if (!player.hasPermission("cliker.contrats")
                                                || slot < 0
                                                || slot >= definitions.size()
                                                || !cycle.equals(cycle())
                                                || !busy.add(player.getUniqueId())) return;
                                        var contract = definitions.get(slot);
                                        var clickedInventory =
                                                player.getOpenInventory().getTopInventory();
                                        result(
                                                player,
                                                contracts.interact(
                                                        player.getUniqueId(),
                                                        player.getName(),
                                                        contract,
                                                        cycle),
                                                status -> {
                                                    busy.remove(player.getUniqueId());
                                                    message(player, status);
                                                    ledger.flushXP(player.getUniqueId());
                                                    if (player.getOpenInventory().getTopInventory()
                                                            == clickedInventory)
                                                        openContracts(player);
                                                });
                                    });
                    for (int i = 0; i < definitions.size(); i++) {
                        var contract = definitions.get(i);
                        var mob = MobTier.parse(contract.mob());
                        var stat = known.get(contract.mob());
                        if (stat == null || stat.kills() == 0) {
                            view.inventory.setItem(
                                    i,
                                    HuntUI.item(
                                            Material.GRAY_DYE,
                                            "&8Contrat inconnu",
                                            List.of("&7Découvre d'abord la créature.")));
                            continue;
                        }
                        var state = progress.join().get(contract.id());
                        List<String> lore = new ArrayList<>();
                        lore.add(
                                "&7Objectif : &f"
                                        + contract.target()
                                        + " "
                                        + (contract.boss() ? "boss" : "mobs"));
                        lore.add(
                                "&7Progression : &f"
                                        + Math.min(state.done(), contract.target())
                                        + "/"
                                        + contract.target());
                        lore.add("");
                        lore.addAll(HuntUI.gains(contract.reward()));
                        lore.add("");
                        lore.add(
                                state.claimed()
                                        ? "&aRécompense récupérée"
                                        : !state.accepted()
                                                ? "&eClique pour accepter"
                                                : state.done() >= contract.target()
                                                        ? "&aClique pour réclamer"
                                                        : "&7Contrat en cours");
                        view.inventory.setItem(
                                i,
                                HuntUI.item(
                                        state.claimed()
                                                ? Material.LIME_DYE
                                                : contract.boss()
                                                        ? Material.NETHER_STAR
                                                        : Material.PAPER,
                                        "&e" + mob.label() + (contract.boss() ? " &c• Boss" : ""),
                                        lore));
                    }
                    view.inventory.setItem(
                            49,
                            HuntUI.item(
                                    Material.CLOCK,
                                    "&bContrats quotidiens",
                                    List.of(
                                            "&7Réinitialisation à minuit : " + zone,
                                            "&7Seules les éliminations après acceptation comptent.",
                                            "&7Réclame tes récompenses avant minuit.")));
                    player.openInventory(view.inventory);
                });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) {
            if (command.getName().equals("bestiaire")) {
                if (player.hasPermission("cliker.bestiaire")) openBestiary(player);
                else message(player, "interdit");
            } else openContracts(player);
            return true;
        }
        if (!command.getName().equals("contrats")
                || !player.hasPermission("cliker.contrats.admin")) {
            message(player, "interdit");
            return true;
        }
        try {
            if (args.length == 2 && args[0].equalsIgnoreCase("pnj")) {
                if (!npc.available()) {
                    message(player, "citizens");
                    return true;
                }
                if (args[1].equalsIgnoreCase("spawn"))
                    message(player, npc.spawn(player.getLocation()) ? "cree" : "existant");
                else if (args[1].equalsIgnoreCase("remove"))
                    message(player, npc.remove() ? "supprime" : "absent");
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .log(java.util.logging.Level.SEVERE, "PNJ des contrats indisponible", e);
            message(player, "erreur");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        return sender.hasPermission("cliker.contrats.admin") && command.getName().equals("contrats")
                ? (args.length == 1
                        ? List.of("pnj")
                        : args.length == 2 ? List.of("spawn", "remove") : List.of())
                : List.of();
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        requests.remove(e.getPlayer().getUniqueId());
        busy.remove(e.getPlayer().getUniqueId());
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        stopped = true;
        npc.shutdown();
        busy.clear();
        requests.clear();
    }
}
