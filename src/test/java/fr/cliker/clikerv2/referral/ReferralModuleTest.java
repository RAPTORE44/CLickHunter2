package fr.cliker.clikerv2.referral;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.lootbox.LootboxItem;
import fr.cliker.clikerv2.rewards.*;
import org.bukkit.*;
import org.bukkit.boss.BossBar;
import org.bukkit.command.*;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class ReferralModuleTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager database;
    private ClaimService claims;
    private ReferralModule module;
    private PlayerMock recruit, sponsor, observer;
    private MutableClock clock;
    private final Command command = new Command("parrain") { @Override public boolean execute(CommandSender s,String l,String[]a){return false;} };
    @BeforeEach void setup() throws Exception {
        server = MockBukkit.mock(new ServerMock() {
            // Le cache de profils est simulé pour les tests hors ligne.
            @Override public OfflinePlayer getOfflinePlayerIfCached(String name) {
                for (OfflinePlayer player : getOfflinePlayers()) if (name.equalsIgnoreCase(player.getName())) return player;
                return null;
            }
        });
        plugin = MockBukkit.createMockPlugin(); ConfigurationSerialization.registerClass(ItemStackMock.class); LootboxItem.init(plugin);
        for (String resource : List.of("claim.yml", "parrainage.yml")) {
            var path = plugin.getDataFolder().toPath().resolve(resource); Files.createDirectories(path.getParent());
            try (var input = getClass().getResourceAsStream("/" + resource)) { Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING); }
        }
        recruit = server.addPlayer("Nouveau"); sponsor = server.addPlayer("RAPTORE44"); observer = server.addPlayer("Temoin");
        recruit.addAttachment(plugin, "cliker.parrein", true);
        database = new DatabaseManager(plugin, "referral-ui-" + UUID.randomUUID() + ".db"); database.initialize();
        claims = ClaimTestFactory.create(plugin, database); server.getPluginManager().registerEvents(claims, plugin);
        clock = new MutableClock(System.currentTimeMillis()); module = new ReferralModule(plugin, database, claims, clock);
        server.getPluginManager().registerEvents(module, plugin);
        load(sponsor); load(recruit);
    }
    @AfterEach void cleanup() { try { module.shutdown(); claims.shutdown(); database.close(); } finally { MockBukkit.unmock(); } }
    private void load(PlayerMock player) throws Exception {
        var method = ReferralModule.class.getDeclaredMethod("load", org.bukkit.entity.Player.class); method.setAccessible(true); method.invoke(module, player);
        database.submit(c -> null).join(); server.getScheduler().performOneTick();
    }
    @SuppressWarnings("unchecked") private Map<UUID, BossBar> bars() throws Exception {
        var field = ReferralModule.class.getDeclaredField("bars"); field.setAccessible(true); return (Map<UUID, BossBar>) field.get(module);
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 4_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10); server.getScheduler().performOneTick(); }
        assertTrue(condition.getAsBoolean());
    }
    private List<String> messages(PlayerMock player) { List<String> list = new ArrayList<>(); for (String s; (s = player.nextMessage()) != null;) list.add(s); return list; }
    @Test void welcomeHasFallbackAndBarDisappearsAfterSuccessfulChoice() throws Exception {
        assertTrue(messages(recruit).stream().anyMatch(m -> m.contains("/parrain RAPTORE44")));
        BossBar bar = bars().get(recruit.getUniqueId()); assertNotNull(bar);
        assertTrue(bar.getTitle().contains("/parrain")); assertTrue(bar.getTitle().contains("30 min"));
        messages(sponsor); messages(observer);
        module.onCommand(recruit, command, "parrain", new String[]{"RAPTORE44"});
        await(() -> new ReferralStore(database, Map.of()).notices(sponsor.getUniqueId()).join().size() == 1);
        server.getScheduler().performTicks(3); database.submit(c -> null).join(); server.getScheduler().performTicks(3);
        assertFalse(bars().containsKey(recruit.getUniqueId()));
        var economy = new EconomyService(database, Map.of());
        assertEquals(1000L, economy.getBalances(recruit.getUniqueId()).join().ecus());
        assertEquals(2500L, economy.getBalances(sponsor.getUniqueId()).join().ecus());
        assertTrue(messages(sponsor).stream().anyMatch(m -> m.contains("Nouveau") && m.contains("parrain")));
        assertTrue(messages(observer).isEmpty());
    }
    @Test void expiryRemovesBarAndReconnectDoesNotResetTimer() throws Exception {
        clock.time += 31 * 60000;
        var tick = ReferralModule.class.getDeclaredMethod("tick"); tick.setAccessible(true); tick.invoke(module);
        assertFalse(bars().containsKey(recruit.getUniqueId()));
        module.onCommand(recruit, command, "parrain", new String[]{"RAPTORE44"});
        assertTrue(messages(recruit).stream().anyMatch(m -> m.contains("terminé")));
        load(recruit); assertFalse(bars().containsKey(recruit.getUniqueId()));
    }
    @Test void offlineSponsorIsNotifiedOnlyOnNextConnection() throws Exception {
        messages(sponsor); sponsor.disconnect();
        module.onCommand(recruit, command, "parrain", new String[]{"RAPTORE44"});
        var repository = new ReferralStore(database, Map.of());
        await(() -> !repository.notices(sponsor.getUniqueId()).join().isEmpty());
        assertEquals(2500L, new EconomyService(database, Map.of()).getBalances(sponsor.getUniqueId()).join().ecus());
        assertFalse(messages(observer).stream().anyMatch(m -> m.contains("parrain")));
        sponsor.reconnect(); load(sponsor);
        database.submit(c -> null).join(); server.getScheduler().performTicks(3);
        assertTrue(messages(sponsor).stream().anyMatch(m -> m.contains("Nouveau") && m.contains("parrain")));
    }
    @Test void oldCommandTextsAreCorrectedWithoutChangingRewards() throws Exception {
        var file = plugin.getDataFolder().toPath().resolve("parrainage.yml").toFile();
        var old = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        old.set("barre.titre", "&dFais /parrein <pseudo> {temps}");
        old.set("messages.invitation", "Utilise /parrein RAPTORE44");
        old.set("recompenses.filleul.monnaies.ecus", 1234); old.save(file);
        var corrected = new ReferralModule(plugin, database, claims, clock);
        try {
            var saved = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            assertEquals("&dFais /parrain <pseudo> {temps}", saved.getString("barre.titre"));
            assertEquals("Utilise /parrain RAPTORE44", saved.getString("messages.invitation"));
            assertEquals(1234L, saved.getLong("recompenses.filleul.monnaies.ecus"));
        } finally { corrected.shutdown(); }
    }
    private static final class MutableClock extends Clock {
        long time; MutableClock(long time) { this.time = time; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(time); }
    }
}
