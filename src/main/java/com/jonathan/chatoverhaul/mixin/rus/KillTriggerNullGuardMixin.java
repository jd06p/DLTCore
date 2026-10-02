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
        "net.mcreator.interpritation.procedures.CurvedThisEntityKillsAnotherOneProcedure",
        "net.mcreator.interpritation.procedures.NullIsHereThisEntityKillsAnotherOneProcedure",
        "net.mcreator.interpritation.procedures.NulllPriGibieliOtEtoiSushchnostiDrughoiProcedure",
        "net.mcreator.interpritation.procedures.SiluetChasePriGibieliOtEtoiSushchnostiDrughoiProcedure",
        "net.mcreator.interpritation.procedures.TheBrokenEndThisEntityKillsAnotherOneProcedure"
}, remap = false)
public class KillTriggerNullGuardMixin {

    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDD)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void head(LevelAccessor level, double x, double y, double z, CallbackInfo ci) {
        if (!level.isClientSide()) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDD)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;orElse(Ljava/lang/Object;)Ljava/lang/Object;", remap = false),
            remap = false)
    private static <T> T guardOrElse(Optional<T> opt, T def) {
        return RusKillTriggerGuard.guardOrElse(opt, def);
    }
}