package com.jonathan.chatoverhaul.mixin.rus;

import com.jonathan.chatoverhaul.event.RusKillTriggerGuard;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(targets = {
        "net.mcreator.interpritation.procedures.CircuitThisEntityKillsAnotherOneProcedure",
        "net.mcreator.interpritation.procedures.SiluetChasePriGibieliOtEtoiSushchnostiDrughoiProcedure"
}, remap = false)
public class KillTriggerDelayedNullGuardMixin {

    @Inject(method = "lambda$execute$4(Lnet/minecraft/world/level/LevelAccessor;DDD)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void headDelayed(LevelAccessor level, double x, double y, double z, CallbackInfo ci) {
        if (!level.isClientSide()) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "lambda$execute$4(Lnet/minecraft/world/level/LevelAccessor;DDD)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;orElse(Ljava/lang/Object;)Ljava/lang/Object;", remap = false),
            remap = false)
    private static <T> T guardOrElseDeferred(Optional<T> opt, T def) {
        return RusKillTriggerGuard.guardOrElse(opt, def);
    }
}