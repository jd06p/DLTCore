package com.jonathan.chatoverhaul.client;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only bookkeeping for the quest HUD cache: clears it when the client
 * leaves a world, so joining a different server or logging into a different
 * player can never show stale quest progress from the previous session.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID, value = Dist.CLIENT)
public final class ClientQuestLifecycle {

    private ClientQuestLifecycle() {}

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientQuestState.clear();
    }
}