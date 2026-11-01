package fr.cliker.clikerv2.performance;

import static org.junit.jupiter.api.Assertions.*;

import fr.cliker.clikerv2.DatabaseManager;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class DatabaseConnectionTest {
    private DatabaseManager db;

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        db =
                new DatabaseManager(
                        MockBukkit.createMockPlugin(), "connection-" + UUID.randomUUID() + ".db");
        db.initialize();
        db.submit(
                        c -> {
                            try (var s = c.createStatement()) {
                                s.execute("CREATE TABLE isolated(value INTEGER)");
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
    }

    @AfterEach
    void close() {
        try {
            db.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void serializedOperationsReuseOneConnection() {
        var first = db.submit(c -> c).join();
        assertSame(first, db.submit(c -> c).join());
    }

    @Test
    void failedTransactionDoesNotLeakIntoTheNextOperation() {
        assertThrows(
                Exception.class,
                () ->
                        db.submit(
                                        c -> {
                                            try {
                                                c.setAutoCommit(false);
                                                try (var s = c.createStatement()) {
                                                    s.execute("INSERT INTO isolated VALUES(1)");
                                                }
                                                throw new IllegalStateException("expected failure");
                                            } catch (java.sql.SQLException e) {
                                                throw new RuntimeException(e);
                                            }
                                        })
                                .join());
        assertEquals(
                0,
                db.submit(
                                c -> {
                                    try (var s = c.createStatement();
                                            var r =
                                                    s.executeQuery(
                                                            "SELECT count(*) FROM isolated")) {
                                        assertTrue(c.getAutoCommit());
                                        return r.getInt(1);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .join());
    }

    @Test
    void forgottenCommitIsRolledBackAsBefore() {
        db.submit(
                        c -> {
                            try {
                                c.setAutoCommit(false);
                                try (var s = c.createStatement()) {
                                    s.execute("INSERT INTO isolated VALUES(2)");
                                }
                                return null;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
        assertEquals(
                0,
                db.submit(
                                c -> {
                                    try (var s = c.createStatement();
                                            var r =
                                                    s.executeQuery(
                                                            "SELECT count(*) FROM isolated")) {
                                        return r.getInt(1);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .join());
    }

    @Test
    void shutdownDrainsAndClosesAndRejectsWithAFuture() {
        var connection = db.submit(c -> c).join();
        db.close();
        assertTrue(assertDoesNotThrow(connection::isClosed));
        var result = assertDoesNotThrow(() -> db.submit(c -> 1));
        assertTrue(result.isCompletedExceptionally());
    }
}
