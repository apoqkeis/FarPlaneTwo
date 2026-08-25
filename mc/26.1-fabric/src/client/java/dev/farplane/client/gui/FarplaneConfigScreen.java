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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * FarPlane configuration screen with sliders and toggles.
 * Following the original FP2 config GUI approach.
 *
 * @author FarPlane contributors
 */
public class FarplaneConfigScreen extends Screen {
    private final Screen parent;
    private final FarplaneConfig config;

    // Edit boxes for numeric values
    private EditBox maxLevelsBox;
    private EditBox cutoffDistanceBox;
    private EditBox terrainThreadsBox;

    // Toggle buttons
    private CycleButton<Boolean> debugPreviewButton;

    public FarplaneConfigScreen(Screen parent) {
        super(Component.literal("FarPlane Settings"));
        this.parent = parent;
        this.config = Farplane.config();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 40;
        int spacing = 30;
        int labelWidth = 120;
        int inputWidth = 60;
        int buttonWidth = 100;

        // --- Render Distance Section ---
        this.addRenderableWidget(Button.builder(
                Component.literal("§6§lRender Distance"),
                btn -> {}
        ).bounds(centerX - 150, startY, 300, 20).build());

        // Max Levels
        startY += spacing;
        this.addRenderableWidget(Button.builder(
                Component.literal("§eMax Levels:"),
                btn -> {}
        ).bounds(centerX - 150, startY, labelWidth, 20).build());

        maxLevelsBox = new EditBox(this.font, centerX - 150 + labelWidth + 10, startY, inputWidth, 20,
                Component.literal("Max Levels"));
        maxLevelsBox.setValue(String.valueOf(config.maxLevels()));
        maxLevelsBox.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(maxLevelsBox);

        this.addRenderableWidget(Button.builder(
                Component.literal("§7(1-4, default: 1)"),
                btn -> {}
        ).bounds(centerX - 150 + labelWidth + inputWidth + 20, startY, 100, 20).build());

        // Cutoff Distance
        startY += spacing;
        this.addRenderableWidget(Button.builder(
                Component.literal("§eCutoff Distance:"),
                btn -> {}
        ).bounds(centerX - 150, startY, labelWidth, 20).build());

        cutoffDistanceBox = new EditBox(this.font, centerX - 150 + labelWidth + 10, startY, inputWidth, 20,
                Component.literal("Cutoff Distance"));
        cutoffDistanceBox.setValue(String.valueOf(config.cutoffDistance()));
        cutoffDistanceBox.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(cutoffDistanceBox);

        this.addRenderableWidget(Button.builder(
                Component.literal("§7(tiles, default: 4)"),
                btn -> {}
        ).bounds(centerX - 150 + labelWidth + inputWidth + 20, startY, 100, 20).build());

        // Effective Render Distance (calculated)
        startY += spacing;
        int effectiveDistance = config.effectiveRenderDistanceBlocks();
        this.addRenderableWidget(Button.builder(
                Component.literal("§aEffective Render: §f" + effectiveDistance + " blocks"),
                btn -> {}
        ).bounds(centerX - 150, startY, 300, 20).build());

        // --- Performance Section ---
        startY += spacing + 10;
        this.addRenderableWidget(Button.builder(
                Component.literal("§6§lPerformance"),
                btn -> {}
        ).bounds(centerX - 150, startY, 300, 20).build());

        // Terrain Threads
        startY += spacing;
        this.addRenderableWidget(Button.builder(
                Component.literal("§eTerrain Threads:"),
                btn -> {}
        ).bounds(centerX - 150, startY, labelWidth, 20).build());

        terrainThreadsBox = new EditBox(this.font, centerX - 150 + labelWidth + 10, startY, inputWidth, 20,
                Component.literal("Terrain Threads"));
        terrainThreadsBox.setValue(String.valueOf(config.terrainThreads()));
        terrainThreadsBox.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(terrainThreadsBox);

        this.addRenderableWidget(Button.builder(
                Component.literal("§7(1-8, default: 2)"),
                btn -> {}
        ).bounds(centerX - 150 + labelWidth + inputWidth + 20, startY, 100, 20).build());

        // --- Debug Section ---
        startY += spacing + 10;
        this.addRenderableWidget(Button.builder(
                Component.literal("§6§lDebug"),
                btn -> {}
        ).bounds(centerX - 150, startY, 300, 20).build());

        // Debug Preview Toggle
        startY += spacing;
        debugPreviewButton = CycleButton.onOffBuilder(config.debugPreview())
                .create(centerX - 150, startY, buttonWidth, 20,
                        Component.literal("Debug Preview"));
        this.addRenderableWidget(debugPreviewButton);

        // --- Action Buttons ---
        startY += spacing + 20;

        // Save Button
        this.addRenderableWidget(Button.builder(
                Component.literal("§a§lSave"),
                btn -> saveConfig()
        ).bounds(centerX - 110, startY, 100, 20).build());

        // Cancel Button
        this.addRenderableWidget(Button.builder(
                Component.literal("§c§lCancel"),
                btn -> onClose()
        ).bounds(centerX + 10, startY, 100, 20).build());

        // Reset to Defaults Button
        startY += spacing;
        this.addRenderableWidget(Button.builder(
                Component.literal("§eReset to Defaults"),
                btn -> resetDefaults()
        ).bounds(centerX - 75, startY, 150, 20).build());
    }

    private void saveConfig() {
        try {
            int maxLevels = Integer.parseInt(maxLevelsBox.getValue());
            int cutoffDistance = Integer.parseInt(cutoffDistanceBox.getValue());
            int terrainThreads = Integer.parseInt(terrainThreadsBox.getValue());
            boolean debugPreview = debugPreviewButton.getValue();

            // Validate ranges
            maxLevels = Math.max(1, Math.min(4, maxLevels));
            cutoffDistance = Math.max(1, Math.min(64, cutoffDistance));
            terrainThreads = Math.max(1, Math.min(8, terrainThreads));

            // Update config
            FarplaneConfig newConfig = new FarplaneConfig();
            newConfig.setMaxLevels(maxLevels);
            newConfig.setCutoffDistance(cutoffDistance);
            newConfig.setTerrainThreads(terrainThreads);
            newConfig.setDebugPreview(debugPreview);
            newConfig.save();

            Farplane.LOGGER.info("[FarPlane] Config saved: maxLevels={}, cutoffDistance={}, terrainThreads={}, debugPreview={}",
                    maxLevels, cutoffDistance, terrainThreads, debugPreview);

            // Show confirmation
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.literal("§a[FarPlane] Settings saved!"));
            }

            onClose();
        } catch (NumberFormatException e) {
            Farplane.LOGGER.warn("[FarPlane] Invalid config value: {}", e.getMessage());
        }
    }

    private void resetDefaults() {
        maxLevelsBox.setValue("1");
        cutoffDistanceBox.setValue("4");
        terrainThreadsBox.setValue("2");
        debugPreviewButton.setValue(true);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(gui.graphics.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, delta);
    }
}
