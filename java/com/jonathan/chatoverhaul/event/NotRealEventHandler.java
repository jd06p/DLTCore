package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * "im not real" chat-triggered event sequence.
 *
 * Detection is an exact match (not "contains"), after trimming, lowercasing,
 * and normalizing curly apostrophes to straight ones - so "im not real",
 * "Im not real", "I'm not real" (either apostrophe style), and
 * "IM NOT REAL" all match, but a message that merely mentions the phrase
 * as part of unrelated longer text does not (matching what was asked:
 * don't fire from a substring match unless that's explicitly wanted).
 *
 * The player's own message is NOT suppressed - this only reads
 * event.getRawText() to decide whether to trigger, it never cancels the
 * event, so the message displays completely normally to everyone before
 * the sequence plays out 10 seconds later.
 *
 * The 10-second delay uses TickScheduler (the same tick-based scheduler
 * FirstJoinSequence already uses), so it's non-blocking - nothing here
 * ever sleeps or waits on the server thread.
 *
 * The player is re-fetched by UUID at the 10-second mark rather than
 * holding onto the original ServerPlayer reference, since 10 seconds is
 * plenty of time for a disconnect/reconnect (which would create a new
 * player object) - if they're gone by then, the sequence is simply
 * skipped rather than acting on a stale reference.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class NotRealEventHandler {

    private static final Set<String> TRIGGER_PHRASES = Set.of(
            "im not real",
            "i'm not real"
    );

    private static final int DELAY_TICKS = 10 * 20;
    private static final int DARKNESS_DURATION_TICKS = 10 * 20;

    private NotRealEventHandler() {}

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        String normalized = event.getRawText()
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'');

        if (!TRIGGER_PHRASES.contains(normalized)) {
            return;
        }

        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        UUID uuid = player.getUUID();
        TickScheduler.scheduleInTicks(DELAY_TICKS, () -> runSequence(server, uuid));
    }

    private static void runSequence(MinecraftServer server, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return;
        }

        server.getPlayerList().broadcastSystemMessage(Component.literal("<author> ..."), false);

        // "Distorted" via minimum pitch - matches the convention used
        // throughout this pack's own ambient.cave usage elsewhere.
        player.playNotifySound(SoundEvents.AMBIENT_CAVE.get(), SoundSource.AMBIENT, 2.0f, 0.0f);

        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_DURATION_TICKS, 0, false, false));

        player.connection.disconnect(Component.literal("console(restart_consciousness) = True"));
    }
}
