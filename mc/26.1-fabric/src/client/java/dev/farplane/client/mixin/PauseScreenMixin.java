package dev.farplane.client.mixin;

import dev.farplane.Farplane;
import dev.farplane.config.FarplaneConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addFarPlaneButton(CallbackInfo ci) {
        Farplane.LOGGER.info("[FarPlane] PauseScreenMixin.init() called!");

        this.addDrawableChild(Button.builder(
                Component.literal("FarPlane"),
                button -> {
                    Farplane.LOGGER.info("[FarPlane] Button clicked!");
                    FarplaneConfig config = Farplane.config();
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(Component.literal("§6§lFarPlane Settings:"));
                        this.minecraft.player.sendSystemMessage(Component.literal("§7Max Levels: §f" + config.maxLevels()));
                        this.minecraft.player.sendSystemMessage(Component.literal("§7Cutoff Distance: §f" + config.cutoffDistance() + " tiles"));
                        this.minecraft.player.sendSystemMessage(Component.literal("§7Effective Render: §f" + config.effectiveRenderDistanceBlocks() + " blocks"));
                    }
                }
        ).bounds(this.width / 2 + 104, this.height / 4 + 8, 100, 20).build());

        Farplane.LOGGER.info("[FarPlane] Button added to pause menu");
    }
}
