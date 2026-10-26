package fr.cliker.clikerv2.liveauction;

import java.util.*;

/**
 * Prix strictement croissants, calculés en entiers ; les cases suivent colonnes descendantes et
 * montantes, espacées de deux cases.
 */
public final class BidPrices {
    private BidPrices() {}

    public static List<Long> generate(long first, long maximum, int percent) {
        if (first < 1
                || maximum < first
                || maximum > 1_000_000_000_000_000L
                || percent < 1
                || percent > 100)
            throw new IllegalArgumentException("Échelle des enchères invalide");
        var prices = new ArrayList<Long>();
        long price = first;
        while (true) {
            prices.add(price);
            if (price == maximum) break;
            price = Math.min(maximum, price + Math.max(1, (price * percent + 99) / 100));
        }
        return List.copyOf(prices);
    }

    public static final int PAGE_SIZE = 29;
    private static final int[] SLOTS = buildPath();

    private static int[] buildPath() {
        int[] result = new int[PAGE_SIZE];
        int at = 0;
        for (int column = 0; column <= 8; column += 2) {
            boolean down = column % 4 == 0;
            if (column > 0) result[at++] = (down ? 0 : 4) * 9 + column - 1;
            for (int row = 0; row < 5; row++) result[at++] = (down ? row : 4 - row) * 9 + column;
        }
        return result;
    }

    public static int slot(int index) {
        if (index < 0 || index >= PAGE_SIZE) throw new IllegalArgumentException();
        return SLOTS[index];
    }

    public static int indexAt(int slot) {
        for (int i = 0; i < PAGE_SIZE; i++) if (SLOTS[i] == slot) return i;
        return -1;
    }
}
