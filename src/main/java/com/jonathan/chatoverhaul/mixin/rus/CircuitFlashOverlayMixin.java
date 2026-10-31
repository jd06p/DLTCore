package com.jonathan.chatoverhaul.mixin.rus;

import com.jonathan.chatoverhaul.client.CircuitChaseState;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.mcreator.interpritation.procedures.CircuitOverlayDisplayOverlayIngameProcedure", remap = false)
public class CircuitFlashOverlayMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private static void chatoverhaul$privateFlash(LevelAccessor level, double x, double y, double z,
                                                CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(CircuitChaseState.flash());
    }
}
