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
 * Removes the description/tooltip from {@code the_wonderland:dark_cross}.
 *
 * <p>Root cause (verified against Wonderland 3.0.3 bytecode): each of these
 * items overrides {@code Item.appendHoverText} and its single tooltip is that
 * one {@code Component.literal(...)} line added straight into the list, with
 * nothing else. Cancelling the {@code appendHoverText} invocation at its head
 * (injected under its runtime name {@code m_7373_}, matching how the shipped
 * jar compiles these item classes) removes exactly that line; every other
 * tooltip the stack would normally show (attributes, enchantments, durability,
 * etc.) is added AFTER this call by {@code ItemStack.getTooltipLines} and is
 * untouched. The item's own class is never otherwise modified, so its
 * behavior/recipes stay identical.
 *
 * <p>Scoped by exact bytecode-level target and only applied client-side (this
 * mixin lives in the "client" array of
 * {@code mixins.chatoverhaul.wonderland.json}, which is "required": false, so
 * chatoverhaul loads fine with or without The Wonderland), so a dedicated
 * server never applies it.
 */
@Mixin(targets = "net.mcreator.thewonderland.item.DarkCrossItem", remap = false)
public class DarkCrossItemTooltipMixin {

    @Inject(method = "m_7373_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;"
            + "Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void chatoverhaul$removeDescription(ItemStack stack, Level level, List<Component> tooltip,
                                                TooltipFlag flag, CallbackInfo ci) {
        ci.cancel();
    }
}