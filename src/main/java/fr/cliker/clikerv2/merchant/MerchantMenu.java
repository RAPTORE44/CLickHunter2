package fr.cliker.clikerv2.merchant;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.essence.CompressorItem;
import fr.cliker.clikerv2.essence.EssenceCompression;
import fr.cliker.clikerv2.essence.EssenceItem;
import fr.cliker.clikerv2.essence.EssenceRarity;
import fr.cliker.clikerv2.gear.CharmItem;
import fr.cliker.clikerv2.gear.TierGear;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI ouvert par un PNJ marchand : achète l'épée/l'armure/le compresseur du mob contre Écus +
 * Essences Communes du mob.
 */
public final class MerchantMenu implements Listener {
    // Colonne gauche (col 2) : épée en haut, armure en colonne dessous.
    private static final int SWORD_SLOT = 2;
    private static final int HELMET_SLOT = 11;
    private static final int CHESTPLATE_SLOT = 20;
    private static final int LEGGINGS_SLOT = 29;
    private static final int BOOTS_SLOT = 38;
    // Colonne droite (col 6) : compresseur en haut, puis compression/décompression des essences.
    private static final int COMPRESSOR_SLOT = 6;
    private static final int[] STEP_SLOTS = {15, 24};
    private static final int[] DECOMPRESS_SLOTS = {33, 42};
    // Centre du GUI : amulette de dégâts passive.
    private static final int CHARM_SLOT = 22;

    private final JavaPlugin plugin;
    private final EconomyService economy;

    public MerchantMenu(JavaPlugin plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void open(Player player, MobTier tier) {
        Holder holder = new Holder(tier);
        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        54,
                        Component.text(
                                "Marchand " + tier.label(),
                                NamedTextColor.LIGHT_PURPLE,
                                TextDecoration.BOLD));
        holder.inventory = inventory;
        decorate(player, inventory, tier);
        fr.cliker.clikerv2.ui.MenuNavigation.back(plugin, inventory, 49);
        player.openInventory(inventory);
    }

    private void decorate(Player player, Inventory inventory, MobTier tier) {
        ItemStack filler = filler();
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        long armorEcus = armorEcus(tier);
        long swordEcus = Math.round(armorEcus * 2.5);
        int armorEssence = armorEssenceUnits(tier);
        int swordEssence = swordEssenceUnits(tier);

        inventory.setItem(
                SWORD_SLOT,
                compareSword(
                        player,
                        purchaseItem(TierGear.createSword(tier), swordEcus, swordEssence, tier),
                        tier));
        inventory.setItem(
                HELMET_SLOT,
                purchaseItem(
                        TierGear.createArmor(tier, TierGear.ArmorSlot.HELMET),
                        armorEcus,
                        armorEssence,
                        tier));
        inventory.setItem(
                CHESTPLATE_SLOT,
                purchaseItem(
                        TierGear.createArmor(tier, TierGear.ArmorSlot.CHESTPLATE),
                        armorEcus,
                        armorEssence,
                        tier));
        inventory.setItem(
                LEGGINGS_SLOT,
                purchaseItem(
                        TierGear.createArmor(tier, TierGear.ArmorSlot.LEGGINGS),
                        armorEcus,
                        armorEssence,
                        tier));
        inventory.setItem(
                BOOTS_SLOT,
                purchaseItem(
                        TierGear.createArmor(tier, TierGear.ArmorSlot.BOOTS),
                        armorEcus,
                        armorEssence,
                        tier));
        inventory.setItem(
                COMPRESSOR_SLOT, purchaseItem(CompressorItem.create(tier), armorEcus, 0, tier));
        inventory.setItem(
                CHARM_SLOT, purchaseItem(CharmItem.create(tier), armorEcus, armorEssence, tier));
        decorateSteps(player, inventory, tier);
    }

    private void decorateSteps(Player player, Inventory inventory, MobTier tier) {
        EssenceRarity[] rarities = EssenceRarity.values();
        for (int i = 0; i < STEP_SLOTS.length; i++) {
            EssenceRarity rarity = rarities[i];
            EssenceRarity next = rarity.next();
            if (next == null) continue;
            inventory.setItem(STEP_SLOTS[i], compressStepButton(player, tier, rarity, next));
        }
        for (int i = 0; i < DECOMPRESS_SLOTS.length; i++) {
            EssenceRarity rarity = rarities[i + 1];
            EssenceRarity previous = rarity.previous();
            if (previous == null) continue;
            inventory.setItem(
                    DECOMPRESS_SLOTS[i], decompressStepButton(player, tier, rarity, previous));
        }
    }

    private ItemStack compressStepButton(
            Player player, MobTier tier, EssenceRarity rarity, EssenceRarity next) {
        int owned = EssenceItem.count(player, tier, rarity);
        ItemStack item = EssenceItem.create(tier, rarity, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(
                Component.text(
                                rarity.label() + " -> " + next.label(),
                                next.color(),
                                TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(
                Component.text("En stock : ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(
                                Component.text(
                                        Numbers.compact(owned) + "/" + EssenceCompression.RATIO,
                                        owned >= EssenceCompression.RATIO
                                                ? NamedTextColor.GREEN
                                                : NamedTextColor.RED)));
        lore.add(
                Component.text("Donne : ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("1 " + next.label(), next.color())));
        lore.add(Component.text(""));
        lore.add(
                Component.text(
                                "Gratuit, clique pour compresser !",
                                NamedTextColor.GREEN,
                                TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack decompressStepButton(
            Player player, MobTier tier, EssenceRarity rarity, EssenceRarity previous) {
        int owned = EssenceItem.count(player, tier, rarity);
        ItemStack item = EssenceItem.create(tier, rarity, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(
                Component.text(
                                rarity.label() + " -> " + previous.label(),
                                previous.color(),
                                TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(
                Component.text("En stock : ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(
                                Component.text(
                                        Numbers.compact(owned) + "/1",
                                        owned >= 1 ? NamedTextColor.GREEN : NamedTextColor.RED)));
        lore.add(
                Component.text("Donne : ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(
                                Component.text(
                                        EssenceCompression.RATIO + " " + previous.label(),
                                        previous.color())));
        lore.add(Component.text(""));
        lore.add(
                Component.text(
                                "Gratuit, clique pour décompresser !",
                                NamedTextColor.GREEN,
                                TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack compareSword(Player player, ItemStack offered, MobTier tier) {
        var held = player.getInventory().getItemInMainHand();
        MobTier current = null;
        if (held.hasItemMeta()) {
            String id =
                    held.getItemMeta()
                            .getPersistentDataContainer()
                            .get(TierGear.SWORD_KEY, PersistentDataType.STRING);
            if (id != null) current = MobTier.parse(id);
        }
        long before = current == null ? 0 : TierGear.pieceDamage(current);
        var starterKey = fr.cliker.clikerv2.combat.SwordCommand.SWORD_KEY;
        if (current == null
                && starterKey != null
                && held.hasItemMeta()
                && held.getItemMeta()
                        .getPersistentDataContainer()
                        .has(starterKey, PersistentDataType.BYTE)) before = 25;
        long after = TierGear.pieceDamage(tier);
        long delta = after - before;
        var meta = offered.getItemMeta();
        var lore = new ArrayList<Component>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(
                Component.text("Comparaison des dégâts de base (hors bonus)", NamedTextColor.GRAY));
        String percent =
                before == 0
                        ? ""
                        : String.format(
                                java.util.Locale.FRANCE,
                                " (%+.1f %%)",
                                (double) delta * 100 / before);
        lore.add(
                Component.text(
                        (delta >= 0 ? "+" : "") + Numbers.compact(delta) + " dégâts" + percent,
                        delta >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        meta.lore(lore);
        offered.setItemMeta(meta);
        return offered;
    }

    private ItemStack purchaseItem(ItemStack base, long ecusCost, int essenceCost, MobTier tier) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.text(""));
        lore.add(
                Component.text("Prix : ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(
                                Component.text(
                                        Numbers.compact(ecusCost) + " Écus", NamedTextColor.GOLD)));
        if (essenceCost > 0) {
            EssenceRarity required = requiredRarity(tier);
            lore.add(
                    Component.text("  + ", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                            .append(
                                    Component.text(
                                            Numbers.compact(essenceCost)
                                                    + " "
                                                    + tier.essenceName()
                                                    + " "
                                                    + required.label(),
                                            required.color())));
        }
        lore.add(
                Component.text("Clique pour acheter !", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Équivalent en essences Commune du "prix" de l'armure de ce tier : double à chaque tier
     * (Vache=2, Cochon=4, Poulet=8, ...).
     */
    private long communeEquivalent(MobTier tier) {
        return 1L << (tier.ordinal() + 1);
    }

    /**
     * Rareté d'essence exigée pour ce mob, déterminée par la valeur (64 Commune = 1 Peu Commune, 64
     * Peu Commune = 1 Rare).
     */
    private EssenceRarity requiredRarity(MobTier tier) {
        return EssenceRarity.forCommuneEquivalent(communeEquivalent(tier));
    }

    private int armorEssenceUnits(MobTier tier) {
        EssenceRarity required = requiredRarity(tier);
        long units = communeEquivalent(tier) / required.valueMultiplier();
        return (int) Math.min(units, EssenceRarity.maxItemCost(required));
    }

    private int swordEssenceUnits(MobTier tier) {
        EssenceRarity required = requiredRarity(tier);
        long units = (communeEquivalent(tier) * 2) / required.valueMultiplier();
        return (int) Math.min(units, EssenceRarity.maxItemCost(required));
    }

    /**
     * Prix Écus d'une pièce d'armure : 10, 100, 500, 1000, 5000, 10000, 50000, 100000, 500000, ...
     * (alterne x1/x5 à chaque puissance de dix croissante, doublé x2.5 pour l'épée).
     */
    private long armorEcus(MobTier tier) {
        int index = tier.ordinal() + 1;
        if (index == 1) return 10L;
        int pairIndex = index - 2;
        int power = 2 + pairIndex / 2;
        long digit = (pairIndex % 2 == 0) ? 1L : 5L;
        return digit * (long) Math.pow(10, power);
    }

    private ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    private final java.util.Set<java.util.UUID> pending = new java.util.HashSet<>();

    private void purchase(
            Player player, MobTier tier, ItemStack reward, long ecusCost, int essenceCost) {
        var delivery =
                org.bukkit.Bukkit.getServicesManager()
                        .load(fr.cliker.clikerv2.rewards.ClaimService.class);
        if (delivery == null) {
            player.sendMessage(
                    Component.text(
                            "Boutique temporairement indisponible. Tes ressources sont conservées.",
                            NamedTextColor.RED));
            return;
        }
        java.util.UUID owner = player.getUniqueId();
        if (!pending.add(owner)) return;
        EssenceRarity required = requiredRarity(tier);
        if (essenceCost > 0 && !EssenceItem.remove(player, tier, required, essenceCost)) {
            pending.remove(owner);
            player.sendMessage(
                    Component.text("Pas assez d’essences pour cet achat.", NamedTextColor.RED));
            return;
        }
        String source = "merchant:" + java.util.UUID.randomUUID();
        var purchase =
                essenceCost > 0
                        ? economy.transformItem(
                                owner,
                                player.getName(),
                                source,
                                CurrencyType.ECUS,
                                ecusCost,
                                fr.cliker.clikerv2.rewards.ClaimService.serialize(
                                        EssenceItem.create(tier, required, essenceCost)),
                                fr.cliker.clikerv2.rewards.ClaimService.serialize(reward))
                        : economy.purchaseItem(
                                owner,
                                player.getName(),
                                source,
                                CurrencyType.ECUS,
                                ecusCost,
                                fr.cliker.clikerv2.rewards.ClaimService.serialize(reward));
        purchase.whenComplete(
                (paid, error) ->
                        fr.cliker.clikerv2.runtime.ServerTasks.run(
                                plugin,
                                () -> {
                                    pending.remove(owner);
                                    if (error == null) delivery.notifyAndDeliver(owner);
                                    if (error != null || !Boolean.TRUE.equals(paid)) {
                                        if (essenceCost > 0 && error != null)
                                            delivery.give(
                                                    owner,
                                                    player.getName(),
                                                    EssenceItem.create(
                                                            tier, required, essenceCost));
                                        if (player.isOnline())
                                            player.sendMessage(
                                                    Component.text(
                                                            error == null
                                                                    ? "Écus insuffisants."
                                                                            + " Tes essences"
                                                                            + " ont été"
                                                                            + " rendues."
                                                                    : "Achat impossible."
                                                                            + " Tes essences"
                                                                            + " ont été"
                                                                            + " rendues.",
                                                            NamedTextColor.RED));
                                        if (error != null)
                                            plugin.getLogger()
                                                    .log(
                                                            java.util.logging.Level.SEVERE,
                                                            "Achat marchand" + " impossible",
                                                            error);
                                    } else {
                                        if (player.isOnline())
                                            player.sendMessage(
                                                    Component.text(
                                                            "✦ Achat effectué !"
                                                                    + " L’objet est"
                                                                    + " livré ou"
                                                                    + " disponible dans"
                                                                    + " /claim.",
                                                            NamedTextColor.GREEN));
                                    }
                                }));
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()
                || (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                        && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT)) return;

        MobTier tier = holder.tier;
        long armorEcus = armorEcus(tier);
        long swordEcus = Math.round(armorEcus * 2.5);
        int armorEssence = armorEssenceUnits(tier);
        int swordEssence = swordEssenceUnits(tier);
        int slot = event.getSlot();
        if (slot == SWORD_SLOT)
            purchase(player, tier, TierGear.createSword(tier), swordEcus, swordEssence);
        else if (slot == HELMET_SLOT)
            purchase(
                    player,
                    tier,
                    TierGear.createArmor(tier, TierGear.ArmorSlot.HELMET),
                    armorEcus,
                    armorEssence);
        else if (slot == CHESTPLATE_SLOT)
            purchase(
                    player,
                    tier,
                    TierGear.createArmor(tier, TierGear.ArmorSlot.CHESTPLATE),
                    armorEcus,
                    armorEssence);
        else if (slot == LEGGINGS_SLOT)
            purchase(
                    player,
                    tier,
                    TierGear.createArmor(tier, TierGear.ArmorSlot.LEGGINGS),
                    armorEcus,
                    armorEssence);
        else if (slot == BOOTS_SLOT)
            purchase(
                    player,
                    tier,
                    TierGear.createArmor(tier, TierGear.ArmorSlot.BOOTS),
                    armorEcus,
                    armorEssence);
        else if (slot == COMPRESSOR_SLOT)
            purchase(player, tier, CompressorItem.create(tier), armorEcus, 0);
        else if (slot == CHARM_SLOT)
            purchase(player, tier, CharmItem.create(tier), armorEcus, armorEssence);
        else {
            for (int i = 0; i < STEP_SLOTS.length; i++) {
                if (STEP_SLOTS[i] != slot) continue;
                EssenceRarity rarity = EssenceRarity.values()[i];
                EssenceRarity next = rarity.next();
                if (next == null) return;
                if (EssenceItem.remove(player, tier, rarity, EssenceCompression.RATIO)) {
                    fr.cliker.clikerv2.rewards.ItemDelivery.give(
                            player, EssenceItem.create(tier, next, 1));
                    player.sendMessage(
                            Component.text(
                                            "✦ "
                                                    + EssenceCompression.RATIO
                                                    + " "
                                                    + rarity.label()
                                                    + " ",
                                            NamedTextColor.GREEN)
                                    .append(
                                            Component.text(
                                                    "-> 1 "
                                                            + next.label()
                                                            + " "
                                                            + tier.label()
                                                            + " !",
                                                    next.color())));
                } else {
                    player.sendMessage(
                            Component.text(
                                    "Pas assez d'Essences "
                                            + rarity.label()
                                            + " "
                                            + tier.label()
                                            + ".",
                                    NamedTextColor.RED));
                }
                decorateSteps(player, holder.inventory, tier);
                return;
            }
            for (int i = 0; i < DECOMPRESS_SLOTS.length; i++) {
                if (DECOMPRESS_SLOTS[i] != slot) continue;
                EssenceRarity rarity = EssenceRarity.values()[i + 1];
                EssenceRarity previous = rarity.previous();
                if (previous == null) return;
                if (EssenceItem.remove(player, tier, rarity, 1)) {
                    fr.cliker.clikerv2.rewards.ItemDelivery.give(
                            player, EssenceItem.create(tier, previous, EssenceCompression.RATIO));
                    player.sendMessage(
                            Component.text("✦ 1 " + rarity.label() + " ", NamedTextColor.YELLOW)
                                    .append(
                                            Component.text(
                                                    "-> "
                                                            + EssenceCompression.RATIO
                                                            + " "
                                                            + previous.label()
                                                            + " "
                                                            + tier.label()
                                                            + " !",
                                                    previous.color())));
                } else {
                    player.sendMessage(
                            Component.text(
                                    "Pas d'Essence " + rarity.label() + " " + tier.label() + ".",
                                    NamedTextColor.RED));
                }
                decorateSteps(player, holder.inventory, tier);
                return;
            }
        }
    }

    private static final class Holder implements InventoryHolder {
        private final MobTier tier;
        private Inventory inventory;

        private Holder(MobTier tier) {
            this.tier = tier;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
