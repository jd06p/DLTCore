package com.jonathan.chatoverhaul.mixin.arg;

import com.jonathan.chatoverhaul.event.Entity303RitualManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes over The Arg Container's own User0RightclickedOnBlockProcedure -
 * the method that fires when the user_0 music disc is used on a block,
 * and which (unconditionally, in the original mod) applies
 * curseof_user_0 the instant it detects the target block is a jukebox.
 *
 * This is ALWAYS cancelled here, regardless of dimension - the entire
 * ritual flow (dimension gate, dialogue, timing, curse application) is
 * handled by Entity303RitualManager instead, since the original one-line
 * "just apply the effect immediately" behavior has no way to insert a
 * delay for dialogue or a dimension restriction. The jukebox itself still
 * plays the disc completely normally - that's vanilla RecordItem/
 * JukeboxBlock behavior via User0Item's own useOn/super call, an entirely
 * separate code path this mixin never touches.
 *
 * The jukebox-block check mirrors the original procedure's own logic
 * (checking the target block is actually Blocks.JUKEBOX) - without it, ANY
 * right-click with the disc in hand on ANY block would incorrectly show
 * the "rejects this place" message.
 *
 * No compile-time dependency on The Arg Container was needed - every type
 * referenced in this method's signature (LevelAccessor, Entity) is vanilla.
 * Lives in its own "required": false mixin config
 * (mixins.chatoverhaul.arg.json).
 */
@Mixin(targets = "net.mcreator.minecraftalphaargmod.procedures.User0RightclickedOnBlockProcedure", remap = false)
public class JukeboxRitualGateMixin {

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private static void chatoverhaul$gateRitual(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        ci.cancel();

        if (world.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }

        BlockPos pos = BlockPos.containing(x, y, z);
        if (world.getBlockState(pos).getBlock() != Blocks.JUKEBOX) {
            return;
        }

        Entity303RitualManager.tryStartRitual(player, pos);
    }
}
