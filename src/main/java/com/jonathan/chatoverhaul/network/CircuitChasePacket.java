package com.jonathan.chatoverhaul.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record CircuitChasePacket(ResourceLocation dimension, boolean active, double frame, boolean flash) {
    public CircuitChasePacket(FriendlyByteBuf buf) {
        this(buf.readResourceLocation(), buf.readBoolean(), buf.readDouble(), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeBoolean(active);
        buf.writeDouble(frame);
        buf.writeBoolean(flash);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.jonathan.chatoverhaul.client.CircuitChaseState.accept(this)));
        context.setPacketHandled(true);
    }
}
