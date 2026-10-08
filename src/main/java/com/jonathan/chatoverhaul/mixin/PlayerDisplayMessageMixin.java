package com.jonathan.chatoverhaul.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class PlayerDisplayMessageMixin {

    private boolean isOurMessage(String text) {
        String lower = text.toLowerCase();
        return lower.contains("has connected to local play") || lower.contains("has disconnected from local play");
    }

    @Inject(method = "displayClientMessage", at = @At("HEAD"), cancellable = true)
    private void chatoverhaul(Component message, boolean actionBar, CallbackInfo ci) {
        String text = message.getString();
        if (isOurMessage(text)) {
            return;
        }
        String lower = text.toLowerCase();

        if (lower.contains("the_creator")) {
            String replaced = text.replace("The_Creator", "author");
            ServerPlayer self = (ServerPlayer) (Object) this;
            ci.cancel();
            self.displayClientMessage(Component.literal(replaced).withStyle(ChatFormatting.WHITE), actionBar);
            return;
        }

        if (lower.contains(" joined the game")) {
            int idx = lower.indexOf(" joined the game");
            String name = text.substring(0, idx).trim();
            if (!name.isEmpty() && !name.toLowerCase().contains("joined the game")) {
                ServerPlayer self = (ServerPlayer) (Object) this;
                ci.cancel();
                self.displayClientMessage(Component.literal(name + " has connected to Local Play!").withStyle(ChatFormatting.WHITE), actionBar);
            }
            return;
        }
        if (lower.contains(" left the game")) {
            int idx = lower.indexOf(" left the game");
            String name = text.substring(0, idx).trim();
            if (!name.isEmpty() && !name.toLowerCase().contains("left the game")) {
                ServerPlayer self = (ServerPlayer) (Object) this;
                ci.cancel();
                self.displayClientMessage(Component.literal(name + " has disconnected from Local Play!").withStyle(ChatFormatting.WHITE), actionBar);
            }
            return;
        }
    }

    @Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void chatoverhaul(Component message, CallbackInfo ci) {
        String text = message.getString();
        if (isOurMessage(text)) {
            return;
        }
        String lower = text.toLowerCase();

        if (lower.contains("the_creator")) {
            String replaced = text.replace("The_Creator", "author");
            ServerPlayer self = (ServerPlayer) (Object) this;
            ci.cancel();
            self.sendSystemMessage(Component.literal(replaced).withStyle(ChatFormatting.WHITE));
            return;
        }

        if (lower.contains(" joined the game")) {
            int idx = lower.indexOf(" joined the game");
            String name = text.substring(0, idx).trim();
            if (!name.isEmpty() && !name.toLowerCase().contains("joined the game")) {
                ServerPlayer self = (ServerPlayer) (Object) this;
                ci.cancel();
                self.sendSystemMessage(Component.literal(name + " has connected to Local Play!").withStyle(ChatFormatting.WHITE));
            }
            return;
        }
        if (lower.contains(" left the game")) {
            int idx = lower.indexOf(" left the game");
            String name = text.substring(0, idx).trim();
            if (!name.isEmpty() && !name.toLowerCase().contains("left the game")) {
                ServerPlayer self = (ServerPlayer) (Object) this;
                ci.cancel();
                self.sendSystemMessage(Component.literal(name + " has disconnected from Local Play!").withStyle(ChatFormatting.WHITE));
            }
            return;
        }
    }
}