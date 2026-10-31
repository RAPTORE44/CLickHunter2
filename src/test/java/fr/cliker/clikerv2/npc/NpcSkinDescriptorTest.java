package fr.cliker.clikerv2.npc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NpcSkinDescriptorTest {
    public static class Factory {
        final Object marker = new Object();
        String requested;

        public Object createRefreshingDescriptor(String name) {
            requested = name;
            return marker;
        }

        public Object createStaticDescriptor(String name) {
            throw new AssertionError("Attente réseau interdite sur le thread serveur");
        }
    }

    @Test
    void playerSkinUsesTheDeferredProviderDescriptorInsteadOfTheBlockingStaticFactory()
            throws Exception {
        var factory = new Factory();
        assertSame(factory.marker, ZnpcsNpc.skinDescriptor(factory, "Nati144378"));
        assertEquals("Nati144378", factory.requested);
    }
}
