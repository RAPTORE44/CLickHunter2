package fr.cliker.clikerv2.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.net.*;
import java.nio.file.Path;

class OptionalDependencyLoadingTest {
    @Test
    void buyZoneEventsCanBeInspectedWhenLuckPermsIsAbsent() throws Exception {
        try (var loader =
                new URLClassLoader(
                        new URL[] {Path.of("target/classes").toUri().toURL()},
                        getClass().getClassLoader()) {
                    @Override
                    protected synchronized Class<?> loadClass(String name, boolean resolve)
                            throws ClassNotFoundException {
                        if (name.startsWith("net.luckperms."))
                            throw new ClassNotFoundException(name);
                        if (name.startsWith("fr.cliker.clikerv2.")) {
                            var result = findLoadedClass(name);
                            if (result == null) result = findClass(name);
                            if (resolve) resolveClass(result);
                            return result;
                        }
                        return super.loadClass(name, resolve);
                    }
                }) {
            var listener = Class.forName("fr.cliker.clikerv2.buyzone.BuyZoneModule", false, loader);
            var methods = assertDoesNotThrow(listener::getDeclaredMethods);
            assertTrue(java.util.Arrays.stream(methods).anyMatch(m -> m.getName().equals("move")));
            assertTrue(java.util.Arrays.stream(methods).anyMatch(m -> m.getName().equals("click")));
        }
    }
}
