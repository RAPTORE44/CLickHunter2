package fr.cliker.clikerv2.essence;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.entity.Player;

import java.util.*;

/** Un relevé de l'inventaire par coup ; aucune recherche par compresseur et par palier. */
public final class CompressorManager implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(org.bukkit.event.player.PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getHand() == null) return;
        var tier = CompressorItem.tierOf(event.getItem());
        if (tier == null) return;
        event.setCancelled(true);
        var player = event.getPlayer();
        var key = new org.bukkit.NamespacedKey(CompressorItem.MOB_KEY.getNamespace(), "compressor_active_" + tier.id());
        var data = player.getPersistentDataContainer();
        if (data.has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
            player.sendMessage(net.kyori.adventure.text.Component.text("Cet autocompresseur est déjà activé.", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            return;
        }
        data.set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        event.getItem().subtract(1);
        player.saveData();
        player.sendMessage(net.kyori.adventure.text.Component.text("Autocompresseur " + tier.label() + " activé ! 64 ressources donnent 1 du palier suivant.", net.kyori.adventure.text.format.NamedTextColor.GREEN));
        onHit(player);
    }

    public void onHit(Player player) {
        var contents = player.getInventory().getContents();
        Set<MobTier> active = EnumSet.noneOf(MobTier.class);
        for (var tier : MobTier.values()) {
            var key = new org.bukkit.NamespacedKey(CompressorItem.MOB_KEY.getNamespace(), "compressor_active_" + tier.id());
            if (player.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.BYTE)) active.add(tier);
        }
        if (active.isEmpty()) return;
        Map<MobTier, int[]> counts = new EnumMap<>(MobTier.class);
        // Les 36 premières cases sont le stockage ; les essences en main secondaire ne sont pas
        // consommées.
        for (int slot = 0; slot < Math.min(36, contents.length); slot++) {
            var stack = contents[slot];
            var tier = EssenceItem.tierOf(stack);
            if (tier != null && active.contains(tier))
                counts.computeIfAbsent(tier, k -> new int[EssenceRarity.values().length])[
                                EssenceItem.rarityOf(stack).ordinal()] +=
                        stack.getAmount();
        }
        for (var tier : active) {
            var count = counts.get(tier);
            if (count == null) continue;
            for (var rarity : EssenceRarity.values())
                if (rarity.next() != null && count[rarity.ordinal()] >= EssenceCompression.RATIO) {
                    EssenceCompression.cascade(player, tier);
                    break;
                }
        }
    }
}
