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

package dev.farplane.client.gui;

import dev.farplane.Farplane;
import dev.farplane.config.FarplaneConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Simple config screen for FarPlane.
 * <p>
 * Note: In 26.2, the Screen API has changed significantly.
 * This is a minimal implementation that logs config to chat.
 *
 * @author FarPlane contributors
 */
public class FarplaneConfigScreen {
    private final Screen parent;

    public FarplaneConfigScreen(Screen parent) {
        this.parent = parent;
    }

    /**
     * Opens the config screen.
     * In 26.2, we log config to chat instead of opening a GUI.
     */
    public void open() {
        FarplaneConfig config = Farplane.config();
        Minecraft client = Minecraft.getInstance();

        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("§6§lFarPlane Settings:"));
            client.player.sendSystemMessage(Component.literal("§7Debug Preview: §f" + (config.debugPreview() ? "ON" : "OFF")));
            client.player.sendSystemMessage(Component.literal("§7Max Levels: §f" + config.maxLevels()));
            client.player.sendSystemMessage(Component.literal("§7Cutoff Distance: §f" + config.cutoffDistance() + " tiles"));
            client.player.sendSystemMessage(Component.literal("§7Effective Render: §f" + config.effectiveRenderDistanceBlocks() + " blocks"));
        }

        Farplane.LOGGER.info("[FarPlane] Config: maxLevels={}, cutoffDistance={}, debugPreview={}",
                config.maxLevels(), config.cutoffDistance(), config.debugPreview());
    }
}
