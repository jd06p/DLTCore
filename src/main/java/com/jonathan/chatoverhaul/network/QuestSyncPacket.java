package com.jonathan.chatoverhaul.network;

import com.jonathan.chatoverhaul.client.ClientQuestState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-bound snapshot of a single player's quest state (active quest index,
 * progress, and the complete/claimed flags), sent only to the owning player
 * over the chatoverhaul channel whenever the state changes (init, progress,
 * completion, claim) and re-sent on login/respawn so the client HUD is always
 * current. The client uses it purely for HUD rendering; all authoritative
 * state, progress counting and reward distribution stay on the server.
 *
 * Mirrors {@link ClientAlertPacket}: the client-only cache update runs under
 * DistExecutor, so a dedicated server can keep this packet registered and
 * send it freely without ever loading a client class.
 */
public class QuestSyncPacket {

    private final int questIndex;
    private final int progress;
    private final boolean complete;
    private final boolean claimed;

    public QuestSyncPacket(int questIndex, int progress, boolean complete, boolean claimed) {
        this.questIndex = questIndex;
        this.progress = progress;
        this.complete = complete;
        this.claimed = claimed;
    }

    public QuestSyncPacket(FriendlyByteBuf buf) {
        this.questIndex = buf.readVarInt();
        this.progress = buf.readVarInt();
        this.complete = buf.readBoolean();
        this.claimed = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.questIndex);
        buf.writeVarInt(this.progress);
        buf.writeBoolean(this.complete);
        buf.writeBoolean(this.claimed);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isClient()) {
                int index = this.questIndex;
                int progress = this.progress;
                boolean complete = this.complete;
                boolean claimed = this.claimed;
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ClientQuestState.apply(index, progress, complete, claimed));
            }
        });
        ctx.setPacketHandled(true);
    }
}