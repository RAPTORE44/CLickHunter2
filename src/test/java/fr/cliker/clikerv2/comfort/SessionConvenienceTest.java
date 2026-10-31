package fr.cliker.clikerv2.comfort;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class SessionConvenienceTest {
    private ServerMock server;
    private PlayerMock player;
    private SessionConvenience service;
    private org.bukkit.plugin.java.JavaPlugin plugin;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
        service = new SessionConvenience(plugin);
        service.start();
    }

    @AfterEach
    void cleanup() {
        service.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void creativeModeIsRestoredAfterForcedSurvivalOnLogin() {
        player.setGameMode(GameMode.CREATIVE);
        service.quit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        player.setGameMode(GameMode.SURVIVAL);
        service.join(new PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()));
        server.getScheduler().performOneTick();
        assertEquals(GameMode.CREATIVE, player.getGameMode());
    }

    @Test
    void menuMovesExistingItemWithoutLossAndNeverDuplicates() {
        player.getInventory().clear();
        player.getInventory().setItem(8, new ItemStack(Material.DIAMOND, 23));
        service.ensureMenu(player);
        service.ensureMenu(player);
        assertTrue(service.isMenu(player.getInventory().getItem(8)));
        assertEquals(23, player.getInventory().getItem(0).getAmount());
        assertEquals(
                1,
                java.util.Arrays.stream(player.getInventory().getStorageContents())
                        .filter(service::isMenu)
                        .count());
        assertTrue(PlayerComfort.protectedForSale(player, player.getInventory().getItem(8)));
    }

    @Test
    void fullInventoryIsNeverOverwritten() {
        for (int i = 0; i < 36; i++)
            player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        player.getInventory().setItem(8, new ItemStack(Material.DIAMOND, 23));
        service.ensureMenu(player);
        assertEquals(Material.DIAMOND, player.getInventory().getItem(8).getType());
        assertEquals(23, player.getInventory().getItem(8).getAmount());
    }

    @Test
    void menuCannotBeDroppedEvenWhenOrdinaryDropsAreEnabled() {
        var world = server.addSimpleWorld("drops");
        var entity = world.dropItem(world.getSpawnLocation(), player.getInventory().getItem(8));
        var event = new PlayerDropItemEvent(player, entity);
        service.drop(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void gmRequiresAdministratorPermissionAndSupportsModes() {
        var plugin = MockBukkit.createMockPlugin();
        Command cmd =
                new Command("gm") {
                    @Override
                    public boolean execute(CommandSender s, String l, String[] a) {
                        return false;
                    }
                };
        player.addAttachment(plugin, "cliker.gamemode.admin", false);
        service.onCommand(player, cmd, "gm", new String[] {"1"});
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
        player.addAttachment(plugin, "cliker.gamemode.admin", true);
        service.onCommand(player, cmd, "gm", new String[] {"1"});
        assertEquals(GameMode.CREATIVE, player.getGameMode());
        service.onCommand(player, cmd, "gm", new String[0]);
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
        service.onCommand(player, cmd, "gm", new String[] {"invalide"});
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
    }

    @Test
    void hotbarMainHandRepeatsOpenOnceAndOffhandIsIgnored() {
        var opens = new java.util.concurrent.atomic.AtomicInteger();
        server.getCommandMap()
                .register(
                        "test",
                        new Command("menu") {
                            @Override
                            public boolean execute(
                                    CommandSender sender, String label, String[] args) {
                                opens.incrementAndGet();
                                player.openInventory(Bukkit.createInventory(null, 54));
                                return true;
                            }
                        });
        service.ensureMenu(player);
        var star = player.getInventory().getItem(8);
        service.use(
                new PlayerInteractEvent(
                        player,
                        org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                        star,
                        null,
                        null,
                        org.bukkit.inventory.EquipmentSlot.OFF_HAND));
        server.getScheduler().performOneTick();
        assertEquals(0, opens.get());
        for (int i = 0; i < 4; i++)
            service.use(
                    new PlayerInteractEvent(
                            player,
                            org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                            star,
                            null,
                            null,
                            org.bukkit.inventory.EquipmentSlot.HAND));
        server.getScheduler().performOneTick();
        assertEquals(1, opens.get());
    }
}
