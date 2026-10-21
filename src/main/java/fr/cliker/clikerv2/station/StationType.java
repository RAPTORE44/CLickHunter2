package fr.cliker.clikerv2.station;

import org.bukkit.Material;

/** Type de bloc posé dans le monde : clic avec une épée/armure de tier en main ouvre le menu correspondant, item préchargé. */
public enum StationType {
    ENCHANT(Material.ENCHANTING_TABLE, "Table d'Enchantement"),
    REFORGE(Material.ANVIL, "Enclume de Reforge"),
    RUNE(Material.LODESTONE, "Table de Runes");

    private final Material material;
    private final String label;

    StationType(Material material, String label) {
        this.material = material;
        this.label = label;
    }

    public Material material() { return material; }
    public String label() { return label; }
}
