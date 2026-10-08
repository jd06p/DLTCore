package com.jonathan.chatoverhaul.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Join/leave chat customization.
 *
 * Two separate concerns are handled here:
 *
 * 1. The REAL vanilla join broadcast ({@code PlayerList#placeNewPlayer}) is
 *    suppressed at its call site. The real replacement line is emitted by
 *    {@code JoinLeaveMessageHandler} on the Forge login event, because the
 *    vanilla broadcast fires before the joining player is added to the player
 *    list and so never reaches the joiner. (The real leave is suppressed
 *    likewise by {@code ServerGamePacketListenerLeaveMixin}.)
 *
 * 2. Server Side Horror's fake players ({@code CommonClass#addFakeJoiner} /
 *    {@code removeFakeJoiner}) announce themselves through this same
 *    {@code broadcastSystemMessage(Component, boolean)} method using the
 *    vanilla "multiplayer.player.joined" / "multiplayer.player.left" keys.
 *    Those are NOT suppressed - they are rewritten here into the same custom
 *    line, so fake joins/leaves display "<name> has connected to Local Play!"
 *    / "<name> has disconnected from Local Play!" in white. The name is taken
 *    from the translation argument, so any fake-player name works.
 *
 * Because the real broadcasts are removed at their call sites before they
 * reach this method, whatever arrives here with those keys is a fake/other
 * mod's announcement (or nothing), so rewriting is safe and produces exactly
 * one line per event.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListJoinLeaveMixin {

    private static final String JOIN_KEY = "multiplayer.player.joined";
    private static final String JOIN_RENAMED_KEY = "multiplayer.player.joined.renamed";
    private static final String LEAVE_KEY = "multiplayer.player.left";

    /**
     * Suppress the real join broadcast inside placeNewPlayer; it is replaced by
     * JoinLeaveMessageHandler's PlayerLoggedInEvent line.
     */
    @Redirect(method = "placeNewPlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void chatoverhaul$suppressRealJoin(PlayerList self, Component message, boolean bypassHiddenChat) {
        // Handled by JoinLeaveMessageHandler.
    }

    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void chatoverhaul$rewriteFakeJoinLeave(Component message, boolean bypassHiddenChat, CallbackInfo ci) {
        if (!(message.getContents() instanceof TranslatableContents contents)) {
            return;
        }

        String key = contents.getKey();
        boolean joining = JOIN_KEY.equals(key) || JOIN_RENAMED_KEY.equals(key);
        boolean leaving = LEAVE_KEY.equals(key);
        if (!joining && !leaving) {
            return;
        }

        String name = extractName(contents.getArgs());
        String text = name + (joining ? " has connected to Local Play!" : " has disconnected from Local Play!");

        ci.cancel();
        ((PlayerList) (Object) this).broadcastSystemMessage(
                Component.literal(text).withStyle(ChatFormatting.WHITE), bypassHiddenChat);
    }

    private static String extractName(Object[] args) {
        if (args.length == 0 || args[0] == null) {
            return "";
        }
        Object arg = args[0];
        if (arg instanceof Component component) {
            return component.getString();
        }
        return arg.toString();
    }
}