package fr.cliker.clikerv2.economy;

import org.bukkit.permissions.Permissible;

/**
 * Bonus de gains accordés par permission : cliker.bonus.<categorie>.<N> multiplie les gains par N (jusqu'à x10).
 * Catégories : ecus, gemmes, vente, xp. Le plus haut N accordé au joueur est utilisé (pas cumulatif).
 */
public final class PermissionBonus {
    public static final int MAX_MULTIPLIER = 10;

    private PermissionBonus() {}

    public static long multiplier(Permissible permissible, String category) {
        for (int n = MAX_MULTIPLIER; n >= 2; n--) {
            if (permissible.hasPermission("cliker.bonus." + category + "." + n)) return n;
        }
        return 1;
    }
}
