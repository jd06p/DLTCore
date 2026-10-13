package com.jonathan.chatoverhaul.mixin.instability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes the INSTABILITY music disc running its entire effect sequence from a
 * plain right-click on any block, without ever being inserted into a jukebox.
 *
 * <p>Root cause (proven from INSTABILITY's bytecode, not guessed):
 * {@code InstabilityItem} extends {@code RecordItem} and overrides
 * {@code useOn(UseOnContext)} as:
 *
 * <pre>
 *   super.useOn(context);                       // inserts ONLY when the block is an empty jukebox
 *   InstabilityRightclickedOnBlockProcedure.execute(level, x, y, z);   // runs ALWAYS
 *   return InteractionResult.SUCCESS;
 * </pre>
 *
 * <p>The mod discards the result of {@code super.useOn}, so the procedure -
 * which spawns the black-box particles, broadcasts "You Don't Know what you
 * are doing...", forces rain and night, and sets
 * {@code InstabilitymusicdiskModVariables.MapVariables.isDiscPlaying = true}
 * - runs on every right-click against a block. {@code isDiscPlaying} is read
 * by {@code GItchtimerupdateProcedure} on each player tick, and that is what
 * drives the window-mode glitch and the rest of the sequence. Because the
 * disc's music only starts when {@code RecordItem} actually inserts the stack
 * into a jukebox, the music stays silent while every visual/window effect
 * still fires.
 *
 * <p>Fix: gate the original method at its head against the exact condition
 * {@code RecordItem.useOn} itself uses to insert a record - the clicked block
 * is an empty jukebox. When that holds, the method is left completely
 * untouched, so the insert, the playback and the full effect sequence behave
 * exactly as before. When it does not hold, the method is cancelled and
 * returns {@link InteractionResult#PASS} - precisely what vanilla
 * {@code RecordItem.useOn} returns when it does not insert - so no effect,
 * and no window-mode change, can start from a handheld click.
 *
 * <p>{@code isDiscPlaying} is written in exactly one place in the whole mod
 * (this procedure) and the procedure is called from exactly one place
 * ({@code InstabilityItem.useOn}), so gating here removes the invalid source
 * without touching the legitimate one.
 *
 * <p>Common (both sides): the map variable that drives the sequence is
 * updated on the server and synced to clients, while the window glitch runs
 * on the client as well, so the gate must apply on both. This config is
 * "required": false, so chatoverhaul loads fine with or without the
 * INSTABILITY mod installed.
 */
@Mixin(targets = "net.mcreator.instabilitymusicdisk.item.InstabilityItem", remap = false)
public class InstabilityItemMixin {

    @Inject(
            method = "m_6225_(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void chatoverhaul$requireJukeboxInsert(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        boolean jukeboxInsert = state.is(Blocks.JUKEBOX) && !state.getValue(JukeboxBlock.HAS_RECORD);
        if (!jukeboxInsert) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
