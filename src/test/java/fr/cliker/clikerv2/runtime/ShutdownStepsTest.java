package fr.cliker.clikerv2.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.logging.*;

class ShutdownStepsTest {
    @Test
    void aFailingModuleIsLoggedAndDoesNotPreventTheDatabaseCloseStep() {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var records = new ArrayList<LogRecord>();
        logger.addHandler(
                new Handler() {
                    public void publish(LogRecord r) {
                        records.add(r);
                    }

                    public void flush() {}

                    public void close() {}
                });
        var error = new IllegalStateException("échec simulé");
        assertFalse(
                ShutdownSteps.run(
                        logger,
                        "module",
                        () -> {
                            throw error;
                        }));
        var saved = new java.util.concurrent.atomic.AtomicBoolean();
        assertTrue(ShutdownSteps.run(logger, "SQLite", () -> saved.set(true)));
        assertTrue(saved.get());
        assertEquals(1, records.size());
        assertSame(error, records.getFirst().getThrown());
        assertEquals(Level.SEVERE, records.getFirst().getLevel());
    }
}
