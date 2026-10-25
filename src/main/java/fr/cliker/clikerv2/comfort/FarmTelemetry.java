package fr.cliker.clikerv2.comfort;

import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.hunting.Gains;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Mesures de gains confirmés : aucune estimation ni attribution de récompense. */
public final class FarmTelemetry implements Listener {
    private record Earning(long at, MobTier tier, Gains gains) {}

    private static final class Session {
        long clicks, kills, ecus, gems, cendres, xp;
        final ArrayDeque<Earning> earnings = new ArrayDeque<>();
    }

    private final JavaPlugin plugin;
    private final java.util.function.LongSupplier nanos;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> fights = new HashMap<>();

    public FarmTelemetry(JavaPlugin plugin) {
        this(plugin, System::nanoTime);
    }

    FarmTelemetry(JavaPlugin plugin, java.util.function.LongSupplier nanos) {
        this.plugin = plugin;
        this.nanos = nanos;
    }

    public void start() {
        Bukkit.getServicesManager()
                .register(
                        FarmTelemetry.class,
                        this,
                        plugin,
                        org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(plugin, "farm_" + name);
    }

    private static long add(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    public void hit(Player player, UUID victim) {
        Session session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new Session());
        session.clicks = add(session.clicks, 1);
        var data = player.getPersistentDataContainer();
        data.set(
                key("clicks"),
                PersistentDataType.LONG,
                add(Math.max(0, data.getOrDefault(key("clicks"), PersistentDataType.LONG, 0L)), 1));
        // Ne retenir que les combats en cours ; borne défensive contre une accumulation d'entités.
        if (fights.size() >= 4096) fights.clear();
        fights.putIfAbsent(victim, nanos.getAsLong());
    }

    public long finishFight(UUID victim) {
        Long started = fights.remove(victim);
        return started == null ? 0 : Math.max(1, (nanos.getAsLong() - started) / 1_000_000);
    }

    public void confirmedKill(
            Player player, MobTier tier, boolean boss, Gains gains, long duration) {
        Session s = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new Session());
        s.kills = add(s.kills, 1);
        s.ecus = add(s.ecus, gains.amount(CurrencyType.ECUS));
        s.gems = add(s.gems, gains.amount(CurrencyType.GEMMES_SOMBRES));
        s.cendres = add(s.cendres, gains.amount(CurrencyType.CENDRES));
        s.xp = add(s.xp, gains.xp());
        long now = nanos.getAsLong();
        prune(s, now);
        if (s.earnings.size() >= 12000) s.earnings.removeFirst();
        s.earnings.addLast(new Earning(now, tier, gains));
        if (boss && duration > 0) {
            var recordKey = key("record_" + tier.id());
            long previous =
                    player.getPersistentDataContainer()
                            .getOrDefault(recordKey, PersistentDataType.LONG, Long.MAX_VALUE);
            String time = String.format(Locale.FRANCE, "%.2f s", duration / 1000.0);
            player.sendMessage(
                    Component.text(
                            "Boss "
                                    + tier.label()
                                    + " vaincu en "
                                    + time
                                    + (duration < previous ? " — Nouveau record personnel !" : "."),
                            NamedTextColor.GOLD));
            if (duration < previous)
                player.getPersistentDataContainer()
                        .set(recordKey, PersistentDataType.LONG, duration);
        }
        if (boss) {
            var first = key("boss_first_" + tier.id());
            if (!player.getPersistentDataContainer().has(first, PersistentDataType.BYTE)) {
                player.getPersistentDataContainer().set(first, PersistentDataType.BYTE, (byte) 1);
                player.sendMessage(
                        Component.text(
                                "Première victoire contre le boss "
                                        + tier.label()
                                        + " ! Bravo, poursuis ta progression !",
                                NamedTextColor.GREEN));
                if (!PlayerComfort.enabled(player, "soft_visuals", false))
                    player.spawnParticle(
                            Particle.FIREWORK,
                            player.getLocation().add(0, 1.5, 0),
                            30,
                            0.5,
                            0.5,
                            0.5,
                            0.05);
            }
        }
    }

    private static void prune(Session s, long now) {
        while (!s.earnings.isEmpty() && now - s.earnings.getFirst().at() >= 60_000_000_000L)
            s.earnings.removeFirst();
    }

    public List<Component> lore(Player player) {
        var s = sessions.get(player.getUniqueId());
        var lines = new ArrayList<Component>();
        long clicks =
                player.getPersistentDataContainer()
                        .getOrDefault(key("clicks"), PersistentDataType.LONG, 0L);
        lines.add(
                Component.text(
                        "Coups validés au total : " + Numbers.compact(clicks),
                        NamedTextColor.AQUA));
        if (s == null) return lines;
        prune(s, nanos.getAsLong());
        Map<MobTier, long[]> perZone = new EnumMap<>(MobTier.class);
        for (var earning : s.earnings) {
            var totals = perZone.computeIfAbsent(earning.tier(), ignored -> new long[4]);
            totals[0] = add(totals[0], earning.gains().amount(CurrencyType.ECUS));
            totals[1] = add(totals[1], earning.gains().amount(CurrencyType.GEMMES_SOMBRES));
            totals[2] = add(totals[2], earning.gains().amount(CurrencyType.CENDRES));
            totals[3] = add(totals[3], earning.gains().xp());
        }
        lines.add(Component.text("Gains confirmés des 60 dernières secondes", NamedTextColor.GOLD));
        if (perZone.isEmpty())
            lines.add(Component.text("Aucune élimination récente.", NamedTextColor.GRAY));
        perZone.forEach(
                (tier, totals) ->
                        lines.add(
                                Component.text(
                                        tier.label()
                                                + " : "
                                                + Numbers.compact(totals[0])
                                                + " Écus · "
                                                + CurrencyType.GEMMES_SOMBRES.format(totals[1])
                                                + " Gemmes · "
                                                + Numbers.compact(totals[2])
                                                + " Cendres · "
                                                + Numbers.compact(totals[3])
                                                + " XP",
                                        NamedTextColor.GRAY)));
        return lines;
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        var player = event.getPlayer();
        String previous =
                player.getPersistentDataContainer()
                        .get(key("last_session"), PersistentDataType.STRING);
        if (previous != null)
            player.sendMessage(
                    Component.text("Ta session précédente : " + previous, NamedTextColor.GRAY));
    }

    private void save(Player player) {
        var s = sessions.remove(player.getUniqueId());
        if (s == null) return;
        player.getPersistentDataContainer()
                .set(
                        key("last_session"),
                        PersistentDataType.STRING,
                        Numbers.compact(s.clicks)
                                + (s.clicks > 1 ? " coups · " : " coup · ")
                                + Numbers.compact(s.kills)
                                + (s.kills > 1 ? " éliminations · " : " élimination · ")
                                + Numbers.compact(s.ecus)
                                + " Écus · "
                                + CurrencyType.GEMMES_SOMBRES.format(s.gems)
                                + " Gemmes · "
                                + Numbers.compact(s.cendres)
                                + " Cendres · "
                                + Numbers.compact(s.xp)
                                + " XP");
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        save(event.getPlayer());
    }

    public void shutdown() {
        for (var player : Bukkit.getOnlinePlayers()) save(player);
        sessions.clear();
        fights.clear();
        Bukkit.getServicesManager().unregister(FarmTelemetry.class, this);
        HandlerList.unregisterAll(this);
    }
}
