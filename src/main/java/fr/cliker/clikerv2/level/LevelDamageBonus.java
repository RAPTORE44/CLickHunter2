package fr.cliker.clikerv2.level;

public final class LevelDamageBonus {
    /** Bonus de dégâts par niveau XP vanille du joueur. */
    private static final double PERCENT_PER_LEVEL = 0.1;

    private LevelDamageBonus() {}

    public static double percent(int level) {
        return level * PERCENT_PER_LEVEL;
    }

    public static long apply(long damage, int level) {
        return Math.round(damage * (1 + percent(level) / 100.0));
    }
}
