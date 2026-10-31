package com.jonathan.chatoverhaul.mixin.rus;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Disable Voidd's falsecalm2 event BEFORE its destructive "stopsound @a" command. */
@Mixin(targets = "net.mcreator.interpritation.procedures.VoiddOnInitialEntitySpawnProcedure", remap = false)
public class FalseCalmVoidEventMixin {
    @Unique
    private static boolean chatoverhaul$rejectionReported;

    // This initializer has only one successful event: stop all audio, then falsecalm2.
    // Reject before ANY original eligibility/RNG/command/sound/lifecycle code executes.
    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true, require = 1, expect = 1, allow = 1)
    private static void chatoverhaul$disableFalseCalmEvent(LevelAccessor level, double x, double y, double z,
                                                        Entity entity, CallbackInfo ci) {
        if (entity != null) {
            if (!level.isClientSide()) entity.discard();
            if (!chatoverhaul$rejectionReported) {
                chatoverhaul$rejectionReported = true;
                LogUtils.getLogger().info("[DLTCore] RUS Void falsecalm2 initializer rejected BEFORE conditions/commands/sound submission");
            }
        }
        ci.cancel();
    }
}
