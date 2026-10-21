package fr.cliker.clikerv2;

import java.util.Locale;

public final class Numbers {
    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q"};

    private Numbers() {}

    public static String compact(long value) { return compact((double) value); }

    public static String compact(double value) {
        boolean negative = value < 0;
        double num = Math.abs(value);
        int suffixIndex = 0;
        while (num >= 1000 && suffixIndex < SUFFIXES.length - 1) {
            num /= 1000;
            suffixIndex++;
        }
        String formatted = num == Math.floor(num) ? String.valueOf((long) num) : String.format(Locale.ROOT, "%.1f", num);
        return (negative ? "-" : "") + formatted + SUFFIXES[suffixIndex];
    }
}
