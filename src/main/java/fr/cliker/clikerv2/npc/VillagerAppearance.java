package fr.cliker.clikerv2.npc;

import java.util.List;

/** Tirage déterministe par identité de PNJ : apparences variées, stables après redémarrage. */
public final class VillagerAppearance {
    private VillagerAppearance() {}

    private static final List<String> BIOMES =
            List.of("DESERT", "JUNGLE", "PLAINS", "SAVANNA", "SNOW", "SWAMP", "TAIGA");
    private static final List<String> JOBS =
            List.of(
                    "ARMORER",
                    "BUTCHER",
                    "CARTOGRAPHER",
                    "CLERIC",
                    "FARMER",
                    "FISHERMAN",
                    "FLETCHER",
                    "LEATHER_WORKER",
                    "LIBRARIAN",
                    "MASON",
                    "SHEPHERD",
                    "TOOL_SMITH",
                    "WEAPON_SMITH");

    public static String biome(String id) {
        return BIOMES.get(Math.floorMod(id.hashCode(), BIOMES.size()));
    }

    public static String profession(String id) {
        return JOBS.get(Math.floorMod((id + ":profession").hashCode(), JOBS.size()));
    }
}
