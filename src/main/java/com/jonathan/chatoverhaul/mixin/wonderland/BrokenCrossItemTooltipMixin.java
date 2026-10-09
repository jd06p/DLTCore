package com.jonathan.chatoverhaul.mixin.wonderland;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Removes the description/tooltip from {@code the_wonderland:broken_cross}.
 * Same mechanism as {@link DarkCrossItemTooltipMixin} (verified against
 * Wonderland 3.0.3 bytecode): the single tooltip is the one line added in the
 * item's {@code appendHoverText} override, so cancelling that invocation -
 * under its shipped runtime name {@code m_7373_} - removes exactly the
 * description and nothing else. Class behavior/recipes untouched.
 */
@Mixin(targets = "net.mcreator.thewonderland.item.BrokenCrossItem", remap = false)
public class BrokenCrossItemTooltipMixin {

    @Inject(method = "m_7373_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;"
            + "Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void chatoverhaul$removeDescription(ItemStack stack, Level level, List<Component> tooltip,
                                                TooltipFlag flag, CallbackInfo ci) {
        ci.cancel();
    }
}