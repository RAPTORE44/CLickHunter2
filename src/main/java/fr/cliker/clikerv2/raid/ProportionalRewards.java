package fr.cliker.clikerv2.raid;

import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.hunting.Gains;

import java.math.BigInteger;
import java.util.*;

/** Plus forts restes : partage exact des unités entières, sans créer ni perdre de monnaie. */
public final class ProportionalRewards {
    private ProportionalRewards() {}

    public static Map<UUID, Long> split(long pool, Map<UUID, Long> damage) {
        if (pool < 0 || damage.values().stream().anyMatch(v -> v < 0))
            throw new IllegalArgumentException("Montant négatif");
        var entries =
                damage.entrySet().stream()
                        .filter(e -> e.getValue() > 0)
                        .sorted(Map.Entry.comparingByKey())
                        .toList();
        BigInteger total =
                entries.stream()
                        .map(e -> BigInteger.valueOf(e.getValue()))
                        .reduce(BigInteger.ZERO, BigInteger::add);
        Map<UUID, Long> result = new LinkedHashMap<>();
        Map<UUID, BigInteger> rests = new HashMap<>();
        if (total.signum() == 0) return result;
        long given = 0;
        for (var entry : entries) {
            var qr =
                    BigInteger.valueOf(pool)
                            .multiply(BigInteger.valueOf(entry.getValue()))
                            .divideAndRemainder(total);
            long amount = qr[0].longValueExact();
            result.put(entry.getKey(), amount);
            rests.put(entry.getKey(), qr[1]);
            given += amount;
        }
        var order =
                entries.stream()
                        .map(Map.Entry::getKey)
                        .sorted(
                                Comparator.<UUID, BigInteger>comparing(rests::get)
                                        .reversed()
                                        .thenComparing(Comparator.naturalOrder()))
                        .toList();
        for (int i = 0; i < pool - given; i++) result.compute(order.get(i), (k, v) -> v + 1);
        return result;
    }

    public static Map<UUID, Gains> allocate(Gains pool, Map<UUID, Long> damage) {
        Map<UUID, Map<CurrencyType, Long>> money = new LinkedHashMap<>();
        for (var currency : CurrencyType.values())
            split(pool.amount(currency), damage)
                    .forEach(
                            (id, n) ->
                                    money.computeIfAbsent(
                                                    id, k -> new EnumMap<>(CurrencyType.class))
                                            .put(currency, n));
        var xp = split(pool.xp(), damage);
        Map<UUID, Gains> result = new LinkedHashMap<>();
        money.forEach((id, m) -> result.put(id, new Gains(m, xp.getOrDefault(id, 0L))));
        return result;
    }
}
