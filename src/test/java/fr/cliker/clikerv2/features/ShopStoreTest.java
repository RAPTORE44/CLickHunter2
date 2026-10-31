package fr.cliker.clikerv2.features;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class ShopStoreTest {
    @Test
    void purchaseSourceIsIdempotentAndCanBeUsedForDirectDelivery() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var db = new DatabaseManager(plugin, "shop-" + UUID.randomUUID() + ".db");
        db.initialize();
        try {
            var economy = new EconomyService(db, Map.of());
            var store = new ShopStore(db);
            var owner = UUID.randomUUID();
            economy.set(owner, "Buyer", CurrencyType.ECUS, 100).join();
            String item = ClaimService.serialize(new ItemStack(Material.DIAMOND));
            assertTrue(store.buy(owner, "Buyer", "offer", 5, 10, item, "purchase-unique").join());
            assertTrue(store.buy(owner, "Buyer", "offer", 5, 10, item, "purchase-unique").join());
            assertEquals(90, economy.getBalance(owner, "Buyer", CurrencyType.ECUS).join());
            assertEquals(1, store.stocks().join().get("offer"));
            var mail = new MailStore(db);
            assertNotNull(mail.pendingSource(owner, "purchase-unique").join());
            assertEquals(1, mail.page(owner, 0).join().total());
        } finally {
            db.close();
            MockBukkit.unmock();
        }
    }
}
