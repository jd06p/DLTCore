package com.jonathan.chatoverhaul.mixin;

import com.jonathan.chatoverhaul.client.WonderlandExitGuard;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class WonderlandWindowCloseMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void dltcore$interceptWonderlandWindowClose(boolean renderLevel, CallbackInfo ci) {
        WonderlandExitGuard.interceptWindowClose();
    }
}
