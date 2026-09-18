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
 * triggers. Currently carries exactly one message type, the client-bound
 * {@link ClientAlertPacket}, which lets the server ask every player's client
 * to run a cosmetic window (see client/FakeLwjglAlert) without the server
 * itself ever touching any client class.
 *
 * Everything in this class is common-side; the channel and packet are
 * registered once from the mod constructor, which runs on both physical
 * sides. The client-only work happens inside the packet handler under
 * DistExecutor, so a dedicated server can send these packets freely and the
 * client classes are never loaded there.
 */
public final class ChatOverhaulNetwork {

    /** Bumped only if the wire layout of any registered message changes. */
    public static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ChatOverhaul.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private ChatOverhaulNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(
                0,
                ClientAlertPacket.class,
                ClientAlertPacket::encode,
                ClientAlertPacket::new,
                ClientAlertPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}