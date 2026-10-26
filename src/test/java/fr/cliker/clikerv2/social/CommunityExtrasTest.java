package fr.cliker.clikerv2.social;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.hunting.*;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CommunityExtrasTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private RewardLedger ledger;
    private ClaimService claims;
    private CommunityStore store;
    private CommunityExtras module;
    private PlayerMock player;

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "extra-" + java.util.UUID.randomUUID() + ".db");
        db.initialize();
        ledger = new RewardLedger(plugin, db);
        claims = ClaimTestFactory.create(plugin, db);
        store = new CommunityStore(db, ledger);
        store.initialize().join();
        player = server.addPlayer();
        player.addAttachment(plugin, "cliker.tag.use", true);
        player.addAttachment(plugin, "cliker.chatcouleur.use", true);
        module = new CommunityExtras(plugin, db, ledger, claims);
        var f = CommunityExtras.class.getDeclaredField("ready");
        f.setAccessible(true);
        f.set(module, true);
    }

    @AfterEach
    void close() {
        try {
            module.shutdown();
            claims.shutdown();
            ledger.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void command(String root, String... args) {
        Command cmd =
                new Command(root) {
                    public boolean execute(CommandSender s, String l, String[] a) {
                        return false;
                    }
                };
        module.onCommand(player, cmd, root, args);
        drain();
    }

    private void drain() {
        for (int i = 0; i < 12; i++) {
            db.submit(c -> null).join();
            server.getScheduler().performOneTick();
        }
    }

    private void click(int slot) {
        server.getPluginManager()
                .callEvent(
                        new InventoryClickEvent(
                                player.getOpenInventory(),
                                InventoryType.SlotType.CONTAINER,
                                slot,
                                ClickType.LEFT,
                                InventoryAction.PICKUP_ALL));
        drain();
    }

    @Test
    void tagNeedsBothProgressionAndPermissionAndShowsValidatedBugCount() {
        player.addAttachment(plugin, "cliker.tag.bugs.1", true);
        command("tag");
        click(0);
        assertEquals(
                Material.GRAY_DYE,
                player.getOpenInventory().getTopInventory().getItem(0).getType());
        click(0);
        assertEquals(
                "",
                store.profiles(java.util.List.of(player.getUniqueId()))
                        .join()
                        .get(player.getUniqueId())
                        .tag());
        store.bugs(player.getUniqueId(), 3).join();
        command("tag");
        click(0);
        assertEquals(
                Material.SLIME_BALL,
                player.getOpenInventory().getTopInventory().getItem(0).getType());
        click(0);
        assertEquals(
                "bugs_1",
                store.profiles(java.util.List.of(player.getUniqueId()))
                        .join()
                        .get(player.getUniqueId())
                        .tag());
        assertTrue(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(module.style(player.getUniqueId()).tag())
                        .contains("3"));
        assertEquals(
                Material.GRAY_DYE,
                player.getOpenInventory().getTopInventory().getItem(1).getType());
    }

    @Test
    void chatColorCannotBeChosenWithoutPermissionAndRevocationRemovesIt() throws Exception {
        command("chatcouleur");
        click(9);
        assertEquals(
                "white",
                store.profiles(java.util.List.of(player.getUniqueId()))
                        .join()
                        .get(player.getUniqueId())
                        .color());
        var attachment = player.addAttachment(plugin, "cliker.chat.couleur.gold", true);
        command("chatcouleur");
        click(9);
        assertEquals(
                "gold",
                store.profiles(java.util.List.of(player.getUniqueId()))
                        .join()
                        .get(player.getUniqueId())
                        .color());
        assertEquals(
                net.kyori.adventure.text.format.NamedTextColor.GOLD,
                module.style(player.getUniqueId()).color());
        attachment.setPermission("cliker.chat.couleur.gold", false);
        var update = CommunityExtras.class.getDeclaredMethod("updateStyle", Player.class);
        update.setAccessible(true);
        update.invoke(module, player);
        assertEquals(
                net.kyori.adventure.text.format.NamedTextColor.WHITE,
                module.style(player.getUniqueId()).color());
    }

    @Test
    void playerCannotGrantTheirOwnValidatedBugCounter() {
        command("tag", "bugs", player.getName(), "100");
        assertEquals(
                0,
                store.profiles(java.util.List.of(player.getUniqueId()))
                        .join()
                        .get(player.getUniqueId())
                        .bugs());
    }
}
