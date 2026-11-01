package fr.cliker.clikerv2.performance;

import fr.cliker.clikerv2.DatabaseManager;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.*;
import java.util.*;

class SqlBenchmark {
    @Test
    void queuedReads() throws Exception {
        MockBukkit.mock();
        var db =
                new DatabaseManager(
                        MockBukkit.createMockPlugin(), "bench-" + UUID.randomUUID() + ".db");
        try {
            db.initialize();
            List<Long> rounds = new ArrayList<>();
            for (int round = 0; round < 4; round++) {
                long start = System.nanoTime();
                var jobs = new ArrayList<java.util.concurrent.CompletableFuture<Integer>>();
                for (int i = 0; i < 400; i++)
                    jobs.add(
                            db.submit(
                                    c -> {
                                        try (var s = c.createStatement();
                                                var r = s.executeQuery("SELECT 1")) {
                                            return r.getInt(1);
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    }));
                for (var f : jobs) org.junit.jupiter.api.Assertions.assertEquals(1, f.join());
                long ns = System.nanoTime() - start;
                if (round > 0) rounds.add(ns);
            }
            String result =
                    "{\"scenario\":\"400 SELECT 1 en file, 3 répétitions après"
                        + " échauffement\",\"nanoseconds\":"
                            + rounds
                            + "}";
            String output = System.getProperty("benchmark.output");
            if (output != null) Files.writeString(Path.of(output), result);
            System.out.println(result);
        } finally {
            db.close();
            MockBukkit.unmock();
        }
    }
}
