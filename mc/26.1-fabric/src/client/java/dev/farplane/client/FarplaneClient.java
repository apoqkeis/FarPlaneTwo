package dev.farplane.client;

import dev.farplane.Farplane;
import dev.farplane.config.FarplaneConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

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

        // Add FarPlane button to pause menu
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen) {
                Farplane.LOGGER.info("[FarPlane] Pause screen detected, adding button");

                Button button = Button.builder(
                        Component.literal("FarPlane"),
                        btn -> {
                            Farplane.LOGGER.info("[FarPlane] Button clicked!");
                            FarplaneConfig config = Farplane.config();
                            if (client.player != null) {
                                client.player.sendSystemMessage(Component.literal("§6§lFarPlane Settings:"));
                                client.player.sendSystemMessage(Component.literal("§7Max Levels: §f" + config.maxLevels()));
                                client.player.sendSystemMessage(Component.literal("§7Cutoff Distance: §f" + config.cutoffDistance() + " tiles"));
                                client.player.sendSystemMessage(Component.literal("§7Effective Render: §f" + config.effectiveRenderDistanceBlocks() + " blocks"));
                            }
                        }
                ).bounds(scaledWidth - 110, 10, 100, 20).build();

                // Try to add using children list
                screen.children().add(button);
                screen.renderables.add(button);
                screen.narratables.add(button);
                
                Farplane.LOGGER.info("[FarPlane] Button added to pause menu");
            }
        });

        Farplane.LOGGER.info("[FarPlane] Client initialization complete");
    }

    public static VoxelTerrainRenderer getVoxelRenderer() {
        return voxelRenderer;
    }
}
