package fr.cliker.clikerv2.auction;

import java.util.UUID;

/** Annonce active à l'hôtel des ventes. itemYaml = l'item sérialisé (YamlConfiguration#saveToString). */
public record AuctionListing(String id, UUID sellerUuid, String sellerName, String itemYaml, long price, long listedAt) {
}
