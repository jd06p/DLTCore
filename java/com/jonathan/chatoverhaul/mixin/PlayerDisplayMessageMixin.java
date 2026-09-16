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
 * ALSO handles rus-patch's "null joined the game": that one is dispatched
 * via an actual `/tellraw @a {...}` command, and vanilla's TellRawCommand
 * is believed to resolve `@a` into individual targets and call
 * ServerPlayer#sendSystemMessage(Component) on each one directly - a
 * different method than displayClientMessage, so it gets its own
 * injection point below. The same check is ALSO duplicated into the
 * displayClientMessage injection as a safety net, in case that belief
 * about which exact method vanilla routes through turns out to be wrong -
 * one of the two is guaranteed to be right, and having both costs nothing
 * since only one will ever actually match a given message.
 *
 * IMPORTANT: mixed into ServerPlayer, not Player. Both displayClientMessage
 * and sendSystemMessage are near-empty stubs on the base Player class -
 * ServerPlayer overrides both with the real implementation that actually
 * sends the packet, and virtual dispatch always calls the override. A
 * mixin on Player's copy would compile and package fine but never
 * actually run - this is the same lesson learned the hard way with
 * displayClientMessage originally.
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
            return;
        }

        // Safety net: if TellRawCommand ever routes through displayClientMessage
        // instead of sendSystemMessage, this catches "null joined the game" here too.
        if (text.equals("null joined the game")) {
            ServerPlayer self = (ServerPlayer) (Object) this;
            ci.cancel();
            self.displayClientMessage(Component.literal("\u273A joined the game").withStyle(message.getStyle()), actionBar);
        }
    }

    @Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void chatoverhaul$renameNullJoin(Component message, CallbackInfo ci) {
        String text = message.getString();

        if (text.equals("null joined the game")) {
            ServerPlayer self = (ServerPlayer) (Object) this;
            ci.cancel();
            self.sendSystemMessage(Component.literal("\u273A joined the game").withStyle(message.getStyle()));
        }
    }
}
