package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.event.entity.player.PermissionsChangedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sends a one-time notice to a player when they gain operator/command
 * privileges. The exact wording (including the blank line) is fixed:
 *
 *   NOTICE: Admin has been granted to your user. Use it with responsibility
 *
 *   Do not abuse powers.
 *
 * Three distinct ways a player gains command access are covered, each by
 * the narrowest hook available - no per-tick polling anywhere:
 *
 * 1. /op on a dedicated (or LAN) server: Forge's PermissionsChangedEvent,
 *    fired from PlayerList#op/#deop, carries both old and new permission
 *    level. We only react to a genuine rise (old <= 0, new > 0), so a
 *    player who is already op when they join is never notified, while a
 *    deop-then-reop does fire again.
 *
 * 2. Entering a singleplayer world that was created with cheats enabled:
 *    the host is the singleplayer owner, so MinecraftServer#
 *    getProfilePermissions already returns > 0 on join with no change
 *    event ever firing. PlayerLoggedInEvent covers this, but ONLY for
 *    integrated (singleplayer/LAN) servers - joining an already-op account
 *    on a dedicated server must stay silent. OperatorNoticeTracker records
 *    that the notice was shown so re-entering the same world doesn't
 *    repeat it.
 *
 * 3. Opening a singleplayer world to LAN with "Allow Cheats" enabled:
 *    IntegratedServer#publishServer calls PlayerList#
 *    setAllowCheatsForAllPlayers(true) with no Forge event. A small mixin
 *    calls {@link #onAllowCheatsEnabled} so the players that just gained
 *    access are notified at that moment.
 *
 * The notice is always sent with ServerPlayer#sendSystemMessage, so it is
 * strictly server-side and only ever reaches the affected player.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class OperatorNoticeHandler {

    private static final Component NOTICE = Component.literal(
            "NOTICE: Admin has been granted to your user. Use it with responsibility\n\nDo not abuse powers.");

    private OperatorNoticeHandler() {}

    @SubscribeEvent
    public static void onPermissionsChanged(PermissionsChangedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getOldLevel() <= 0 && event.getNewLevel() > 0) {
            notifyPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSingleplayer()) {
            // Dedicated servers rely solely on the real permission-change
            // event, so an account that is already op on join stays silent.
            return;
        }
        if (server.getProfilePermissions(player.getGameProfile()) > 0) {
            notifyPlayerOnce(server, player);
        }
    }

    /**
     * Called from PlayerListMixin when setAllowCheatsForAllPlayers(true) runs.
     * At that point every online player on an integrated server gains level 4
     * access, so each one that hasn't already been told is notified.
     */
    public static void onAllowCheatsEnabled(PlayerList playerList) {
        MinecraftServer server = playerList.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : playerList.getPlayers()) {
            if (server.getProfilePermissions(player.getGameProfile()) > 0) {
                notifyPlayerOnce(server, player);
            }
        }
    }

    private static void notifyPlayer(ServerPlayer player) {
        player.sendSystemMessage(NOTICE);
        MinecraftServer server = player.getServer();
        if (server != null && server.isSingleplayer()) {
            OperatorNoticeTracker.markNotified(server, player.getUUID());
        }
    }

    private static void notifyPlayerOnce(MinecraftServer server, ServerPlayer player) {
        if (OperatorNoticeTracker.hasNotified(server, player.getUUID())) {
            return;
        }
        OperatorNoticeTracker.markNotified(server, player.getUUID());
        player.sendSystemMessage(NOTICE);
    }
}