package fr.cliker.clikerv2.daily;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/** Calcul pur par dates locales : aucun délai de 24 heures. */
public record DailyProgress(LocalDate lastClaim, int day) {
    public static final int DAYS = 31;
    public static final DailyProgress EMPTY = new DailyProgress(null, 0);
    public enum Status { AVAILABLE, LOCKED, CLAIMED }

    public DailyProgress {
        if ((lastClaim == null && day != 0) || (lastClaim != null && (day < 1 || day > DAYS))) {
            throw new IllegalArgumentException("Progression journalière invalide");
        }
    }

    public boolean claimedToday(LocalDate today) {
        // Bloque aussi une date future, en cas de recul de l'horloge du serveur.
        return lastClaim != null && !lastClaim.isBefore(today);
    }

    public int currentDay(LocalDate today) {
        if (claimedToday(today)) return day;
        return lastClaim != null && lastClaim.equals(today.minusDays(1)) && day < DAYS ? day + 1 : 1;
    }

    public Status status(int rewardDay, LocalDate today) {
        if (rewardDay < 1 || rewardDay > DAYS) throw new IllegalArgumentException("Jour hors calendrier");
        int current = currentDay(today);
        if (rewardDay < current || (rewardDay == current && claimedToday(today))) return Status.CLAIMED;
        return rewardDay == current ? Status.AVAILABLE : Status.LOCKED;
    }

    public Duration untilUnlock(int rewardDay, ZonedDateTime now) {
        if (status(rewardDay, now.toLocalDate()) != Status.LOCKED) return Duration.ZERO;
        long offset = rewardDay - currentDay(now.toLocalDate());
        // Une date future enregistrée ne doit pas annoncer un déblocage avant cette date.
        LocalDate base = lastClaim != null && lastClaim.isAfter(now.toLocalDate()) ? lastClaim : now.toLocalDate();
        return Duration.between(now, base.plusDays(offset).atStartOfDay(now.getZone()));
    }
}
