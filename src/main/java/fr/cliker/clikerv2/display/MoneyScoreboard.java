package fr.cliker.clikerv2.display;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.mobs.MobKillStore;
import fr.cliker.clikerv2.prestige.PrestigeManager;
import fr.cliker.clikerv2.prestige.PrestigeType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Team;
import io.papermc.paper.scoreboard.numbers.NumberFormat;

public final class MoneyScoreboard implements Listener {
    private static final String OBJECTIVE_ID = "cliker_money";
    private static final CurrencyType[] MONEY_LINES = { CurrencyType.ECUS, CurrencyType.GEMMES_SOMBRES, CurrencyType.CENDRES };

    private static final int LINE_LEVEL = 0;
    private static final int LINE_GAP_1 = 1;
    private static final int LINE_KILLS = 2;
    private static final int LINE_GAP_2 = 3;
    private static final int LINE_ECUS = 4;
    private static final int LINE_GEMMES = 5;
    private static final int LINE_CENDRES = 6;
    private static final int LINE_GAP_3 = 7;
    private static final int LINE_FOOTER = 8;
    private static final int LINE_COUNT = 9;

    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final MobKillStore killStore;
    private final PrestigeManager prestige;

    public MoneyScoreboard(JavaPlugin plugin, EconomyService economy, MobKillStore killStore, PrestigeManager prestige) {
        this.plugin = plugin;
        this.economy = economy;
        this.killStore = killStore;
        this.prestige = prestige;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, 0L, 40L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        setup(event.getPlayer());
    }

    private void refreshAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) refresh(player);
    }

    private void setup(Player player) {
        var scoreboard = plugin.getServer().getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_ID, Criteria.DUMMY,
                Component.text("✦ CLIKER ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < LINE_COUNT; i++) {
            Team team = scoreboard.registerNewTeam("cliker_line_" + i);
            team.addEntry(entry(i));
            Score score = objective.getScore(entry(i));
            score.setScore(LINE_COUNT - i);
            score.numberFormat(NumberFormat.blank());
        }
        player.setScoreboard(scoreboard);
        refresh(player);
    }

    private void refresh(Player player) {
        boolean needsSetup = player.getScoreboard().getObjective(OBJECTIVE_ID) == null
                || player.getScoreboard().getTeam("cliker_line_" + (LINE_COUNT - 1)) == null;
        if (needsSetup) setup(player);
        var scoreboard = player.getScoreboard();
        economy.getBalances(player.getUniqueId()).thenCombine(killStore.getTotal(player.getUniqueId()), (balances, kills) ->
                new Object[]{balances, kills}
        ).whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null || !player.isOnline()) return;
            EconomyService.Balances balances = (EconomyService.Balances) result[0];
            long kills = (long) result[1];

            int prestigeLevel = prestige.getLevel(player.getUniqueId());
            Component levelValue = prestigeLevel > 0
                    ? Component.text("[", NamedTextColor.DARK_GRAY)
                            .append(Component.text(PrestigeType.toRoman(prestigeLevel), NamedTextColor.LIGHT_PURPLE))
                            .append(Component.text("-", NamedTextColor.DARK_GRAY))
                            .append(Component.text(player.getLevel(), NamedTextColor.GREEN))
                            .append(Component.text("]", NamedTextColor.DARK_GRAY))
                    : Component.text(player.getLevel(), NamedTextColor.GREEN);
            setLine(scoreboard, LINE_LEVEL, Component.text("★ ", NamedTextColor.GREEN)
                    .append(Component.text("Level: ", NamedTextColor.WHITE))
                    .append(levelValue));
            setLine(scoreboard, LINE_GAP_1, Component.empty());
            setLine(scoreboard, LINE_KILLS, Component.text("☠ ", NamedTextColor.RED)
                    .append(Component.text("Mobs tués : ", NamedTextColor.WHITE))
                    .append(Component.text(String.format("%,d", kills).replace(',', ' '), NamedTextColor.RED)));
            setLine(scoreboard, LINE_GAP_2, Component.empty());

            for (CurrencyType currency : MONEY_LINES) {
                int lineIndex = switch (currency) {
                    case ECUS -> LINE_ECUS;
                    case GEMMES_SOMBRES -> LINE_GEMMES;
                    case CENDRES -> LINE_CENDRES;
                };
                long value = switch (currency) {
                    case ECUS -> balances.ecus();
                    case GEMMES_SOMBRES -> balances.gemmesSombres();
                    case CENDRES -> balances.cendres();
                };
                setLine(scoreboard, lineIndex, Component.text(currency.symbol() + " ", accent(currency))
                        .append(Component.text(currency.displayName() + " ", NamedTextColor.WHITE))
                        .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(currency.format(value), accent(currency))));
            }

            setLine(scoreboard, LINE_GAP_3, Component.empty());
            setLine(scoreboard, LINE_FOOTER, Component.text("play.northblue.fr", NamedTextColor.AQUA));
        }));
    }

    private void setLine(org.bukkit.scoreboard.Scoreboard scoreboard, int index, Component content) {
        Team team = scoreboard.getTeam("cliker_line_" + index);
        if (team == null) return;
        team.prefix(content);
        team.suffix(Component.empty());
    }

    private static NamedTextColor accent(CurrencyType currency) {
        return switch (currency) {
            case ECUS -> NamedTextColor.GOLD;
            case GEMMES_SOMBRES -> NamedTextColor.DARK_PURPLE;
            case CENDRES -> NamedTextColor.GRAY;
        };
    }

    private static String entry(int index) { return "§" + Integer.toHexString(index) + "§r"; }
}
