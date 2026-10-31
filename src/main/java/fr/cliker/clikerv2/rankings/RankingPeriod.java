package fr.cliker.clikerv2.rankings;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

public enum RankingPeriod {
    DAY("Jour"),
    WEEK("Semaine"),
    MONTH("Mois"),
    YEAR("Année"),
    ALL("Depuis toujours");
    public final String label;

    RankingPeriod(String label) {
        this.label = label;
    }

    public LocalDate start(LocalDate day) {
        return switch (this) {
            case DAY, ALL -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
            case YEAR -> day.withDayOfYear(1);
        };
    }

    public LocalDate shift(LocalDate day, int direction) {
        LocalDate start = start(day);
        return switch (this) {
            case DAY, ALL -> start.plusDays(direction);
            case WEEK -> start.plusWeeks(direction);
            case MONTH -> start.plusMonths(direction);
            case YEAR -> start.plusYears(direction);
        };
    }

    public boolean available(LocalDate day, LocalDate since, LocalDate today) {
        return this == ALL || !start(day).isAfter(today) && shift(day, 1).isAfter(since);
    }

    public String range(LocalDate day) {
        if (this == ALL) return label;
        var format = DateTimeFormatter.ofPattern("dd/MM/uuuu");
        LocalDate start = start(day);
        return this == DAY
                ? start.format(format)
                : start.format(format) + " — " + shift(day, 1).minusDays(1).format(format);
    }
}
