package com.jonathan.chatoverhaul.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses the REAL vanilla leave broadcast
 * ({@code ServerGamePacketListenerImpl#onDisconnect} ->
 * {@code PlayerList#broadcastSystemMessage("multiplayer.player.left")}).
 *
 * The custom leave line is emitted by {@code JoinLeaveMessageHandler} on the
 * Forge logout event. Suppressing only this call site means Server Side
 * Horror's fake-player leave broadcast (which goes through
 * {@code broadcastSystemMessage} from {@code CommonClass#removeFakeJoiner},
 * not through this method) is left for {@code PlayerListJoinLeaveMixin} to
 * rewrite.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerLeaveMixin {

    @Redirect(method = "onDisconnect(Lnet/minecraft/network/chat/Component;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void chatoverhaul$suppressRealLeave(PlayerList self, Component message, boolean bypassHiddenChat) {
        // Handled by JoinLeaveMessageHandler.
    }
}