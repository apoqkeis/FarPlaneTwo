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

package dev.farplane.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.farplane.Farplane;
import dev.farplane.config.FarplaneConfig;
import dev.farplane.engine.*;
import dev.farplane.engine.storage.FileTileStorage;
import dev.farplane.engine.storage.TileStorage;
import dev.farplane.engine.tracking.TileTracker;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static dev.farplane.engine.EngineConstants.*;

/**
 * Main terrain renderer for FarPlane.
 *
 * @author FarPlane contributors
 */
public class VoxelTerrainRenderer {
    private final VoxelBaker baker = new VoxelBaker();

    // --- Phase 1: Debug height grid ---
    private final List<float[]> debugQuads = new ArrayList<>();
    private double lastPlayerX = Double.NaN;
    private double lastPlayerZ = Double.NaN;
    private int lastViewChunks = -1;

    // --- Phase 2 & 3: Voxel tiles ---
    private final Map<TilePos, VoxelBaker.BakedMesh> tileMeshes = new ConcurrentHashMap<>();
    private TileStorage storage;
    private AsyncTileGenerator asyncGenerator;
    private TileTracker tracker;
    private BlockSampleSource sampleSource;
    private boolean initialized = false;
    private long lastTrackerUpdate = 0;
    private static final long TRACKER_UPDATE_INTERVAL_MS = 1000;

    public void extract(LevelExtractionContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        if (!initialized) {
            initialize(client.level);
        }

        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();
        int viewChunks = client.options.getEffectiveRenderDistance();

        FarplaneConfig config = Farplane.config();

        // Phase 1: rebuild debug height grid
        if (config.debugPreview()) {
            double dx = px - lastPlayerX;
            double dz = pz - lastPlayerZ;
            if (Double.isNaN(lastPlayerX) || dx * dx + dz * dz > 256 || viewChunks != lastViewChunks) {
                rebuildDebugGrid(client.level, px, pz, viewChunks, config);
                lastPlayerX = px;
                lastPlayerZ = pz;
                lastViewChunks = viewChunks;
            }
        }

        // Phase 3: Update tracker (rate limited)
        long now = System.currentTimeMillis();
        if (tracker != null && now - lastTrackerUpdate > TRACKER_UPDATE_INTERVAL_MS) {
            tracker.update(px, py, pz);
            lastTrackerUpdate = now;
        }

        // Phase 3: Unload distant tiles
        if (asyncGenerator != null) {
            int maxDistance = config.cutoffDistance() * 2;
            asyncGenerator.unloadDistantTiles(px, pz, maxDistance);
        }
    }

    public void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        double camX = context.levelState().cameraRenderState().pos().x;
        double camY = context.levelState().cameraRenderState().pos().y;
        double camZ = context.levelState().cameraRenderState().pos().z;

        PoseStack poseStack = context.poseStack();

        FarplaneConfig config = Farplane.config();

        // Set up render state for custom rendering
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        // Phase 1: render debug height grid
        if (config.debugPreview()) {
            renderDebugGrid(poseStack, camX, camY, camZ);
        }

        // Phase 2 & 3: render voxel tiles
        renderVoxelTiles(poseStack, camX, camY, camZ);

        // Restore render state
        RenderSystem.disableBlend();
    }

    public void clear() {
        debugQuads.clear();
        tileMeshes.clear();

        if (tracker != null) tracker.clear();
        if (asyncGenerator != null) asyncGenerator.clear();

        lastPlayerX = Double.NaN;
        lastPlayerZ = Double.NaN;
        lastViewChunks = -1;
        initialized = false;
    }

    private void initialize(ClientLevel level) {
        FarplaneConfig config = Farplane.config();

        storage = new FileTileStorage(Path.of("farplane_cache"));

        asyncGenerator = new AsyncTileGenerator(config, storage, level);
        asyncGenerator.setOnTileReady(pos -> {
            Tile tile = asyncGenerator.getCachedTile(pos);
            if (tile != null) {
                VoxelBaker.BakedMesh mesh = baker.bake(tile, pos.level(), pos);
                if (!mesh.isEmpty()) {
                    tileMeshes.put(pos, mesh);
                }
            }
        });

        tracker = new TileTracker(config);
        tracker.onTileLoad(pos -> asyncGenerator.requestGeneration(pos, sampleSource));
        tracker.onTileUnload(pos -> tileMeshes.remove(pos));

        sampleSource = new BlockSampleSource(level);

        initialized = true;
        Farplane.LOGGER.info("[FarPlane] Voxel terrain renderer initialized");
    }

    // ========== Phase 1: Debug Height Grid ==========

    private void rebuildDebugGrid(ClientLevel level, double px, double pz, int viewChunks, FarplaneConfig config) {
        debugQuads.clear();

        int extra = config.debugPreviewExtraChunks();
        int step = config.debugGridStep();
        int minBlock = viewChunks * 16;
        int maxBlock = (viewChunks + extra) * 16;
        int seaLevel = level.getSeaLevel();

        for (int dist = minBlock; dist < maxBlock; dist += step) {
            for (int angle = 0; angle < 360; angle += 3) {
                double rad = Math.toRadians(angle);
                int bx = (int) (px + Math.cos(rad) * dist);
                int bz = (int) (pz + Math.sin(rad) * dist);

                ChunkAccess chunk = level.getChunk(bx >> 4, bz >> 4, ChunkStatus.FULL, false);

                float r, g, b;
                int height;

                if (chunk != null) {
                    height = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, bx & 15, bz & 15);
                    BlockPos pos = new BlockPos(bx, height - 1, bz);
                    Holder<Biome> biome = level.getBiome(pos);
                    int grassColor = biome.value().getGrassColor(bx, bz);
                    r = ((grassColor >> 16) & 0xFF) / 255f;
                    g = ((grassColor >> 8) & 0xFF) / 255f;
                    b = (grassColor & 0xFF) / 255f;
                } else {
                    height = seaLevel + (int) (Math.sin(bx * 0.01) * Math.cos(bz * 0.01) * 8);
                    r = 0.25f; g = 0.55f; b = 0.85f;
                    if (height > seaLevel) {
                        r = 0.4f; g = 0.6f; b = 0.3f;
                    }
                }

                float alpha = 0.55f;
                float y = height;
                float s = step * 0.5f;

                debugQuads.add(new float[]{
                        bx - s, y, bz - s, r, g, b, alpha,
                        bx + s, y, bz - s, r, g, b, alpha,
                        bx + s, y, bz + s, r, g, b, alpha,
                        bx - s, y, bz - s, r, g, b, alpha,
                        bx + s, y, bz + s, r, g, b, alpha,
                        bx - s, y, bz + s, r, g, b, alpha,
                });
            }
        }
    }

    private void renderDebugGrid(PoseStack poseStack, double camX, double camY, double camZ) {
        if (debugQuads.isEmpty()) return;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        for (float[] quad : debugQuads) {
            for (int v = 0; v < 6; v++) {
                int off = v * 7;
                buffer.addVertex(matrix,
                                quad[off] - (float) camX,
                                quad[off + 1] - (float) camY,
                                quad[off + 2] - (float) camZ)
                        .setColor(quad[off + 3], quad[off + 4], quad[off + 5], quad[off + 6]);
            }
        }

        tesselator.end();
    }

    // ========== Phase 2 & 3: Voxel Tile Rendering ==========

    private void renderVoxelTiles(PoseStack poseStack, double camX, double camY, double camZ) {
        if (tileMeshes.isEmpty()) return;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Sort tiles by distance (near first)
        List<Map.Entry<TilePos, VoxelBaker.BakedMesh>> sorted = new ArrayList<>(tileMeshes.entrySet());
        sorted.sort(Comparator.comparingInt(e -> {
            TilePos p = e.getKey();
            int dx = p.minBlockX() + 8 - (int) camX;
            int dz = p.minBlockZ() + 8 - (int) camZ;
            return dx * dx + dz * dz;
        }));

        for (var entry : sorted) {
            VoxelBaker.renderMesh(entry.getValue(), poseStack, camX, camY, camZ);
        }
    }
}
