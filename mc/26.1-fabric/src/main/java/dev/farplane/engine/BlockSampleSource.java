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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;

import static dev.farplane.engine.EngineConstants.*;

/**
 * Samples block data from a 26.2 {@link Level} for use by the exact voxel generator.
 * <p>
 * This reads from already-loaded chunks only — it does NOT trigger chunk generation.
 * Unloaded regions return air/invisible.
 *
 * @author FarPlane contributors
 */
public class BlockSampleSource {
    private final Level level;

    public BlockSampleSource(Level level) {
        this.level = level;
    }

    /**
     * Fills the state, biome, and light caches for a padded cube around a tile.
     * Coordinates are world-block coordinates.
     */
    public void multiGetDense(int minBlockX, int minBlockY, int minBlockZ,
                              int sizeX, int sizeY, int sizeZ,
                              int[] stateOut, int[] biomeOut, byte[] lightOut) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Use reasonable defaults for height bounds
        int minBuildHeight = -64;  // 26.2 default
        int maxBuildHeight = 320;  // 26.2 default

        int idx = 0;
        for (int dx = 0; dx < sizeX; dx++) {
            int worldX = minBlockX + dx;
            int chunkX = worldX >> 4;
            int localX = worldX & 0xF;

            for (int dy = 0; dy < sizeY; dy++) {
                int worldY = minBlockY + dy;

                for (int dz = 0; dz < sizeZ; dz++) {
                    int worldZ = minBlockZ + dz;
                    int chunkZ = worldZ >> 4;
                    int localZ = worldZ & 0xF;

                    // Try to get the loaded chunk — do NOT generate
                    ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);

                    if (chunk != null && worldY >= minBuildHeight && worldY < maxBuildHeight) {
                        pos.set(worldX, worldY, worldZ);

                        BlockState blockState = chunk.getBlockState(pos);
                        stateOut[idx] = Block.getId(blockState);

                        Holder<Biome> biomeHolder = chunk.getNoiseBiome(
                                localX >> 2, worldY >> 2, localZ >> 2);
                        biomeOut[idx] = biomeHolder.hashCode(); // placeholder

                        // Simple light estimation for v1
                        // Full implementation would use LevelLightEngine
                        int skyLight = worldY > level.getSeaLevel() ? 15 : 0;
                        int blockLight = 0;
                        lightOut[idx] = (byte) packLight(skyLight, blockLight);
                    } else {
                        // Unloaded: air, no biome, full darkness
                        stateOut[idx] = 0;
                        biomeOut[idx] = 0;
                        lightOut[idx] = 0;
                    }
                    idx++;
                }
            }
        }
    }

    /**
     * Returns the block type classification for a given state ID.
     */
    public static int typeOf(int stateId) {
        if (stateId == 0) return BLOCK_TYPE_INVISIBLE; // air

        BlockState state = Block.stateById(stateId);
        if (state.isAir()) return BLOCK_TYPE_INVISIBLE;

        // Check if the block is a fluid
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty()) return BLOCK_TYPE_TRANSPARENT;

        // Check render shape
        if (state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE) {
            return BLOCK_TYPE_INVISIBLE;
        }

        // Check if the block is transparent
        if (state.canOcclude()) {
            return BLOCK_TYPE_OPAQUE;
        }

        return BLOCK_TYPE_TRANSPARENT;
    }
}
