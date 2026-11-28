package com.jonathan.chatoverhaul.network;

import com.jonathan.chatoverhaul.client.CriticalBarOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record CriticalBarSyncPacket(boolean enabled, int progress, int capacity, boolean active) {
    public CriticalBarSyncPacket(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeVarInt(progress);
        buf.writeVarInt(capacity);
        buf.writeBoolean(active);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> CriticalBarOverlay.apply(this)));
        context.setPacketHandled(true);
    }
}
