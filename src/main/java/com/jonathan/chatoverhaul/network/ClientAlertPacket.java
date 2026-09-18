package com.jonathan.chatoverhaul.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-bound trigger sent by the server for cosmetic, client-only FX
 * sequences. Carries only a primitive {@code kind} selector; the common side
 * contains no client classes, so a dedicated server can build and send it
 * freely. The client-only work (opening the fake "LWJGL Alert" window) is
 * deferred through DistExecutor and is only ever resolved on a client.
 */
public class ClientAlertPacket {

    /** Entity303 (evil_user_0) killed: exactly one fake "LWJGL Alert" reading "I WILL BE BACK". */
    public static final int KIND_ENTITY303_DEATH = 0;

    private final int kind;

    public ClientAlertPacket() {
        this.kind = KIND_ENTITY303_DEATH;
    }

    public ClientAlertPacket(int kind) {
        this.kind = kind;
    }

    public ClientAlertPacket(FriendlyByteBuf buf) {
        this.kind = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.kind);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isClient()) {
                int kind = this.kind;
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.jonathan.chatoverhaul.client.FakeLwjglAlert.trigger(kind));
            }
        });
        ctx.setPacketHandled(true);
    }
}