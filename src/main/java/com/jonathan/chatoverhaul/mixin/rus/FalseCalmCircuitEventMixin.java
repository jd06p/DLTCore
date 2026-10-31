package com.jonathan.chatoverhaul.mixin.rus;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject only the selected falsecalm2 event, not the alternative integritywatching event. */
@Mixin(targets = "net.mcreator.interpritation.procedures.CircuitStareOnInitialEntitySpawnProcedure", remap = false)
public class FalseCalmCircuitEventMixin {
    @Unique
    private static boolean chatoverhaul$rejectionReported;

    // The 0.7 comparison jumps OVER this callback when integritywatching was selected.
    // Otherwise reject at the event boundary, before even testing the Level/playback side.
    // Preserve both random rolls; never reroll/promote the integritywatching alternative.
    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            slice = @Slice(from = @At(value = "CONSTANT", args = "doubleValue=0.7")),
            at = @At(value = "JUMP", opcode = Opcodes.IFGE, ordinal = 0, shift = At.Shift.AFTER),
            cancellable = true, require = 1, expect = 1, allow = 1)
    private static void chatoverhaul$rejectFalseCalmEvent(LevelAccessor level, double x, double y, double z,
                                                        Entity entity, CallbackInfo ci) {
        // Match the procedure's ordinary rejected-spawn cleanup; never leave a silent event entity behind.
        if (!level.isClientSide() && entity != null) entity.discard();
        if (!chatoverhaul$rejectionReported) {
            chatoverhaul$rejectionReported = true;
            LogUtils.getLogger().info("[DLTCore] RUS Circuit falsecalm2 selection rejected BEFORE sound lookup/submission");
        }
        ci.cancel();
    }
}
