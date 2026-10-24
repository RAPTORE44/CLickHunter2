package fr.cliker.clikerv2.prestige;

public final class PrestigeType {
    private PrestigeType() {}

    /** Écus supplémentaires par kill, par niveau de prestige. */
    public static final long ECUS_PER_LEVEL = 100;
    /** Gemmes Sombres supplémentaires par kill, par niveau de prestige (unité affichée, pas dixièmes). */
    public static final long GEMMES_PER_LEVEL = 1;
    /** XP supplémentaire par kill, par niveau de prestige. */
    public static final long XP_PER_LEVEL = 10;
    /** Dégâts supplémentaires, par niveau de prestige. */
    public static final long FORCE_PER_LEVEL = 10;

    /** Niveau XP requis pour atteindre le prestige donné. */
    public static int requiredLevel(int prestige) { return prestige * 100; }

    /** Nombre de mobs tués requis pour atteindre le prestige donné. */
    public static long requiredKills(int prestige) {
        return switch (prestige) {
            case 1 -> 2_500L;
            case 2 -> 10_000L;
            case 3 -> 30_000L;
            case 4 -> 70_000L;
            case 5 -> 150_000L;
            default -> {
                java.math.BigInteger required = java.math.BigInteger.valueOf(150_000L).shiftLeft(Math.max(0, prestige - 5));
                yield required.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
            }
        };
    }

    public static String toRoman(int number) {
        if (number <= 0) return "0";
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        int remaining = number;
        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                remaining -= values[i];
                result.append(symbols[i]);
            }
        }
        return result.toString();
    }
}
