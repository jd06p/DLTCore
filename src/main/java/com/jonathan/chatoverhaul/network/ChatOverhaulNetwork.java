package com.jonathan.chatoverhaul.network;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * chatoverhaul's Forge network channel - the same SimpleChannel pattern the
 * Integrity bossfight mod uses to deliver its client-only fake-LWJGL-alert
 * triggers. Carries three client-bound message types: {@link ClientAlertPacket}
 * (the cosmetic fake-alert trigger, id 0) and {@link QuestSyncPacket} (the
 * per-player quest-state snapshot the quest HUD renders from, id 1), and
 * {@link CircuitChasePacket} (the private Circuit screen state, id 2).
 *
 * Everything in this class is common-side; the channel and packets are
 * registered once from the mod constructor, which runs on both physical
 * sides. The client-only work happens inside each packet handler under
 * DistExecutor, so a dedicated server can send these packets freely and the
 * client classes are never loaded there.
 */
public final class ChatOverhaulNetwork {

    /** Bumped when the wire layout or required packet set changes. */
    public static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ChatOverhaul.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private ChatOverhaulNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(2, CircuitChasePacket.class, CircuitChasePacket::encode,
                CircuitChasePacket::new, CircuitChasePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                0,
                ClientAlertPacket.class,
                ClientAlertPacket::encode,
                ClientAlertPacket::new,
                ClientAlertPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(
                1,
                QuestSyncPacket.class,
                QuestSyncPacket::encode,
                QuestSyncPacket::new,
                QuestSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}
