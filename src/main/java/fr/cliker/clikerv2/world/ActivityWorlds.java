package fr.cliker.clikerv2.world;

import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/** Génération sans scan de monde : seuls les chunks demandés sont générés. */
public final class ActivityWorlds {
    private ActivityWorlds() {}

    public static World create(String name, boolean arena) {
        World world = Bukkit.getWorld(name);
        if (world == null)
            world =
                    new WorldCreator(name)
                            .keepSpawnLoaded(net.kyori.adventure.util.TriState.FALSE)
                            .generator(new Generator(arena))
                            .generateStructures(false)
                            .createWorld();
        if (world == null)
            throw new IllegalStateException("Création du monde impossible : " + name);
        world.setPVP(false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(6000);
        world.setSpawnLocation(0, 65, 0);
        return world;
    }

    public static final class Generator extends ChunkGenerator {
        private final boolean arena;

        public Generator(boolean arena) {
            this.arena = arena;
        }

        @Override
        public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData data) {
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    int wx = cx * 16 + x, wz = cz * 16 + z;
                    boolean floor =
                            arena
                                    ? Math.abs(wx) <= 32 && Math.abs(wz) <= 32
                                    : Math.abs(Math.floorMod(wx + 128, 256) - 128) <= 10
                                            && Math.abs(Math.floorMod(wz + 128, 256) - 128) <= 10;
                    if (floor) {
                        data.setBlock(x, 62, z, Material.BEDROCK);
                        data.setBlock(x, 63, z, Material.STONE);
                        data.setBlock(
                                x,
                                64,
                                z,
                                arena ? Material.POLISHED_DEEPSLATE : Material.GRASS_BLOCK);
                    }
                    if (arena
                            && Math.abs(wx) <= 32
                            && Math.abs(wz) <= 32
                            && (Math.abs(wx) == 32 || Math.abs(wz) == 32))
                        data.setRegion(x, 65, z, x + 1, 72, z + 1, Material.BARRIER);
                }
        }

        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }
}
