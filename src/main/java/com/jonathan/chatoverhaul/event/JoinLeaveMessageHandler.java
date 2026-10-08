package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Emits the DeltaQuest join/leave lines:
 *
 *   "Steve" joins  ->  "Steve has connected to Local Play!"      (white)
 *   "Steve" leaves ->  "Steve has disconnected from Local Play!" (white)
 *
 * The real vanilla broadcasts are suppressed at their source by
 * PlayerListJoinLeaveMixin (join) and ServerGamePacketListenerLeaveMixin
 * (leave). The replacement is sent from here instead, on the Forge
 * login/logout events, because the vanilla join broadcast fires before the
 * joining player is added to the player list - broadcasting from this point
 * (player already present) reaches the joiner as well as everyone else,
 * exactly once.
 *
 * Server Side Horror fake players are handled separately by
 * PlayerListJoinLeaveMixin, which rewrites the join/leave broadcasts those
 * fake players still emit.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class JoinLeaveMessageHandler {

    private JoinLeaveMessageHandler() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            broadcast(player, " has connected to Local Play!");
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            broadcast(player, " has disconnected from Local Play!");
        }
    }

    private static void broadcast(ServerPlayer player, String suffix) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        String name = player.getGameProfile().getName();
        Component message = Component.literal(name + suffix).withStyle(ChatFormatting.WHITE);
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}