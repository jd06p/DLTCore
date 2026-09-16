package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Plays a scripted "DeltaQuest" chat sequence the first time a player ever
 * joins the world.
 *
 * "Already seen" state lives in FirstJoinTracker - see that class for why
 * this moved off player.getPersistentData() (a version that used that,
 * with a forced save() call, still let the sequence replay after certain
 * kicks/crashes). FirstJoinTracker#markSeen() is synchronous: the file
 * write is on disk before this method returns, well before any of the
 * sequence's 9 lines even start playing - not queued for a future
 * autosave or disconnect.
 *
 * No in-memory "already in progress this session" guard is needed anymore
 * either: hasSeenIntro()/markSeen() do a real file read/write on every
 * call rather than trusting a cached value, so even a same-JVM double-login
 * edge case would correctly see the just-written state on its very next
 * check - there's no window where an in-memory cache could be stale.
 *
 * All 9 lines are scheduled as absolute tick offsets from login, via
 * TickScheduler, so nothing here depends on KubeJS at all.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class FirstJoinSequence {

    private static final char SYMBOL = '\uA55A';

    private FirstJoinSequence() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        UUID uuid = player.getUUID();
        if (FirstJoinTracker.hasSeenIntro(server, uuid)) return;

        // Durable write happens right here, synchronously, before anything
        // else - see FirstJoinTracker for why.
        FirstJoinTracker.markSeen(server, uuid);

        long t = 0;

        t += seconds(30);
        broadcastAt(server, t, Component.literal("[@] Running worldscan.. (at srv_\u2588 \u2588 \u2588 \u2588 \u2588)"));

        t += seconds(4);
        broadcastAt(server, t, Component.literal("[@] Host is available for testing!"));

        t += seconds(1);
        broadcastAt(server, t, Component.literal("[@] Full Worldlink established"));

        t += seconds(15);
        broadcastAt(server, t, Component.literal(SYMBOL + " joined the game").withStyle(ChatFormatting.YELLOW));

        t += seconds(9);
        broadcastAt(server, t, Component.literal("<" + SYMBOL + "> Access to DeltaQuest servers has now been granted to you.")
                .withStyle(ChatFormatting.WHITE));

        t += seconds(4);
        broadcastAt(server, t, Component.literal("<" + SYMBOL + "> Please remember: do not record any gameplay footage or take screenshots of DeltaQuestRPG.")
                .withStyle(ChatFormatting.WHITE));

        t += seconds(4);
        broadcastAt(server, t, Component.literal("<" + SYMBOL + "> Failure to comply with this rule will result in the immediate termination of your account.")
                .withStyle(ChatFormatting.WHITE));

        t += seconds(5);
        broadcastAt(server, t, Component.literal("<" + SYMBOL + "> Farewell.").withStyle(ChatFormatting.WHITE));

        t += seconds(8);
        broadcastAt(server, t, Component.literal(SYMBOL + " left the game").withStyle(ChatFormatting.YELLOW));
    }

    private static long seconds(int s) {
        return s * 20L;
    }

    private static void broadcastAt(MinecraftServer server, long delayTicks, Component message) {
        TickScheduler.scheduleInTicks(delayTicks, () -> server.getPlayerList().broadcastSystemMessage(message, false));
    }
}
