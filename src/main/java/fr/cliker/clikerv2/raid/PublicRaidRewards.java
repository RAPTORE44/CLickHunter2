package fr.cliker.clikerv2.raid;

import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.hunting.Gains;

import java.util.*;

/** Chef : lot normal. Trois renforts au plus : un tiers chacun, s'ils ont contribué. */
public final class PublicRaidRewards {
    private PublicRaidRewards() {}

    public static Gains third(Gains base) {
        return Gains.of(
                base.amount(CurrencyType.ECUS) / 3,
                base.amount(CurrencyType.GEMMES_SOMBRES) / 3,
                base.amount(CurrencyType.CENDRES) / 3,
                base.xp() / 3);
    }

    public static Map<UUID, Gains> allocate(
            Gains base, UUID leader, Set<UUID> helpers, Map<UUID, Long> damage) {
        if (helpers.size() > 3) throw new IllegalArgumentException("Trois renforts maximum");
        var result = new LinkedHashMap<UUID, Gains>();
        if (damage.getOrDefault(leader, 0L) > 0) result.put(leader, base);
        for (UUID helper : helpers)
            if (!helper.equals(leader) && damage.getOrDefault(helper, 0L) > 0)
                result.put(helper, third(base));
        return result;
    }
}
