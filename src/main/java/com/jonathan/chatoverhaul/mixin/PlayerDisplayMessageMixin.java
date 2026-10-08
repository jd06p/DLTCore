package com.jonathan.chatoverhaul.mixin;

import com.jonathan.chatoverhaul.util.LocalPlayNames;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class PlayerDisplayMessageMixin {

    private static final String CONNECT = " has connected to Local Play!";
    private static final String DISCONNECT = " has disconnected from Local Play!";

    /**
     * Returns the replacement text for a join/leave system message, or null
     * when the message must be shown as-is.
     *
     * Only two things are rewritten: mod-specific identities (The Broken
     * Script's "null" -> U+273A, The ARG Container's "The_Creator" ->
     * "author") and the vanilla "X joined the game" / "X left the game"
     * wording. The identity match is exact on the extracted name, so normal
     * player names and unrelated messages containing "null" are untouched.
     */
    private String chatoverhaul$remap(String text) {
        if (text.endsWith(CONNECT)) {
            String raw = text.substring(0, text.length() - CONNECT.length());
            String mapped = LocalPlayNames.normalize(raw);
            return mapped.equals(raw) ? null : mapped + CONNECT;
        }
        if (text.endsWith(DISCONNECT)) {
            String raw = text.substring(0, text.length() - DISCONNECT.length());
            String mapped = LocalPlayNames.normalize(raw);
            return mapped.equals(raw) ? null : mapped + DISCONNECT;
        }

        String lower = text.toLowerCase();
        if (lower.contains(" joined the game")) {
            int idx = lower.indexOf(" joined the game");
            String name = LocalPlayNames.normalize(text.substring(0, idx).trim());
            if (name.isEmpty() || name.toLowerCase().contains("joined the game")) {
                return null;
            }
            return name + CONNECT;
        }
        if (lower.contains(" left the game")) {
            int idx = lower.indexOf(" left the game");
            String name = LocalPlayNames.normalize(text.substring(0, idx).trim());
            if (name.isEmpty() || name.toLowerCase().contains("left the game")) {
                return null;
            }
            return name + DISCONNECT;
        }
        if (lower.contains("the_creator")) {
            return text.replace("The_Creator", "author");
        }
        return null;
    }

    @Inject(method = "displayClientMessage", at = @At("HEAD"), cancellable = true)
    private void chatoverhaul$onDisplayClientMessage(Component message, boolean actionBar, CallbackInfo ci) {
        String text = message.getString();
        String replacement = chatoverhaul$remap(text);
        if (replacement == null || replacement.equals(text)) {
            return;
        }
        ci.cancel();
        ServerPlayer self = (ServerPlayer) (Object) this;
        self.displayClientMessage(Component.literal(replacement).withStyle(ChatFormatting.WHITE), actionBar);
    }

    @Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void chatoverhaul$onSendSystemMessage(Component message, CallbackInfo ci) {
        String text = message.getString();
        String replacement = chatoverhaul$remap(text);
        if (replacement == null || replacement.equals(text)) {
            return;
        }
        ci.cancel();
        ServerPlayer self = (ServerPlayer) (Object) this;
        self.sendSystemMessage(Component.literal(replacement).withStyle(ChatFormatting.WHITE));
    }
}
