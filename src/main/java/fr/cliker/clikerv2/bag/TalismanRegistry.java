package fr.cliker.clikerv2.bag;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.rewards.OutgoingStore;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Ajouts permanents, distincts des découvertes : aucune opération de retrait joueur. */
public final class TalismanRegistry {
    private final DatabaseManager db;

    public TalismanRegistry(DatabaseManager db) {
        this.db = db;
    }

    public static String valid(String id) {
        if (id == null) return null;
        if (id.equals("treasure")
                || id.matches("afk:[1-4]")
                || id.matches("general:(lame|richesse|sagesse|fortune|vitalite):[1-5]")) return id;
        String[] parts = id.split(":");
        if (parts.length == 2 && Set.of("mob", "boss").contains(parts[0])) {
            MobTier tier = MobTier.parse(parts[1]);
            if (tier != null) return parts[0] + ":" + tier.id();
        }
        return null;
    }

    public static String id(ItemStack item, NamespacedKey tag) {
        if (item == null || !item.hasItemMeta()) return null;
        var mob = TalismanItem.mobTierOf(item);
        if (mob != null) return "mob:" + mob.id();
        if (TalismanItem.isTreasure(item)) return "treasure";
        return valid(
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(tag, PersistentDataType.STRING));
    }

    public CompletableFuture<Void> register(UUID owner, String id, String receipt, String payload) {
        if (!Objects.equals(valid(id), id))
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Talisman invalide"));
        return db.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        try (var q =
                                c.prepareStatement(
                                        "INSERT OR IGNORE INTO registered_talismans(owner,id)"
                                            + " VALUES(?,?)")) {
                            q.setString(1, owner.toString());
                            q.setString(2, id);
                            if (q.executeUpdate() != 1)
                                throw new SQLException("Talisman déjà enregistré");
                        }
                        try (var q =
                                c.prepareStatement(
                                        "INSERT OR IGNORE INTO talisman_collection(owner,id)"
                                            + " VALUES(?,?)")) {
                            q.setString(1, owner.toString());
                            q.setString(2, id);
                            q.executeUpdate();
                        }
                        OutgoingStore.commit(c, receipt, owner, payload);
                        c.commit();
                        return null;
                    } catch (SQLException error) {
                        try {
                            c.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw new DatabaseManager.DatabaseException(error);
                    }
                });
    }
}
