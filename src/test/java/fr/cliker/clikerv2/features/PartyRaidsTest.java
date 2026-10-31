package fr.cliker.clikerv2.features;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.buyzone.BuyZoneModule;
import fr.cliker.clikerv2.hunting.RewardLedger;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.*;

class PartyRaidsTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private RewardLedger ledger;
    private ClaimService claims;
    private BuyZoneModule zones;
    private PartyRaids raids;
    private Menus menus;
    private PlayerMock leader, member;
    private World lobby;
    private final List<FakeRaid> sessions = new ArrayList<>();
    private final Command raidCommand =
            new Command("raid") {
                @Override
                public boolean execute(CommandSender s, String l, String[] a) {
                    return false;
                }
            };
    private final Command bossCommand =
            new Command("boss") {
                @Override
                public boolean execute(CommandSender s, String l, String[] a) {
                    return false;
                }
            };

    private class FakeRaid implements PartyRaids.RaidSession {
        final World world;
        final boolean publicBoss;
        boolean closed, spawned;
        int spawns;

        FakeRaid(String name, boolean boss) {
            world = Bukkit.getWorld(name);
            publicBoss = boss;
        }

        public World world() {
            return world;
        }

        public void start() {}

        public void enter(Player p) {
            p.teleport(world.getSpawnLocation());
        }

        public void exit(Player p) {
            if (p.getWorld() == world) p.teleport(lobby.getSpawnLocation());
        }

        public void evacuate() {
            for (Player p : new ArrayList<>(world.getPlayers()))
                p.teleport(lobby.getSpawnLocation());
        }

        public void shutdown() {
            closed = true;
        }

        public void beginPublicBoss() {
            if (!spawned) {
                spawned = true;
                spawns++;
            }
        }
    }

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        lobby = server.addSimpleWorld("lobby");
        for (int i = 0; i < 4; i++) server.addSimpleWorld("clickhunter_raid_groupe_" + i);
        server.addSimpleWorld("clickhunter_boss_serveur");
        db = new DatabaseManager(plugin, "raid-party-" + UUID.randomUUID() + ".db");
        db.initialize();
        ledger = new RewardLedger(plugin, db);
        claims = ClaimTestFactory.create(plugin, db);
        server.getServicesManager()
                .register(ClaimService.class, claims, plugin, ServicePriority.Normal);
        zones = new BuyZoneModule(plugin, new EconomyService(db, Map.of()), db);
        menus = new Menus(plugin);
        leader = server.addPlayer("Leader");
        member = server.addPlayer("Member");
        leader.teleport(lobby.getSpawnLocation());
        member.teleport(lobby.getSpawnLocation());
        leader.addAttachment(plugin, "cliker.raid", true);
        member.addAttachment(plugin, "cliker.raid", true);
        leader.addAttachment(plugin, "cliker.services.admin", true);
        createModule();
        drain();
    }

    private void createModule() {
        raids =
                new PartyRaids(
                        plugin,
                        db,
                        null,
                        zones,
                        menus,
                        (name, difficulty, boss, finished) -> {
                            var session = new FakeRaid(name, boss);
                            sessions.add(session);
                            return session;
                        },
                        p -> {});
    }

    private void drain() {
        for (int i = 0; i < 4; i++) {
            db.submit(c -> null).join();
            server.getScheduler().performTicks(2);
        }
    }

    private void command(Player p, String... args) {
        raids.onCommand(p, raidCommand, "raid", args);
        drain();
    }

    private void clickLaunch() {
        var e =
                new InventoryClickEvent(
                        leader.getOpenInventory(),
                        InventoryType.SlotType.CONTAINER,
                        22,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(e);
        drain();
    }

    private FakeRaid publicBoss() {
        return sessions.stream().filter(s -> s.publicBoss && !s.closed).findFirst().orElseThrow();
    }

    private long counter(String id) {
        return db.submit(
                        c -> {
                            try (var s =
                                    c.prepareStatement(
                                            "SELECT value FROM server_counters WHERE id=?")) {
                                s.setString(1, id);
                                try (var r = s.executeQuery()) {
                                    return r.next() ? r.getLong(1) : 0;
                                }
                            } catch (java.sql.SQLException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
    }

    @AfterEach
    void cleanup() {
        try {
            raids.shutdown();
            zones.shutdown();
            claims.shutdown();
            ledger.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void acceptingUpdatesTheLeadersMenuAndBothPlayersEnterTheSameRaid() {
        command(leader);
        var original = leader.getOpenInventory().getTopInventory();
        command(leader, "inviter", member.getName());
        command(member, "accepter", leader.getName());
        assertSame(original, leader.getOpenInventory().getTopInventory());
        String name =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(original.getItem(1).getItemMeta().displayName());
        assertTrue(name.contains(member.getName()));
        command(member, "accepter", leader.getName());
        command(leader, "parchemin", "vache");
        clickLaunch();
        assertEquals("clickhunter_raid_groupe_0", leader.getWorld().getName());
        assertSame(leader.getWorld(), member.getWorld());
        assertTrue(leader.getInventory().getItemInMainHand().getType().isAir());
    }

    @Test
    void staleLobbyButtonsUseTheCurrentPartyRatherThanThePreviousSoloParty() {
        command(leader);
        var old = leader.getOpenInventory().getTopInventory();
        command(leader, "quitter");
        command(leader, "inviter", member.getName());
        command(member, "accepter", leader.getName());
        assertSame(old, leader.getOpenInventory().getTopInventory());
        command(leader, "parchemin", "vache");
        clickLaunch();
        assertEquals("clickhunter_raid_groupe_0", leader.getWorld().getName());
        assertSame(leader.getWorld(), member.getWorld());
    }

    @Test
    void onlyTheLeaderCanLaunchAndARevokedInvitationCannotRejoin() {
        command(leader, "inviter", member.getName());
        command(leader, "quitter");
        command(member, "accepter", leader.getName());
        command(member, "parchemin", "vache");
        assertSame(lobby, member.getWorld());
        command(
                leader,
                "inviter",
                member.getName()); // invitation throttle is deliberately retained
        // A new invite is valid once the leader's invitation timestamp has elapsed.
        // Use a fresh leader to exercise the separate group deterministically.
        var other = server.addPlayer("OtherLeader");
        other.addAttachment(plugin, "cliker.raid", true);
        other.teleport(lobby.getSpawnLocation());
        command(other, "inviter", member.getName());
        command(member, "accepter", other.getName());
        command(member, "lancer");
        assertSame(lobby, member.getWorld());
    }

    @Test
    void forceBossWorksFromConsoleAndCannotCreateDuplicatesOrConsumeTheNaturalCounter() {
        raids.onCommand(member, bossCommand, "boss", new String[] {"spawn"});
        drain();
        assertEquals(0, publicBoss().spawns);
        raids.onCommand(server.getConsoleSender(), bossCommand, "boss", new String[] {"spawn"});
        raids.onCommand(server.getConsoleSender(), bossCommand, "boss", new String[] {"spawn"});
        drain();
        assertEquals(1, publicBoss().spawns);
        assertEquals(1, counter("boss_active"));
        assertEquals(0, counter("boss_cycles"));
        raids.onCommand(server.getConsoleSender(), bossCommand, "boss", new String[] {"spawn"});
        drain();
        assertEquals(1, publicBoss().spawns);
        raids.shutdown();
        createModule();
        server.getScheduler().performTicks(20);
        drain();
        assertEquals(1, publicBoss().spawns);
        assertEquals(0, counter("boss_cycles"));
    }

    @Test
    void aMemberWhoseTeleportIsBlockedStaysInThePartyAndCanRejoin() {
        var blocked = new java.util.concurrent.atomic.AtomicBoolean(true);
        server.getPluginManager()
                .registerEvents(
                        new org.bukkit.event.Listener() {
                            @org.bukkit.event.EventHandler
                            public void teleport(org.bukkit.event.player.PlayerTeleportEvent e) {
                                if (e.getPlayer() == member
                                        && e.getTo()
                                                .getWorld()
                                                .getName()
                                                .startsWith("clickhunter_raid_groupe_")
                                        && blocked.get()) e.setCancelled(true);
                            }
                        },
                        plugin);
        command(leader, "inviter", member.getName());
        command(member, "accepter", leader.getName());
        command(leader, "parchemin", "vache");
        command(leader, "lancer");
        assertEquals("clickhunter_raid_groupe_0", leader.getWorld().getName());
        assertSame(lobby, member.getWorld());
        blocked.set(false);
        command(member, "rejoindre");
        assertSame(leader.getWorld(), member.getWorld());
    }
}
