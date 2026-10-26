package fr.cliker.clikerv2.social;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.hunting.RewardLedger;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;

import java.time.*;
import java.util.*;

class SocialModuleTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private RewardLedger ledger;
    private ClaimService claims;
    private OutgoingItems outgoing;
    private SocialModule module;
    private SocialStore store;
    private PlayerMock a, b, c;
    private final MutableClock clock = new MutableClock();

    private static final class MutableClock extends Clock {
        long time = Instant.parse("2026-02-10T12:00:00Z").toEpochMilli();

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        public Instant instant() {
            return Instant.ofEpochMilli(time);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        ConfigurationSerialization.registerClass(ItemStackMock.class);
        db = new DatabaseManager(plugin, "social-menu-" + UUID.randomUUID() + ".db");
        db.initialize();
        ledger = new RewardLedger(plugin, db);
        claims = ClaimTestFactory.create(plugin, db);
        outgoing = new OutgoingItems(plugin, db, claims, p -> {});
        store = new SocialStore(db, ledger);
        store.initialize().join();
        a = server.addPlayer("Alice");
        b = server.addPlayer("Bob");
        c = server.addPlayer("Chloe");
        for (var p : List.of(a, b, c))
            for (String permission :
                    List.of(
                            "cliker.marry.use",
                            "cliker.marry.consulter",
                            "cliker.marry.teleport",
                            "cliker.popularite.use")) p.addAttachment(plugin, permission, true);
        module = new SocialModule(plugin, db, ledger, claims, outgoing, null, clock);
        var ready = SocialModule.class.getDeclaredField("ready");
        ready.setAccessible(true);
        ready.set(module, true);
        server.getPluginManager().registerEvents(module, plugin);
    }

    @AfterEach
    void cleanup() {
        try {
            module.shutdown();
            outgoing.shutdown();
            claims.shutdown();
            ledger.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void drain() {
        for (int i = 0; i < 14; i++) {
            db.submit(connection -> null).join();
            server.getScheduler().performOneTick();
        }
    }

    private void command(PlayerMock p, String root, String... args) {
        Command cmd =
                new Command(root) {
                    @Override
                    public boolean execute(CommandSender s, String l, String[] a) {
                        return false;
                    }
                };
        module.onCommand(p, cmd, root, args);
    }

    private void expire() throws Exception {
        var f = SocialModule.class.getDeclaredMethod("expire");
        f.setAccessible(true);
        f.invoke(module);
    }

    @Test
    void requestFromLastSecondOfFebruaryNeedsRingIfAcceptedInMarch() {
        clock.time = Instant.parse("2026-02-28T22:59:59Z").toEpochMilli();
        command(a, "marry", "demand", "Bob");
        drain();
        clock.time += 2000;
        command(b, "marry", "accept", "Alice");
        drain();
        assertNull(store.bond(a.getUniqueId()).join());
    }

    @Test
    void quittingCancelsPendingRequestAndCannotLeaveAnAcceptableGhostRequest() {
        command(a, "marry", "demand", "Bob");
        drain();
        module.quit(
                new org.bukkit.event.player.PlayerQuitEvent(
                        a, net.kyori.adventure.text.Component.empty()));
        command(b, "marry", "accept", "Alice");
        drain();
        assertNull(store.bond(a.getUniqueId()).join());
    }

    @Test
    void closeButtonStillWorksAfterItemsAdderChangesItsMaterial() {
        command(a, "echelons");
        var view = a.getOpenInventory();
        view.getTopInventory().setItem(26, new ItemStack(Material.PAPER));
        var click =
                new InventoryClickEvent(
                        view,
                        InventoryType.SlotType.CONTAINER,
                        26,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        drain();
        assertTrue(click.isCancelled());
        assertNotSame(view.getTopInventory(), a.getOpenInventory().getTopInventory());
    }

    @Test
    void pendingRequestIsExclusiveAndDoubleAcceptanceCannotCreateTwoMarriages() {
        command(a, "marry", "demand", "Bob");
        drain();
        command(c, "marry", "demand", "Bob");
        drain();
        command(b, "marry", "accept", "Alice");
        command(b, "marry", "accept", "Alice");
        drain();
        assertEquals(b.getUniqueId(), store.bond(a.getUniqueId()).join().partner());
        assertEquals(a.getUniqueId(), store.bond(b.getUniqueId()).join().partner());
        assertNull(store.bond(c.getUniqueId()).join());
    }

    @Test
    void requestExpiresAfterSixtySecondsAndCannotBeAccepted() throws Exception {
        command(a, "marry", "demand", "Bob");
        drain();
        clock.time += 60000;
        expire();
        command(b, "marry", "accept", "Alice");
        drain();
        assertNull(store.bond(b.getUniqueId()).join());
    }

    @Test
    void rejectedRequestDoesNotMarryEitherPlayer() {
        command(a, "marry", "demand", "Bob");
        drain();
        command(b, "marry", "deny", "Alice");
        command(b, "marry", "accept", "Alice");
        drain();
        assertNull(store.bond(a.getUniqueId()).join());
    }

    @Test
    void octoberRequiresAuthenticRingAndExistingMarriageStillWorks() throws Exception {
        clock.time = Instant.parse("2026-10-26T12:00:00Z").toEpochMilli();
        var fake = new ItemStack(Material.AMETHYST_SHARD);
        var meta = fake.getItemMeta();
        meta.setDisplayName("Anneau de Saint-Valentin");
        fake.setItemMeta(meta);
        a.getInventory().setItemInMainHand(fake);
        command(a, "marry", "demand", "Bob");
        drain();
        command(b, "marry", "accept", "Alice");
        drain();
        assertNull(store.bond(a.getUniqueId()).join());
        var ring = SocialModule.class.getDeclaredMethod("ring");
        ring.setAccessible(true);
        a.getInventory().setItemInMainHand((ItemStack) ring.invoke(module));
        command(a, "marry", "demand", "Bob");
        drain();
        command(b, "marry", "accept", "Alice");
        drain();
        assertNotNull(store.bond(a.getUniqueId()).join());
        assertEquals(1, a.getInventory().getItemInMainHand().getAmount());
        b.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
        command(a, "marry", "inv");
        drain();
        assertEquals(Material.DIAMOND, a.getOpenInventory().getTopInventory().getItem(0).getType());
    }

    @Test
    void ringMustStillBeHeldOnAcceptance() throws Exception {
        clock.time = Instant.parse("2026-10-26T12:00:00Z").toEpochMilli();
        var ring = SocialModule.class.getDeclaredMethod("ring");
        ring.setAccessible(true);
        a.getInventory().setItemInMainHand((ItemStack) ring.invoke(module));
        command(a, "marry", "demand", "Bob");
        drain();
        a.getInventory().clear();
        command(b, "marry", "accept", "Alice");
        drain();
        assertNull(store.bond(b.getUniqueId()).join());
    }

    @Test
    void divorceNeedsExplicitConfirmationAndInsufficientFundsDoNotEndMarriage() {
        store.marry(a.getUniqueId(), b.getUniqueId(), clock.millis()).join();
        command(a, "marry", "divorce");
        drain();
        assertNotNull(store.bond(a.getUniqueId()).join());
        command(a, "marry", "divorce", "confirmer");
        drain();
        assertNotNull(store.bond(b.getUniqueId()).join());
    }

    @Test
    void snapshotInventoryAndEnderChestCannotBeLootedByAnyClickOrDrag() {
        store.marry(a.getUniqueId(), b.getUniqueId(), clock.millis()).join();
        b.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
        b.getEnderChest().setItem(0, new ItemStack(Material.EMERALD, 2));
        for (String sub : List.of("inv", "ec")) {
            command(a, "marry", sub);
            drain();
            var view = a.getOpenInventory();
            assertTrue(SocialModule.isSnapshot(view.getTopInventory()));
            for (ClickType type :
                    List.of(
                            ClickType.LEFT,
                            ClickType.RIGHT,
                            ClickType.SHIFT_LEFT,
                            ClickType.SWAP_OFFHAND,
                            ClickType.DOUBLE_CLICK,
                            ClickType.DROP)) {
                var click =
                        new InventoryClickEvent(
                                view,
                                InventoryType.SlotType.CONTAINER,
                                0,
                                type,
                                InventoryAction.PICKUP_ALL);
                server.getPluginManager().callEvent(click);
                assertTrue(click.isCancelled());
            }
            var number =
                    new InventoryClickEvent(
                            view,
                            InventoryType.SlotType.CONTAINER,
                            0,
                            ClickType.NUMBER_KEY,
                            InventoryAction.HOTBAR_SWAP,
                            1);
            server.getPluginManager().callEvent(number);
            assertTrue(number.isCancelled());
            var drag =
                    new InventoryDragEvent(
                            view,
                            new ItemStack(Material.DIAMOND),
                            new ItemStack(Material.DIAMOND, 2),
                            false,
                            Map.of(0, new ItemStack(Material.DIAMOND)));
            server.getPluginManager().callEvent(drag);
            assertTrue(drag.isCancelled());
        }
        assertEquals(3, b.getInventory().getItem(0).getAmount());
        assertEquals(2, b.getEnderChest().getItem(0).getAmount());
    }

    @Test
    void recommendationsNeedFiveHoursThenExactlyOnePointEvenWithCommandSpam() {
        command(a, "rc", "Bob");
        drain();
        assertEquals(0, store.points(b.getUniqueId()).join());
        a.setStatistic(Statistic.PLAY_ONE_MINUTE, 360000);
        command(a, "rc", "Bob");
        command(a, "rc", "Bob");
        drain();
        command(a, "rc", "Chloe");
        drain();
        assertEquals(1, store.points(b.getUniqueId()).join());
        assertEquals(0, store.points(c.getUniqueId()).join());
        String hover =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(module.hover(b.getUniqueId()));
        assertTrue(hover.contains("1 recommandation"));
        assertTrue(hover.contains("Inconnu"));
    }

    @Test
    void nonAdminCannotObtainASpecialRingAndTabCompletesRealCommandsOnly() {
        command(a, "marry", "objet", "Alice");
        drain();
        assertEquals(0, claims.store().page(a.getUniqueId(), 0).join().total());
        Command cmd =
                new Command("marry") {
                    @Override
                    public boolean execute(CommandSender s, String l, String[] a) {
                        return false;
                    }
                };
        assertFalse(module.onTabComplete(a, cmd, "marry", new String[] {""}).contains("objet"));
        assertTrue(
                module.onTabComplete(a, cmd, "marry", new String[] {"demand", "Bo"})
                        .contains("Bob"));
    }
}
