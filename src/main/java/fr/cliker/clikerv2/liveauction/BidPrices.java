package fr.cliker.clikerv2.liveauction;

import java.util.*;

/**
 * Prix strictement croissants, calculés en entiers ; les cases suivent cinq rangées serpentines.
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

    public static int slot(int index) {
        if (index < 0 || index >= 45) throw new IllegalArgumentException();
        int row = index / 9, col = index % 9;
        return row * 9 + (row % 2 == 0 ? col : 8 - col);
    }
}
