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

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static dev.farplane.engine.EngineConstants.*;

/**
 * Main terrain renderer for FarPlane.
 * <p>
 * Phase 1-5: Debug preview, voxel tiles, async generation, biome colors, rough noise.
 * <p>
 * Note: Rendering is simplified for 26.2 compatibility.
 * The full rendering pipeline will be implemented once we understand the 26.2 API better.
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
    private static final long TRACKER_UPDATE_INTERVAL_MS = 1000; // Update tracker every 1 second

    /**
     * Called during level extraction (may touch ClientLevel).
     * Prepares data for rendering.
     */
    public void extract(LevelExtractionContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        // Initialize on first call
        if (!initialized) {
            initialize(client.level);
        }

        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();
        int viewChunks = client.options.getEffectiveRenderDistance();

        FarplaneConfig config = Farplane.config();

        // Phase 1: rebuild debug height grid if player moved significantly
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

        // Phase 3: Update tracker with player position (rate limited)
        // DISABLED for v1 - causing server overload
        // long now = System.currentTimeMillis();
        // if (tracker != null && now - lastTrackerUpdate > TRACKER_UPDATE_INTERVAL_MS) {
        //     tracker.update(px, py, pz);
        //     lastTrackerUpdate = now;
        // }

        // Phase 3: Unload distant tiles
        // DISABLED for v1
        // if (asyncGenerator != null) {
        //     int maxDistance = config.cutoffDistance() * 2;
        //     asyncGenerator.unloadDistantTiles(px, pz, maxDistance);
        // }
    }

    /**
     * Called after translucent terrain — draws our terrain.
     * <p>
     * Note: In 26.2, the rendering API has changed significantly.
     * For v1, we log tile count but don't render yet.
     * Full rendering will be implemented once we understand the 26.2 API.
     */
    public void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        // In 26.2, MultiBufferSource and RenderType have been removed/changed
        // For v1, we just log the tile count
        if (!tileMeshes.isEmpty()) {
            Farplane.LOGGER.debug("[FarPlane] {} tiles ready for rendering", tileMeshes.size());
        }
    }

    /**
     * Clears all state (called when returning to menu).
     */
    public void clear() {
        debugQuads.clear();
        tileMeshes.clear();

        if (tracker != null) {
            tracker.clear();
        }
        if (asyncGenerator != null) {
            asyncGenerator.clear();
        }

        lastPlayerX = Double.NaN;
        lastPlayerZ = Double.NaN;
        lastViewChunks = -1;
        initialized = false;
    }

    private void initialize(ClientLevel level) {
        FarplaneConfig config = Farplane.config();

        // Initialize storage
        storage = new FileTileStorage(Path.of("farplane_cache"));

        // Initialize async generator
        asyncGenerator = new AsyncTileGenerator(config, storage, level);
        asyncGenerator.setOnTileReady(pos -> {
            // When a tile is ready, bake it for rendering
            Tile tile = asyncGenerator.getCachedTile(pos);
            if (tile != null) {
                VoxelBaker.BakedMesh mesh = baker.bake(tile, pos.level(), pos);
                if (!mesh.isEmpty()) {
                    tileMeshes.put(pos, mesh);
                }
            }
        });

        // Initialize tracker
        tracker = new TileTracker(config);
        tracker.onTileLoad(pos -> {
            // Request generation when tracker says a tile should be loaded
            asyncGenerator.requestGeneration(pos, sampleSource);
        });
        tracker.onTileUnload(pos -> {
            // Remove from render cache when tracker says unload
            tileMeshes.remove(pos);
        });

        // Initialize sample source
        sampleSource = new BlockSampleSource(level);

        initialized = true;
        Farplane.LOGGER.info("[FarPlane] Voxel terrain renderer initialized with async generation");
    }

    // ========== Phase 1: Debug Height Grid ==========

    private void rebuildDebugGrid(ClientLevel level, double px, double pz, int viewChunks, FarplaneConfig config) {
        debugQuads.clear();

        int extra = config.debugPreviewExtraChunks();
        int step = config.debugGridStep();
        int minBlock = (viewChunks) * 16;
        int maxBlock = (viewChunks + extra) * 16;

        int seaLevel = level.getSeaLevel();

        for (int dist = minBlock; dist < maxBlock; dist += step) {
            for (int angle = 0; angle < 360; angle += 3) {
                double rad = Math.toRadians(angle);
                int bx = (int) (px + Math.cos(rad) * dist);
                int bz = (int) (pz + Math.sin(rad) * dist);

                int chunkX = bx >> 4;
                int chunkZ = bz >> 4;

                var chunk = level.getChunk(chunkX, chunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);

                float r, g, b;
                int height;

                if (chunk != null) {
                    height = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, bx & 15, bz & 15);

                    var pos = new net.minecraft.core.BlockPos(bx, height - 1, bz);
                    var biome = level.getBiome(pos);
                    int grassColor = biome.value().getGrassColor(bx, bz);
                    r = ((grassColor >> 16) & 0xFF) / 255f;
                    g = ((grassColor >> 8) & 0xFF) / 255f;
                    b = (grassColor & 0xFF) / 255f;
                } else {
                    // Unloaded: placeholder noise
                    height = seaLevel + (int) (Math.sin(bx * 0.01) * Math.cos(bz * 0.01) * 8);
                    r = 0.25f; g = 0.55f; b = 0.85f; // sea color
                    if (height > seaLevel) {
                        r = 0.4f; g = 0.6f; b = 0.3f; // grass placeholder
                    }
                }

                float alpha = 0.55f;
                float y = height;
                float s = step * 0.5f;

                // Add a small quad at this position
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
}
