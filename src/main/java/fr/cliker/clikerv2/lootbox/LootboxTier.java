package fr.cliker.clikerv2.lootbox;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Color;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public enum LootboxTier {
    COMMUN(
            "commun",
            "Coffre du Nether",
            25_000,
            NamedTextColor.WHITE,
            Color.fromRGB(0xAAAAAA),
            0.05),
    PEU_COMMUN(
            "peu-commun",
            "Coffre Ardent",
            100_000,
            NamedTextColor.GREEN,
            Color.fromRGB(0x55FF55),
            0.02),
    RARE(
            "rare",
            "Coffre des Bastions",
            500_000,
            NamedTextColor.BLUE,
            Color.fromRGB(0x5555FF),
            0.008),
    EPIQUE(
            "epique",
            "Coffre Infernal",
            2_000_000,
            NamedTextColor.DARK_PURPLE,
            Color.fromRGB(0xAA00AA),
            0.003),
    LEGENDAIRE(
            "legendaire",
            "Coffre légendaire",
            4_000_000,
            NamedTextColor.GOLD,
            Color.fromRGB(0xFFAA00),
            0.0),
    MYTHIQUE(
            "mythique",
            "Coffre mythique",
            7_000_000,
            NamedTextColor.RED,
            Color.fromRGB(0xFF5555),
            0.0),
    DIVIN(
            "divin",
            "Coffre du Néant",
            10_000_000,
            NamedTextColor.LIGHT_PURPLE,
            Color.fromRGB(0xFF55FF),
            0.001);

    private static org.bukkit.configuration.file.YamlConfiguration settings;
    private static final Map<String, LootboxTier> BY_ID = new LinkedHashMap<>();

    static {
        for (LootboxTier tier : values()) BY_ID.put(tier.id, tier);
    }

    private final String id;
    private final String boxName;
    private final long ecus;
    private final NamedTextColor color;
    private final Color leatherColor;

    /** Chance de drop par kill de mob custom, identique pour les 19 mobs (en %). */
    private final double dropChancePercent;

    LootboxTier(
            String id,
            String boxName,
            long ecus,
            NamedTextColor color,
            Color leatherColor,
            double dropChancePercent) {
        this.id = id;
        this.boxName = boxName;
        this.ecus = ecus;
        this.color = color;
        this.leatherColor = leatherColor;
        this.dropChancePercent = dropChancePercent;
    }

    public String id() {
        return id;
    }

    public String boxName() {
        return boxName;
    }

    public NamedTextColor color() {
        return color;
    }

    public Color leatherColor() {
        return leatherColor;
    }

    public String rarityLabel() {
        return switch (this) {
            case COMMUN -> "Commun";
            case PEU_COMMUN -> "Peu commun";
            case RARE -> "Rare";
            case EPIQUE -> "Épique";
            case LEGENDAIRE -> "Légendaire";
            case MYTHIQUE -> "Mythique";
            case DIVIN -> "Divin";
        };
    }

    public double dropChancePercent() {
        return dropChancePercent;
    }

    public static void load(org.bukkit.plugin.java.JavaPlugin plugin) {
        java.io.File file = fr.cliker.clikerv2.config.ConfigFiles.file(plugin, "lootboxes.yml");
        if (!file.exists()) fr.cliker.clikerv2.config.ConfigFiles.install(plugin, "lootboxes.yml");
        settings = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
    }

    private long configured(String key, long fallback) {
        return settings == null ? fallback : settings.getLong(id + "." + key, fallback);
    }

    public long ecus() {
        return configured("ecus", ecus);
    }

    /** Cendres = récompense la plus rare des 4 (plus petits montants). */
    public long cendres() {
        return configured("cendres", ecus() / 250);
    }

    public long xp() {
        return configured("xp", Math.round(ecus() * 0.4));
    }

    /** Gemmes Sombres stockées en dixièmes en BDD. Valeur affichée = ecus/10. */
    public long gemmesSombresTenths() {
        return configured("gemmes-dixiemes", ecus());
    }

    public static LootboxTier parse(String input) {
        return BY_ID.get(input.toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    public static Map<String, LootboxTier> byId() {
        return BY_ID;
    }
}
