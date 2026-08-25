package dev.farplane.client;

import dev.farplane.Farplane;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public class FarplaneClient implements ClientModInitializer {
    private static VoxelTerrainRenderer voxelRenderer;

    @Override
    public void onInitializeClient() {
        Farplane.LOGGER.info("[FarPlane] Client initializing");
        voxelRenderer = new VoxelTerrainRenderer();

        LevelRenderEvents.END_EXTRACTION.register(context -> {
            if (voxelRenderer != null) voxelRenderer.extract(context);
        });

        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            if (voxelRenderer != null) voxelRenderer.render(context);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null && voxelRenderer != null) voxelRenderer.clear();
        });

        Farplane.LOGGER.info("[FarPlane] Client initialization complete");
    }

    public static VoxelTerrainRenderer getVoxelRenderer() {
        return voxelRenderer;
    }
}
