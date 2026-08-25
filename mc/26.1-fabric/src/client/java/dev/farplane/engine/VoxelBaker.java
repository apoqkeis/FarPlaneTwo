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

import dev.farplane.client.render.BiomeColorProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static dev.farplane.engine.EngineConstants.*;

/**
 * Bakes voxel tiles into renderable quad geometry.
 * Rendering disabled for 26.2 compat — only generates mesh data.
 *
 * @author DaPorkchop_ (original algorithm)
 */
public class VoxelBaker {

    /**
     * A baked mesh ready for rendering.
     */
    public record BakedMesh(List<float[]> quads, int level) {
        public boolean isEmpty() { return quads.isEmpty(); }
    }

    /**
     * Bakes a tile into a renderable mesh.
     */
    public BakedMesh bake(Tile tile, int level, TilePos pos) {
        if (tile == null || tile.isEmpty()) {
            return new BakedMesh(List.of(), level);
        }

        ClientLevel clientLevel = Minecraft.getInstance().level;
        List<float[]> quads = new ArrayList<>();
        TileData data = new TileData();

        float scale = 1 << level;
        float baseX = pos.minBlockX();
        float baseY = pos.minBlockY();
        float baseZ = pos.minBlockZ();

        for (int i = 0; i < tile.count(); i++) {
            int cellPos = tile.get(i, data);
            if (cellPos < 0) continue;

            int cx = (cellPos >> (T_SHIFT << 1)) & T_MASK;
            int cy = (cellPos >> T_SHIFT) & T_MASK;
            int cz = cellPos & T_MASK;

            int edges = data.edges;
            if ((((edges >> 2) ^ (edges >> 3)) & 1) != 0) {
                edges ^= EDGE_DIR_MASK << 2;
            }

            float[] baseColor = getBlockColorWithTint(clientLevel, pos, cx, cy, cz, data);

            for (int edge = 0; edge < EDGE_COUNT; edge++) {
                int edgeDir = (edges >> (edge << 1)) & EDGE_DIR_MASK;
                if (edgeDir == EDGE_DIR_NONE) continue;

                float[] verts = new float[12];
                for (int ci = 0; ci < CONNECTION_INDEX_COUNT; ci++) {
                    int j = CONNECTION_INDICES[edge * CONNECTION_INDEX_COUNT + ci];
                    verts[ci * 3 + 0] = baseX + (cx + ((j >> 2) & 1)) * scale;
                    verts[ci * 3 + 1] = baseY + (cy + ((j >> 1) & 1)) * scale;
                    verts[ci * 3 + 2] = baseZ + (cz + (j & 1)) * scale;
                }

                float[] shadedColor = applyDirectionalShading(baseColor, edge);

                if ((edgeDir & EDGE_DIR_NEGATIVE) != 0) addQuad(quads, verts, shadedColor, true);
                if ((edgeDir & EDGE_DIR_POSITIVE) != 0) addQuad(quads, verts, shadedColor, false);
            }
        }

        return new BakedMesh(quads, level);
    }

    private float[] getBlockColorWithTint(ClientLevel level, TilePos pos, int cx, int cy, int cz, TileData data) {
        if (level == null) return new float[]{0.5f, 0.5f, 0.5f};

        int stateId = 0;
        for (int edge = 0; edge < EDGE_COUNT; edge++) {
            if (data.states[edge] != 0) { stateId = data.states[edge]; break; }
        }
        if (stateId == 0) return new float[]{0.5f, 0.5f, 0.5f};

        BlockState state = Block.stateById(stateId);
        BlockPos blockPos = new BlockPos(pos.minBlockX() + cx, pos.minBlockY() + cy, pos.minBlockZ() + cz);

        float[] baseColor = BiomeColorProvider.getBlockColor(state);
        float[] tintColor = BiomeColorProvider.getTintColor(level, blockPos, state, data.biome);

        return new float[]{ baseColor[0] * tintColor[0], baseColor[1] * tintColor[1], baseColor[2] * tintColor[2] };
    }

    private float[] applyDirectionalShading(float[] color, int edge) {
        float shade = switch (edge) {
            case 0 -> 0.8f; case 1 -> 1.0f; case 2 -> 0.7f; default -> 0.9f;
        };
        return new float[]{ color[0] * shade, color[1] * shade, color[2] * shade };
    }

    private void addQuad(List<float[]> quads, float[] verts, float[] color, boolean flip) {
        float r = color[0], g = color[1], b = color[2], a = 1.0f;
        if (flip) {
            quads.add(new float[]{
                    verts[0], verts[1], verts[2], r, g, b, a, verts[6], verts[7], verts[8], r, g, b, a, verts[3], verts[4], verts[5], r, g, b, a,
                    verts[0], verts[1], verts[2], r, g, b, a, verts[9], verts[10], verts[11], r, g, b, a, verts[6], verts[7], verts[8], r, g, b, a,
            });
        } else {
            quads.add(new float[]{
                    verts[0], verts[1], verts[2], r, g, b, a, verts[3], verts[4], verts[5], r, g, b, a, verts[6], verts[7], verts[8], r, g, b, a,
                    verts[0], verts[1], verts[2], r, g, b, a, verts[6], verts[7], verts[8], r, g, b, a, verts[9], verts[10], verts[11], r, g, b, a,
            });
        }
    }
}
