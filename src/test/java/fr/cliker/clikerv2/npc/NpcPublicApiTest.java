package fr.cliker.clikerv2.npc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NpcPublicApiTest {
    @Test
    void publicApiMethodCanInvokeTheVendorsAnonymousImplementation() throws Exception {
        var implementation = fr.cliker.clikerv2.npc.fixture.HiddenApiFactory.create();
        assertThrows(IllegalAccessException.class, () -> ZnpcsNpc.call(implementation, "get"));
        assertEquals(
                "manager",
                ZnpcsNpc.publicApiCall(java.util.function.Supplier.class, implementation, "get"));
    }
}
