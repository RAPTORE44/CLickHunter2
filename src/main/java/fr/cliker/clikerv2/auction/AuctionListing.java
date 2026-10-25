package fr.cliker.clikerv2.auction;

import java.util.UUID;

/**
 * Annonce active à l'hôtel des ventes. itemYaml = l'item sérialisé
 * (YamlConfiguration#saveToString).
 */
public record AuctionListing(
        String id, UUID sellerUuid, String sellerName, String itemYaml, long price, long listedAt) {
    public static long tax(long price) {
        if (price < 0) throw new IllegalArgumentException("Prix négatif");
        return price / 100 + (price % 100 >= 50 ? 1 : 0);
    }
}
