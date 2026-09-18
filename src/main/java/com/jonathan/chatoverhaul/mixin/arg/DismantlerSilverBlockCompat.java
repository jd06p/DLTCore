package com.jonathan.chatoverhaul.mixin.arg;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the ARG Container's Dismantler harvest The Wonderland's silver block.
 *
 * <p>Root cause (proven from Wonderland 3.0.3 bytecode, not guessed): the
 * block side gates harvesting in {@code canHarvestBlock}: it takes the
 * player's selected ({@code getInventory().getSelected()}) stack LOCALLY as an
 * {@code Item}, requires {@code instanceOf PickaxeItem}, and then requires the
 * flattened tier {@code PickaxeItem.getTier().getLevel() >= 2}. The
 * Dismantler is a {@code TieredItem} (never a {@code PickaxeItem}), so this
 * gate always fails and the silver block can never drop -- regardless of any
 * {@code mineable/*} tag, since {@code SilverBlockBlock.canHarvestBlock}
 * never consults the tags at all.
 *
 * <p>This mixin is the inverse and, as the block-side gate is the ONLY gate
 * that matters, this is the place we must win. It is deliberately scoped via
 * an EXACT "is the held item an instance of the Dismantler" check (matched by
 * runtime class name, so there is no compile-time or hard dependency on the
 * ARG mod): when the held item IS the Dismantler, the silver block short
 * circuits to correctly-harvestable; every other item and every other block
 * falls through to Wonderland's own logic unchanged.
 *
 * <p>Keeps the existing DLTCore convention: the target block lives in
 * Wonderland, this mod ships no such block, and the mixin config it lives in
 * is marked "required": false, so chatoverhaul loads fine with or without
 * The Wonderland.
 */
@Mixin(targets = "net.mcreator.thewonderland.block.SilverBlockBlock", remap = false)
public class DismantlerSilverBlockCompat {

    private static final String DISMANTLER_ITEM_CLASS =
            "net.mcreator.minecraftalphaargmod.item.DismantlerItem";

    @Inject(method = "canHarvestBlock",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void chatoverhaul$dismantlerHarvestsSilverBlock(
            BlockState state, BlockGetter level, BlockPos pos, Player player,
            CallbackInfoReturnable<Boolean> cir) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem().getClass().getName().equals(DISMANTLER_ITEM_CLASS)) {
            cir.setReturnValue(true);
        }
    }
}
