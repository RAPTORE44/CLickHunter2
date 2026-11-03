package fr.cliker.clikerv2.achievements;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class AchievementStoreTest {
    DatabaseManager db;
    AchievementStore store;
    List<AchievementCatalog.Entry> catalog;
    UUID owner = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "achievements-" + UUID.randomUUID() + ".db");
        db.initialize();
        store = new AchievementStore(db);
        catalog = AchievementCatalog.create(new YamlConfiguration());
    }

    @AfterEach
    void close() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    void sql(String sql) {
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.executeUpdate(sql);
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
    }

    @Test
    void fiveHundredUniqueConfigurableAndReachableDefaults() {
        assertEquals(500, catalog.size());
        assertEquals(500, catalog.stream().map(AchievementCatalog.Entry::id).distinct().count());
        assertEquals(190, catalog.stream().filter(e -> e.category().equals("Créatures")).count());
        assertEquals(
                190, catalog.stream().filter(e -> e.category().equals("Boss de zone")).count());
        assertTrue(catalog.stream().allMatch(e -> e.target() > 0));
        var yaml =
                YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(
                                Objects.requireNonNull(
                                        getClass().getResourceAsStream("/progression/succes.yml")),
                                java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(500, yaml.getConfigurationSection("succes").getKeys(false).size());
        assertEquals(catalog, AchievementCatalog.create(yaml));
        yaml.set("succes.mob_vache_1.objectif", 30);
        assertThrows(IllegalArgumentException.class, () -> AchievementCatalog.create(yaml));
    }

    @Test
    void historyIsCreditedExactlyOnceAcrossConcurrentRefreshesAndReload() {
        sql(
                "INSERT INTO bestiary_stats VALUES('"
                        + owner
                        + "','vache',0,100,0,0,0,0,0),('"
                        + owner
                        + "','vache',1,10,0,0,0,0,0)");
        var a = store.refresh(owner, Map.of("minutes", 300L), catalog);
        var b = store.refresh(owner, Map.of("minutes", 300L), catalog);
        var first = a.join();
        assertTrue(first.unlocked().containsKey("mob_vache_3"));
        assertTrue(first.unlocked().containsKey("boss_vache_3"));
        assertEquals(110, first.values().get("kills"));
        assertTrue(b.join().added().isEmpty());
        var reloaded =
                new AchievementStore(db).refresh(owner, Map.of("minutes", 1L), catalog).join();
        assertTrue(reloaded.added().isEmpty());
        assertEquals(first.unlocked(), reloaded.unlocked());
        assertEquals(300, reloaded.values().get("minutes"));
    }

    @Test
    void peaksAndUnlockDatesSurvivePrestigeResetAndOtherOwnersAreIsolated() {
        var before = store.refresh(owner, Map.of("level", 500L, "zones", 19L), catalog).join();
        var after = store.refresh(owner, Map.of("level", 0L, "zones", 1L), catalog).join();
        assertEquals(before.unlocked(), after.unlocked());
        assertEquals(500, after.values().get("level"));
        assertTrue(store.refresh(UUID.randomUUID(), Map.of(), catalog).join().unlocked().isEmpty());
    }

    @Test
    void mailCountSurvivesRecipientDeletingLettersAndDuplicateSendRollsBack() {
        var mail = new fr.cliker.clikerv2.community.CommunityStore(db);
        UUID recipient = UUID.randomUUID();
        mail.send("letter-1", recipient, owner, "Sender", "Bonjour", null, null, 20).join();
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () ->
                        mail.send("letter-1", recipient, owner, "Sender", "Bonjour", null, null, 20)
                                .join());
        sql("DELETE FROM community_mail WHERE id='letter-1'");
        var result = store.refresh(owner, Map.of(), catalog).join();
        assertEquals(1, result.values().get("mail"));
        assertTrue(result.unlocked().containsKey("mail_1"));
        mail.send("self", owner, owner, "Sender", "Bonjour", null, null, 20).join();
        assertEquals(1, store.refresh(owner, Map.of(), catalog).join().values().get("mail"));
    }

    @Test
    void confirmedActivitiesUseStoredUnitsAndVictoryCounters() {
        sql(
                "INSERT INTO bestiary_money VALUES('"
                        + owner
                        + "','vache','ecus',10000),('"
                        + owner
                        + "','vache','gemmes-sombres',1000)");
        sql(
                "INSERT INTO boss_records VALUES('"
                        + owner
                        + "','raid:vache',10,10,5),('"
                        + owner
                        + "','serveur:vache',10,10,3)");
        sql("INSERT INTO registered_talismans VALUES('" + owner + "','treasure')");
        sql("INSERT INTO outposts(owner,name,tier) VALUES('" + owner + "','Test',5)");
        var result = store.refresh(owner, Map.of(), catalog).join();
        assertEquals(100, result.values().get("gems"));
        assertEquals(5, result.values().get("raids"));
        assertEquals(3, result.values().get("serverboss"));
        assertTrue(
                result.unlocked()
                        .keySet()
                        .containsAll(
                                Set.of(
                                        "gems_2",
                                        "ecus_2",
                                        "talismans_1",
                                        "outpost_6",
                                        "raids_2",
                                        "serverboss_2")));
    }
}
