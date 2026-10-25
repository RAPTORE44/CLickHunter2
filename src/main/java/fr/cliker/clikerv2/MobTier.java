package fr.cliker.clikerv2;

import org.bukkit.entity.EntityType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public enum MobTier {
    VACHE("vache", EntityType.COW, "Vache", 100),
    COCHON("cochon", EntityType.PIG, "Cochon", 250),
    POULET("poulet", EntityType.CHICKEN, "Poulet", 500),
    MOUTON("mouton", EntityType.SHEEP, "Mouton", 1_000),
    LAPIN("lapin", EntityType.RABBIT, "Lapin", 2_500),
    OURSE("ourse", EntityType.POLAR_BEAR, "Ourse", 5_000),
    ANE("ane", EntityType.DONKEY, "Âne", 10_000),
    PIGMAN("pigman", EntityType.ZOMBIFIED_PIGLIN, "Pigman", 25_000),
    PIGLIN("piglin", EntityType.PIGLIN, "Piglin", 50_000),
    PIGLIN_BRUTE("piglin_brute", EntityType.PIGLIN_BRUTE, "Piglin Brute", 100_000),
    HOGLIN("hoglin", EntityType.HOGLIN, "Hoglin", 250_000),
    MAGMA_SLIME("magma_slime", EntityType.MAGMA_CUBE, "Magma Slime", 500_000),
    BLAZE("blaze", EntityType.BLAZE, "Blaze", 1_000_000),
    WITHER_SKELETON("wither_skeleton", EntityType.WITHER_SKELETON, "Wither Squelette", 2_500_000),
    GHAST("ghast", EntityType.GHAST, "Ghast", 5_000_000),
    VINDICATOR("vindicator", EntityType.VINDICATOR, "Vindicateur", 10_000_000),
    RAVAGER("ravager", EntityType.RAVAGER, "Ravageur", 25_000_000),
    WITHER("wither", EntityType.WITHER, "Wither", 50_000_000),
    WARDEN("warden", EntityType.WARDEN, "Warden", 100_000_000);

    private static final Map<String, MobTier> BY_ID = new LinkedHashMap<>();

    static {
        for (MobTier tier : values()) BY_ID.put(tier.id, tier);
    }

    private final String id;
    private final EntityType entityType;
    private final String label;
    private final double maxHealth;

    MobTier(String id, EntityType entityType, String label, double maxHealth) {
        this.id = id;
        this.entityType = entityType;
        this.label = label;
        this.maxHealth = maxHealth;
    }

    public String id() {
        return id;
    }

    public EntityType entityType() {
        return entityType;
    }

    public String label() {
        return fr.cliker.clikerv2.features.CreatureSettings.text(this, "nom", label);
    }

    public double maxHealth() {
        return fr.cliker.clikerv2.features.CreatureSettings.value(this, "pv", maxHealth);
    }

    public String essenceName() {
        return label() + " Essence";
    }

    public long sellPricePerUnit() {
        return Math.round(
                fr.cliker.clikerv2.features.CreatureSettings.value(
                        this, "prix-vente", (ordinal() + 1) * 50L));
    }

    /**
     * Bonus de dégâts fixe de l'amulette de ce mob : 2.5, double à chaque tier (Vache 2.5, Cochon
     * 5, Poulet 10, Mouton 20, Lapin 40, ...).
     */
    public double charmDamageBonus() {
        return fr.cliker.clikerv2.features.CreatureSettings.value(
                this, "degats-amulette", 2.5 * Math.pow(2, ordinal()));
    }

    public static MobTier parse(String input) {
        return BY_ID.get(input.toLowerCase(Locale.ROOT).replace(' ', '_'));
    }

    public static Map<String, MobTier> byId() {
        return BY_ID;
    }
}
