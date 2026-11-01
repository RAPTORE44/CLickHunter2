package fr.cliker.clikerv2.performance;

import fr.cliker.clikerv2.daily.DailySettings;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;

class ColorBenchmark {
    private static volatile int sink;
    private static final String[] LABELS = {
        "&aMenu principal",
        "&7Clique pour ouvrir.",
        "&6Prix : &e1 000 Écus",
        "&bInformations",
        "&cVerrouillé",
        "&aDisponible",
        "&eRetour",
        "&dRécompenses"
    };

    private long loop(boolean cache, int count) {
        int total = 0;
        long start = System.nanoTime();
        for (int i = 0; i < count; i++)
            total +=
                    (cache
                                    ? DailySettings.color(LABELS[i % LABELS.length])
                                    : LegacyComponentSerializer.legacyAmpersand()
                                            .deserialize(LABELS[i % LABELS.length]))
                            .hashCode();
        sink = total;
        return System.nanoTime() - start;
    }

    @Test
    void repeatedLabels() throws Exception {
        for (String label : LABELS)
            org.junit.jupiter.api.Assertions.assertEquals(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(label),
                    DailySettings.color(label));
        for (int i = 0; i < 20; i++) {
            loop(false, 20000);
            loop(true, 20000);
        }
        List<Long> before = new ArrayList<>(), after = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (i % 2 == 0) {
                before.add(loop(false, 100000));
                after.add(loop(true, 100000));
            } else {
                after.add(loop(true, 100000));
                before.add(loop(false, 100000));
            }
        }
        String out =
                "{\"scenario\":\"100000 couleurs, 8 libellés, 20 échauffements, 5 paires alternées"
                    + " même JVM\",\"beforeNanoseconds\":"
                        + before
                        + ",\"afterNanoseconds\":"
                        + after
                        + "}";
        if (System.getProperty("benchmark.output") != null)
            Files.writeString(Path.of(System.getProperty("benchmark.output")), out);
        System.out.println(out);
    }
}
