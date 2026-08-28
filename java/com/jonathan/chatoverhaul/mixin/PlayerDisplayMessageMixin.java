package com.jonathan.chatoverhaul.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Arg Container sends "The_Creator joined the game" (and a handful of
 * "<The_Creator> ..." chat lines) directly via Player#displayClientMessage,
 * bypassing the command system entirely - so there's no vanilla/Forge event
 * to hook. We intercept the call itself and rewrite the text in place.
 *
 * IMPORTANT: mixed into ServerPlayer, not Player. Player#displayClientMessage
 * is a near-empty stub - ServerPlayer overrides it with the real
 * implementation that actually sends the packet, and virtual dispatch always
 * calls the override. A mixin on Player's copy would compile and package
 * fine but never actually run.
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerDisplayMessageMixin {

    @Inject(method = "displayClientMessage", at = @At("HEAD"), cancellable = true)
    private void chatoverhaul$renameTheCreator(Component message, boolean actionBar, CallbackInfo ci) {
        String text = message.getString();

        if (text.contains("The_Creator")) {
            String replaced = text.replace("The_Creator", "author");
            ServerPlayer self = (ServerPlayer) (Object) this;
            ci.cancel();
            self.displayClientMessage(Component.literal(replaced).withStyle(message.getStyle()), actionBar);
        }
    }
}
