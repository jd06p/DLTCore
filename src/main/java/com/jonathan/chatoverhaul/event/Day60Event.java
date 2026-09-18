package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.stream.Collectors;

/**
 * A scripted, world-level horror sequence that plays exactly once when the
 * overworld clock reaches in-game day 60 - never replays in the same world.
 *
 * Runs on the server thread via TickScheduler (no client mods, no Mixins).
 * The sequence is:
 *
 * <ol>
 *   <li>Two reversed "<\u273A>" warning lines (3s apart).</li>
 *   <li>A 10-second silence, then /stopsound, "the end is near", and
 *       minecraft:music.end for all online players.</li>
 *   <li>12 warning lines with the players' names (3s apart).</li>
 *   <li>Darkness + the_wonderland:screenshake + the_wonderland:the_spawn_scream_new
 *       applied simultaneously with a rapid 3-cycle glitch title sequence
 *       (7 titles, 0.2s each = 4.2s total), while thebrokenscript:nullishereloop
 *       loops underneath for the whole span.</li>
 * </ol>
 *
 * WHY THE DAY COUNTER IS ZERO-INDEXED:
 * "Day 60" here means the value Minecraft's OWN displayed day counter shows.
 * The client renders that counter (the "Day %d" in the debug overlay) as
 * getDayTime() / 24000L - zero-indexed, so the first in-game day reads
 * "Day 0". Triggering on dayTime >= 59 * 24000 therefore fires while the
 * HUD still displays "Day 59"; this class instead mirrors Minecraft's own
 * formula and waits for getDayTime() / 24000L >= 60 (the moment the HUD
 * actually flips to "Day 60"). See TARGET_DISPLAYED_DAY for the full
 * walkthrough.

 * WHY nullishereloop IS STARTED ONLY ONCE:
 * nullishereloop is the continuous dread ambience The Broken Script uses as
 * a menu/background loop. Starting it once per player at the moment the
 * titles begin lets it run for the full title span on its own; restarting
 * it on every tick (or once per title frame) would stack multiple loop
 * copies on top of each other and get increasingly loud/layered. The one
 * playNotifySound call in applyFinaleEffects is the entire lifecycle.
 *
 * WHY A DEDICATED CLASS:
 * The Day 60 sequence is much longer and more elaborate than the first-join
 * or Entity303 sequences, and it's world-level (not player-level) - it
 * targets ALL online players, and it fires once per world rather than once
 * per player. A dedicated class keeps the lifecycle clear and avoids mixing
 * different concerns (player-level vs world-level) in the same handler.
 *
 * WHY TickScheduler:
 * Uses the same TickScheduler as every other event handler in this mod -
 * scheduleInTicks(...) gives absolute-tick resolution so every line and
 * effect fires at precisely the right moment, and there's no timer task to
 * clean up because every scheduled callback naturally expires after its one
 * execution.
 *
 * THE \u273A SYMBOL:
 * Every other chat broadcast in the mod (<\u2550> joining/leaving, <\u0E3F>
 * language swap, <\u273A> for Entity303 ritual dialogue) uses a unique
 * prefix character. This class uses \u273A (the "teardrop-spoked asterisk"
 * symbol the spec specifies for both dialogue and the glitch title cycle).
 *
 * THE OKLAB COLOR:
 * The spec defines the \u273A title color as "#OKLAB(0.964427 0.000418067
 * -0.00125384)". Minecraft 1.20.1's TextColor.parseColor only accepts
 * #RRGGBB hex strings or vanilla color names, not OKLAB. The OKLAB values
 * were converted to sRGB, yielding the hex value #F3F3F4 - an essentially
 * off-white that matches the ghostly near-white the spec means.
 *
 * STOPPING ALL SOUNDS:
 * Minecraft 1.20.1's ClientboundStopSoundPacket accepts nullable parameters:
 * sending (null, null) is the packet-level equivalent of the vanilla
 * /stopsound command with no arguments - it stops every sound on the client.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class Day60Event {

    // =========================================================================
    // Timing
    // =========================================================================

    private static final long TICKS_PER_DAY = 24000L;

    /**
     * The displayed day the event targets. Minecraft's own day counter (the
     * vanilla "Day %d" in the debug/HUD overlay - see
     * DebugScreenOverlay#getGameInformation) is ZERO-INDEXED: it is computed
     * as getDayTime() / 24000L with no +1, so the first in-game day is "Day
     * 0" and displayed day N means dayTime / 24000 == N. The event must fire
     * exactly when that displayed counter reads 60, i.e. when
     * dayTime / 24000 >= 60 (dayTime >= 60 * 24000).
     *
     * A naive threshold of 59 * 24000 (or an explicit "+1" in the day
     * formula) fires a whole day early, because it treats the counter as
     * one-indexed. Matching Minecraft's own day-counting behaviour (the same
     * getDayTime()/24000L the client displays) fixes the off-by-one at its
     * root instead of papering over it with a delay.
     */
    private static final long TARGET_DISPLAYED_DAY = 60L;

    /** Gap between consecutive "<\u273A>" dialogue lines (3 seconds). */
    private static final long CHAT_GAP = 3 * 20L;

    /** 10-second silence between the opening reversed lines and the main warning dialogue. */
    private static final long PAUSE_TICKS = 10 * 20L;

    /** Delay between each rapid "glitch" title frame (0.2 seconds - noticeably faster and more intense, but each title still gets a readable slice of screen time). */
    private static final long TITLE_INTERVAL = 4;

    /** How many times the 7-title sequence repeats (7 titles x 3 cycles = 21 frames, 4.2s total). */
    private static final int TITLE_CYCLES = 3;

    /** Extra ticks of Darkness/screenshake so the effects can't expire before the very last title frame. */
    private static final long EFFECT_BUFFER = 20;

    private static final char SYMBOL = '\u273A';

    // =========================================================================
    // Dialogue content
    // =========================================================================

    private static final String[] OPENING = {
            "T'NAC I DOG",
            "gnimoc s'ti"
    };

    /** null = the player-name line, filled in at runtime from the online player list. */
    private static final String[] MAIN_LINES = {
            "its coming for you soon",
            "please just listen to us",
            "you have to stop this",
            null,
            "corruption has been slowly taking control over this host",
            "it has been waiting patiently",
            "and soon it will gain complete control over this host",
            "please you have to leave now",
            "before it's too late",
            "EROMYNA FLESYM LORTNOC T'NAC I DOG",
            "HCUM OS STRUH TI",
            "WON EVAEL TSUJ ESAELP"
    };

    private static final String[] TITLE_SEQUENCE = {
            "PLEASE HELP US",
            "WE ARE IN CONSTANT AGONY",
            "THE END IS NIGH",
            "THE END IS NULL",
            "\u273A",
            "INTEGRITY IS COMING",
            "IT WAS ALL ITS FAULT"
    };

    // =========================================================================
    // Resource locations
    // =========================================================================

    private static final ResourceLocation SPAWN_SCREAM_ID = new ResourceLocation("the_wonderland", "the_spawn_scream_new");
    private static final ResourceLocation SCREENSHAKE_EFFECT_ID = new ResourceLocation("the_wonderland", "screenshake");
    /** The continuous dread ambience from The Broken Script, started ONCE when the glitch titles begin - never on every tick or per title, so the loop can never layer up into duplicates. */
    private static final ResourceLocation NULL_IS_HERE_LOOP_ID = new ResourceLocation("thebrokenscript", "nullishereloop");

    // =========================================================================
    // One-shot trigger state
    // =========================================================================

    /**
     * In-memory guard on top of the durable Day60Tracker file. The file
     * prevents retriggering across server restarts; this prevents repeated
     * file reads once the event has already fired for the current server
     * instance. Resets when the server instance changes (new world load,
     * /reload, etc.).
     */
    private static volatile MinecraftServer firedServer = null;
    private static volatile boolean fired = false;

    private Day60Event() {}

    // =========================================================================
    // Trigger
    // =========================================================================

    /**
     * Checks once per second (20 server ticks) whether the overworld's
     * displayed day has reached 60 and the event hasn't already fired. The
     * displayed day is computed exactly as Minecraft's own counter computes
     * it (getDayTime()/24000L, zero-indexed - so "Day 60" means that value
     * has reached 60, not 59). Only acts when at least one player is online
     * - the sequence targets all online players, and firing to nobody wastes
     * the one-shot flag. If day 60 passes while the server is empty, it
     * fires the next time a player is present.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 20 != 0) {
            return;
        }
        maybeTrigger(server);
    }

    private static void maybeTrigger(MinecraftServer server) {
        if (firedServer != server) {
            firedServer = server;
            fired = false;
        }
        if (fired) {
            return;
        }
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        // Mirror Minecraft's own day counter (getDayTime()/24000L, no +1) so
        // the event lands exactly on the HUD's displayed Day 60, never on 59.
        long displayedDay = server.overworld().getDayTime() / TICKS_PER_DAY;
        if (displayedDay < TARGET_DISPLAYED_DAY) {
            return;
        }
        if (Day60Tracker.alreadyTriggered(server)) {
            fired = true;
            return;
        }
        Day60Tracker.markTriggered(server);
        fired = true;
        startSequence(server);
    }

    // =========================================================================
    // Sequence
    // =========================================================================

    private static void startSequence(MinecraftServer server) {
        String playerNames = server.getPlayerList().getPlayers().stream()
                .map(p -> p.getName().getString())
                .collect(Collectors.joining(", "));
        String nameLine = playerNames + ", you don't wanna end like us";

        String[] fullMain = new String[MAIN_LINES.length];
        for (int i = 0; i < MAIN_LINES.length; i++) {
            fullMain[i] = MAIN_LINES[i] == null ? nameLine : MAIN_LINES[i];
        }

        // --- Opening reversed warning (3s apart) ---
        long t = 0;
        for (String line : OPENING) {
            t += schedule(server, t, line);
        }

        // --- 10-second silence, then stopsound + "the end is near" + music.end ---
        long pauseEnd = t + PAUSE_TICKS;
        TickScheduler.scheduleInTicks(pauseEnd, () -> stopAllSounds(server));
        schedule(server, pauseEnd, "the end is near");
        TickScheduler.scheduleInTicks(pauseEnd + 5, () -> playEndMusic(server));

        // --- Main warning dialogue (3s apart) ---
        long d = pauseEnd + CHAT_GAP;
        for (String line : fullMain) {
            d += schedule(server, d, line);
        }

        // --- Finale: Darkness + screenshake + scream + 3-cycle glitch titles ---
        long titleSpanTicks = (long) TITLE_SEQUENCE.length * TITLE_CYCLES * TITLE_INTERVAL;
        long effectsDuration = titleSpanTicks + EFFECT_BUFFER;
        long finaleStart = d + CHAT_GAP;

        // Set fade/stay/fout one tick before the first title frame arrives
        final long titleSetupTick = finaleStart > 0 ? finaleStart - 1 : 0;
        TickScheduler.scheduleInTicks(titleSetupTick,
                () -> sendTitleTimesToAll(server, 0, (int) TITLE_INTERVAL, 0));

        // Apply effects + scream + the single nullishereloop start at the
        // moment the titles begin (the loop is NOT restarted per title).
        TickScheduler.scheduleInTicks(finaleStart,
                () -> applyFinaleEffects(server, effectsDuration));

        // Schedule each title frame (7 titles x 3 cycles, TITLE_INTERVAL apart)
        long ttl = 0;
        for (int cycle = 0; cycle < TITLE_CYCLES; cycle++) {
            for (String title : TITLE_SEQUENCE) {
                final long delay = finaleStart + ttl;
                final String text = title;
                TickScheduler.scheduleInTicks(delay,
                        () -> sendTitleToAll(server, titleComponent(text)));
                ttl += TITLE_INTERVAL;
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static long schedule(MinecraftServer server, long tick, String line) {
        TickScheduler.scheduleInTicks(tick, () -> broadcastLine(server, line));
        return CHAT_GAP;
    }

    private static void broadcastLine(MinecraftServer server, String line) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("<" + SYMBOL + "> " + line).withStyle(ChatFormatting.WHITE), false);
    }

    /**
     * Stops all sounds for every online player - packet-level equivalent of
     * /stopsound with no arguments ((null, null) = stop everything).
     */
    private static void stopAllSounds(MinecraftServer server) {
        ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket((ResourceLocation) null, (SoundSource) null);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    /**
     * Plays minecraft:music.end for every online player via playNotifySound.
     * SoundEvents.MUSIC_END is a Holder&lt;SoundEvent&gt; in 1.20.1, so
     * .value() is required to get the underlying SoundEvent that playNotifySound
     * expects.
     */
    private static void playEndMusic(MinecraftServer server) {
        SoundEvent endMusic = SoundEvents.MUSIC_END.value();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(endMusic, SoundSource.MUSIC, 1.0f, 1.0f);
        }
    }

    /**
     * Applies Darkness, screenshake, and the spawn scream to every online
     * player, and starts thebrokenscript:nullishereloop exactly ONCE so it
     * plays continuously for the whole title span - never restarted per
     * title or per tick, which would layer duplicate loop copies. ambient=
     * false, visible=false means no particles - the same presentation
     * Entity303's ritual uses.
     */
    private static void applyFinaleEffects(MinecraftServer server, long duration) {
        MobEffect screenshake = ForgeRegistries.MOB_EFFECTS.getValue(SCREENSHAKE_EFFECT_ID);
        SoundEvent scream = ForgeRegistries.SOUND_EVENTS.getValue(SPAWN_SCREAM_ID);
        SoundEvent nullLoop = ForgeRegistries.SOUND_EVENTS.getValue(NULL_IS_HERE_LOOP_ID);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, (int) duration, 0, false, false));
            if (screenshake != null) {
                player.addEffect(new MobEffectInstance(screenshake, (int) duration, 0, false, false));
            }
            if (scream != null) {
                player.playNotifySound(scream, SoundSource.HOSTILE, 2.0f, 1.0f);
            }
            // Single start of the looping dread ambience for the title span.
            // Volume/pitch match how the Integrity bossfight starts the same
            // sound; SoundSource.MASTER keeps it audible above the music.end.
            if (nullLoop != null) {
                player.playNotifySound(nullLoop, SoundSource.MASTER, 0.6f, 1.0f);
            }
        }
    }

    /**
     * The \u273A title is rendered in #F3F3F4 (the sRGB equivalent of the
     * spec's OKLAB color). All other titles render in the default white.
     */
    private static Component titleComponent(String text) {
        if ("\u273A".equals(text)) {
            return Component.literal(text).withStyle(
                    Style.EMPTY.withColor(TextColor.fromRgb(0xF3F3F4)));
        }
        return Component.literal(text);
    }

    private static void sendTitleTimesToAll(MinecraftServer server, int fadeIn, int stay, int fadeOut) {
        ClientboundSetTitlesAnimationPacket packet = new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    private static void sendTitleToAll(MinecraftServer server, Component title) {
        ClientboundSetTitleTextPacket packet = new ClientboundSetTitleTextPacket(title);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }
}