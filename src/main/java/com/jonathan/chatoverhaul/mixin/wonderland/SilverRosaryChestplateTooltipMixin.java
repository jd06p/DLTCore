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
 * Removes the description/tooltip from
 * {@code the_wonderland:silver_rosary_chestplate} (the {@code Chestplate}
 * inner class of {@code SilverRosaryItem}, verified against Wonderland 3.0.3
 * bytecode: its {@code appendHoverText} override adds exactly one literal
 * line - the description - so cancelling that single invocation removes the
 * tooltip while the armor's attributes and everything else stay untouched).
 * The armor functionality ({@code getArmorTexture}, defense, etc.) is not
 * modified.
 */
@Mixin(targets = "net.mcreator.thewonderland.item.SilverRosaryItem$Chestplate", remap = false)
public class SilverRosaryChestplateTooltipMixin {

    @Inject(method = "m_7373_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;"
            + "Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void chatoverhaul$removeDescription(ItemStack stack, Level level, List<Component> tooltip,
                                                TooltipFlag flag, CallbackInfo ci) {
        ci.cancel();
    }
}