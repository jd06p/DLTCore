package com.jonathan.chatoverhaul.mixin.customskyboxes;

import com.jonathan.chatoverhaul.client.ChatOverhaulConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional client compatibility with Custom Skyboxes 1.0.4.2.
 * Its renderer gets active and fading sky layers through these manager methods,
 * so filtering here leaves its normal Overworld render/animation code intact.
 */
@Pseudo
@Mixin(targets = "org.cotii.customskyboxes.client.sky.SkyboxManager", remap = false)
public abstract class SkyboxManagerDimensionMixin {

    private static boolean dltcore$shouldSuppressOverrides() {
        if (!ChatOverhaulConfig.CUSTOM_SKYBOXES_OVERWORLD_ONLY.get()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && !Level.OVERWORLD.equals(minecraft.level.dimension());
    }

    @Inject(method = "resolveLayer", at = @At("HEAD"), cancellable = true, remap = false)
    private void dltcore$hideActiveOverrides(@Coerce Object type, int layer,
                                             CallbackInfoReturnable<Object> cir) {
        if (dltcore$shouldSuppressOverrides()) cir.setReturnValue(null);
    }

    @Inject(
            method = "getFadingOut(Lorg/cotii/customskyboxes/client/sky/SkyboxType;)Lorg/cotii/customskyboxes/client/sky/SkyboxManager$FadingOut;",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private void dltcore$hideFadingOverride(@Coerce Object type,
                                            CallbackInfoReturnable<Object> cir) {
        if (dltcore$shouldSuppressOverrides()) cir.setReturnValue(null);
    }

    @Inject(
            method = "getFadingOut(Lorg/cotii/customskyboxes/client/sky/SkyboxType;I)Lorg/cotii/customskyboxes/client/sky/SkyboxManager$FadingOut;",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private void dltcore$hideFadingLayer(@Coerce Object type, int layer,
                                         CallbackInfoReturnable<Object> cir) {
        if (dltcore$shouldSuppressOverrides()) cir.setReturnValue(null);
    }
}
