package fr.cliker.clikerv2.economy;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.util.*;

class CurrencyCommandTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private DatabaseManager db;
    private EconomyService economy;
    private ClaimService claims;
    private CurrencyCommand command;
    private org.mockbukkit.mockbukkit.entity.PlayerMock admin;
    private final Command entry =
            new Command("ecus") {
                public boolean execute(CommandSender s, String l, String[] a) {
                    return false;
                }
            };

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new DatabaseManager(plugin, "commands-" + UUID.randomUUID() + ".db");
        db.initialize();
        economy = new EconomyService(db, Map.of());
        claims = ClaimTestFactory.create(plugin, db);
        Bukkit.getServicesManager()
                .register(ClaimService.class, claims, plugin, ServicePriority.Normal);
        command = new CurrencyCommand(plugin, economy, CurrencyType.ECUS);
        admin = server.addPlayer();
        admin.addAttachment(plugin, "cliker.money.use", true);
        admin.addAttachment(plugin, "cliker.money.admin", true);
    }

    @AfterEach
    void cleanup() {
        try {
            claims.shutdown();
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    private void drain() {
        db.submit(c -> null).join();
        server.getScheduler().performTicks(2);
    }

    @Test
    void offlineKnownPlayerUsesDatabaseIdentity() {
        UUID offline = UUID.randomUUID();
        economy.set(offline, "AncienJoueur", CurrencyType.ECUS, 10).join();
        command.onCommand(admin, entry, "ecus", new String[] {"give", "AncienJoueur", "25"});
        drain();
        assertEquals(35, economy.getBalances(offline).join().ecus());
    }

    @Test
    void unknownTargetIsRejectedWithoutCreatingPhantomAccount() {
        command.onCommand(admin, entry, "ecus", new String[] {"give", "NobodyFound", "25"});
        drain();
        assertTrue(admin.nextMessage().contains("pas connu"));
        assertTrue(economy.topBalances(CurrencyType.ECUS, 10).join().isEmpty());
    }

    @Test
    void unprivilegedPlayerCannotCreditMoney() {
        admin.addAttachment(plugin, "cliker.money.admin", false);
        command.onCommand(admin, entry, "ecus", new String[] {"give", admin.getName(), "25"});
        assertTrue(admin.nextMessage().contains("administrateur"));
        assertEquals(0, economy.getBalances(admin.getUniqueId()).join().ecus());
    }
}
