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
import dev.farplane.client.gui.FarplaneConfigScreen;
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
                Farplane.LOGGER.info("[FarPlane] Pause screen detected, adding button!");

                Button button = Button.builder(
                        Component.literal("FarPlane"),
                        btn -> {
                            Farplane.LOGGER.info("[FarPlane] Button clicked! Opening config screen...");
                            client.setScreen(new FarplaneConfigScreen(screen));
                        }
                ).bounds(scaledWidth - 110, 10, 100, 20).build();

                screen.addRenderableWidget(button);
                Farplane.LOGGER.info("[FarPlane] Button added to pause menu!");
            }
        });

        Farplane.LOGGER.info("[FarPlane] Client initialization complete");
    }

    public static VoxelTerrainRenderer getVoxelRenderer() {
        return voxelRenderer;
    }
}
