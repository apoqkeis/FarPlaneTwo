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
 * Phase 1-3: Tile generation and tracking (rendering disabled for 26.2 compat).
 *
 * @author FarPlane contributors
 */
public class VoxelTerrainRenderer {
    private final VoxelBaker baker = new VoxelBaker();

    // --- Voxel tiles ---
    private final Map<TilePos, VoxelBaker.BakedMesh> tileMeshes = new ConcurrentHashMap<>();
    private TileStorage storage;
    private AsyncTileGenerator asyncGenerator;
    private TileTracker tracker;
    private BlockSampleSource sampleSource;
    private boolean initialized = false;
    private long lastTrackerUpdate = 0;
    private long lastLogTime = 0;
    private static final long TRACKER_UPDATE_INTERVAL_MS = 1000;
    private static final long LOG_INTERVAL_MS = 10000; // Log every 10 seconds

    public void extract(LevelExtractionContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        if (!initialized) {
            initialize(client.level);
        }

        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        FarplaneConfig config = Farplane.config();

        // Update tracker (rate limited)
        long now = System.currentTimeMillis();
        if (tracker != null && now - lastTrackerUpdate > TRACKER_UPDATE_INTERVAL_MS) {
            tracker.update(px, py, pz);
            lastTrackerUpdate = now;
        }

        // Unload distant tiles
        if (asyncGenerator != null) {
            int maxDistance = config.cutoffDistance() * 2;
            asyncGenerator.unloadDistantTiles(px, pz, maxDistance);
        }

        // Log status periodically
        if (now - lastLogTime > LOG_INTERVAL_MS) {
            Farplane.LOGGER.info("[FarPlane] Tiles: {} loaded, {} generating",
                    tileMeshes.size(),
                    asyncGenerator != null ? "active" : "none");
            lastLogTime = now;
        }
    }

    public void render(LevelRenderContext context) {
        // Rendering disabled for 26.2 compat
        // Tiles are generated and tracked but not drawn yet
        // TODO: Implement 26.2 rendering with Blaze3D API
    }

    public void clear() {
        tileMeshes.clear();
        if (tracker != null) tracker.clear();
        if (asyncGenerator != null) asyncGenerator.clear();
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
        Farplane.LOGGER.info("[FarPlane] Voxel terrain renderer initialized (generation only, rendering TODO)");
    }
}
