package fr.cliker.clikerv2.essence;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.entity.Player;

import java.util.*;

/** Un relevé de l'inventaire par coup ; aucune recherche par compresseur et par palier. */
public final class CompressorManager implements org.bukkit.event.Listener {
    private final java.util.function.Consumer<Player> save;

    public CompressorManager() {
        this(Player::saveData);
    }

    CompressorManager(java.util.function.Consumer<Player> save) {
        this.save = save;
    }

    @org.bukkit.event.EventHandler(
            priority = org.bukkit.event.EventPriority.HIGH,
            ignoreCancelled = false)
    public void onUse(org.bukkit.event.player.PlayerInteractEvent event) {
        // Paper peut refuser l'interaction avec l'air tout en autorisant l'usage de l'objet.
        // Respecter le résultat de l'objet, plutôt que le drapeau d'annulation du bloc.
        if (!event.getAction().isRightClick()
                || event.getHand() == null
                || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        var tier = CompressorItem.tierOf(event.getItem());
        if (tier == null) return;
        event.setCancelled(true);
        var player = event.getPlayer();
        var key = CompressorItem.activationKey(tier);
        var inventory = player.getInventory();
        var held = inventory.getItem(event.getHand());
        if (held == null
                || held.getAmount() <= 0
                || CompressorItem.tierOf(held) != tier
                || !held.isSimilar(event.getItem())) return;
        var data = player.getPersistentDataContainer();
        if (data.has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
            player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                            "Cet autocompresseur est déjà activé.",
                            net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            return;
        }
        var original = held.clone();
        data.set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        held.subtract(1);
        inventory.setItem(event.getHand(), held.getAmount() == 0 ? null : held);
        try {
            save.accept(player);
        } catch (RuntimeException error) {
            data.remove(key);
            inventory.setItem(event.getHand(), original);
            org.bukkit.Bukkit.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Activation du compresseur non sauvegardée pour "
                                    + player.getUniqueId(),
                            error);
            player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                            "Activation impossible. Ton objet est conservé ; réessaie plus tard.",
                            net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        player.sendMessage(
                net.kyori.adventure.text.Component.text(
                        "Autocompresseur "
                                + tier.label()
                                + " activé ! 64 ressources donnent 1 du palier suivant.",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN));
        onHit(player);
    }

    public void onHit(Player player) {
        Set<MobTier> active = EnumSet.noneOf(MobTier.class);
        for (var tier : MobTier.values()) {
            var key = CompressorItem.activationKey(tier);
            if (player.getPersistentDataContainer()
                    .has(key, org.bukkit.persistence.PersistentDataType.BYTE)) active.add(tier);
        }
        if (active.isEmpty()) return;
        var contents = player.getInventory().getStorageContents();
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
                    int converted = EssenceCompression.cascade(player, tier);
                    if (converted > 0
                            && fr.cliker.clikerv2.comfort.PlayerComfort.enabled(
                                    player, "compression_sound", true))
                        player.playSound(
                                player.getLocation(),
                                org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                                0.35f,
                                1.4f);
                    break;
                }
        }
    }
}
