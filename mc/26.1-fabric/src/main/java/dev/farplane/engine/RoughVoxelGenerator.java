/*
 * Adapted from The MIT License (MIT)
 *
 * Copyright (c) 2020-2026 DaPorkchop_
 * Portions Copyright (c) 2026 FarPlane contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * Any persons and/or organizations using this software must include the above copyright notice and this permission notice,
 * provide sufficient credit to the original authors of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package dev.farplane.engine;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

import static dev.farplane.engine.EngineConstants.*;

/**
 * Rough voxel generator — generates tiles from noise without loading chunks.
 * <p>
 * v1 uses simple Perlin-like noise for terrain shape.
 * Phase 5+ will integrate with Minecraft's NoiseRouter for accurate terrain.
 *
 * @author DaPorkchop_ (original algorithm)
 * @author FarPlane contributors (26.2 adaptation)
 */
public class RoughVoxelGenerator {
    private final Level level;
    private final long worldSeed;

    public RoughVoxelGenerator(Level level) {
        this.level = level;
        // In 26.2, getSeed() may be renamed or removed
        // Use a default seed based on level hash for v1
        this.worldSeed = level.hashCode() * 31L;
    }

    /**
     * Generates a rough tile at the given position using noise sampling.
     */
    public boolean generate(TilePos pos, Tile tile) {
        int lodLevel = pos.level();
        int scale = 1 << lodLevel;

        int minBX = pos.minBlockX();
        int minBY = pos.minBlockY();
        int minBZ = pos.minBlockZ();

        TileData data = new TileData();

        for (int dx = 0; dx < T_VOXELS; dx++) {
            for (int dy = 0; dy < T_VOXELS; dy++) {
                for (int dz = 0; dz < T_VOXELS; dz++) {
                    int worldX = minBX + (dx << lodLevel);
                    int worldY = minBY + (dy << lodLevel);
                    int worldZ = minBZ + (dz << lodLevel);

                    // Sample density at this position
                    double density = sampleDensity(worldX, worldY, worldZ);

                    // Check if this is a surface voxel
                    double densityAbove = sampleDensity(worldX, worldY + scale, worldZ);

                    boolean isSolid = density > 0;
                    boolean isSolidAbove = densityAbove > 0;

                    // Only create voxel if this is a surface transition
                    if (isSolid == isSolidAbove) {
                        continue; // No surface here
                    }

                    // Determine edges based on density transitions
                    int edges = 0;
                    double densityX = sampleDensity(worldX + scale, worldY, worldZ);
                    double densityY = sampleDensity(worldX, worldY + scale, worldZ);
                    double densityZ = sampleDensity(worldX, worldY, worldZ + scale);

                    // X edge
                    if ((density > 0) != (densityX > 0)) {
                        edges |= (density > 0) ? EDGE_DIR_POSITIVE : EDGE_DIR_NEGATIVE;
                    }

                    // Y edge
                    if ((density > 0) != (densityY > 0)) {
                        edges |= ((density > 0) ? EDGE_DIR_POSITIVE : EDGE_DIR_NEGATIVE) << 2;
                    }

                    // Z edge
                    if ((density > 0) != (densityZ > 0)) {
                        edges |= ((density > 0) ? EDGE_DIR_POSITIVE : EDGE_DIR_NEGATIVE) << 4;
                    }

                    if (edges == 0) {
                        // Default: assume Y-facing surface
                        edges = isSolid ? (EDGE_DIR_POSITIVE << 2) : (EDGE_DIR_NEGATIVE << 2);
                    }

                    data.edges = edges;

                    // Set block states
                    int stateId = getBlockStateForPosition(worldX, worldY, worldZ);
                    data.states[0] = stateId;
                    data.states[1] = stateId;
                    data.states[2] = stateId;

                    // Vertex position (center of cell)
                    data.x = POS_ONE >> 1;
                    data.y = POS_ONE >> 1;
                    data.z = POS_ONE >> 1;

                    // Biome (simple hash)
                    data.biome = getBiomeHash(worldX, worldY, worldZ);

                    // Lighting (sky=15, block=0 for rough gen)
                    data.light = (byte) packLight(15, 0);

                    tile.set(dx, dy, dz, data);
                }
            }
        }

        return !tile.isEmpty();
    }

    /**
     * Samples terrain density at a world position.
     * Positive = solid, negative = air.
     * Uses simple multi-octave noise for terrain shape.
     */
    private double sampleDensity(int x, int y, int z) {
        int seaLevel = level.getSeaLevel();

        // Base density from height (negative above sea level, positive below)
        double heightDensity = (seaLevel - y) / 64.0;

        // Add noise for terrain variation
        double noise = 0;
        noise += noise2D(x * 0.01, z * 0.01, 1.0) * 0.5;
        noise += noise2D(x * 0.02, z * 0.02, 2.0) * 0.25;
        noise += noise2D(x * 0.04, z * 0.04, 3.0) * 0.125;

        // Scale noise by height (more variation at surface)
        double heightFactor = Math.max(0, 1.0 - Math.abs(y - seaLevel) / 128.0);
        noise *= heightFactor;

        return heightDensity + noise;
    }

    /**
     * Simple 2D noise function (Perlin-like).
     */
    private double noise2D(double x, double z, double seed) {
        // Simple hash-based noise
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double fx = x - ix;
        double fz = z - iz;

        // Smoothstep
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);

        // Hash corners
        double n00 = hash2D(ix, iz, seed);
        double n10 = hash2D(ix + 1, iz, seed);
        double n01 = hash2D(ix, iz + 1, seed);
        double n11 = hash2D(ix + 1, iz + 1, seed);

        // Bilinear interpolation
        double nx0 = n00 * (1 - fx) + n10 * fx;
        double nx1 = n01 * (1 - fx) + n11 * fx;

        return nx0 * (1 - fz) + nx1 * fz;
    }

    /**
     * Hash function for noise generation.
     */
    private double hash2D(int x, int z, double seed) {
        long h = worldSeed;
        h = h * 374761393L + x * 668265263L;
        h = h * 374761393L + z * 668265263L;
        h = h * 374761393L + (long) (seed * 1000000);
        h = (h ^ (h >> 13)) * 1274126177L;
        h = h ^ (h >> 16);
        return (h & 0xFFFF) / (double) 0xFFFF * 2.0 - 1.0;
    }

    /**
     * Gets a block state ID for a position based on terrain type.
     */
    private int getBlockStateForPosition(int x, int y, int z) {
        int seaLevel = level.getSeaLevel();

        BlockState state;
        if (y < seaLevel - 10) {
            state = Blocks.STONE.defaultBlockState();
        } else if (y < seaLevel) {
            state = Blocks.GRAVEL.defaultBlockState();
        } else if (y == seaLevel) {
            state = Blocks.GRASS_BLOCK.defaultBlockState();
        } else if (y < seaLevel + 4) {
            state = Blocks.DIRT.defaultBlockState();
        } else {
            state = Blocks.STONE.defaultBlockState();
        }

        // In 26.2, use Block.getId(BlockState)
        return Block.getId(state);
    }

    /**
     * Gets a biome hash for a position.
     */
    private int getBiomeHash(int x, int y, int z) {
        return ((x * 1317194159 + y * 1964379643 + z * 1656858407) & 0xFF);
    }

    private static int packLight(int skyLight, int blockLight) {
        return ((skyLight & 0xF) << 4) | (blockLight & 0xF);
    }
}
