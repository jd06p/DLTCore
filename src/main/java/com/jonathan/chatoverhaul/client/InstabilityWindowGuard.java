package com.jonathan.chatoverhaul.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Client-side guard for the INSTABILITY music-disc's permanently stuck window
 * corruption, injected at the head of
 * {@code GItchtimerupdateProcedure.execute(Event, LevelAccessor)} by
 * {@code mixin/instability/GItchtimerupdateProcedureMixin}. This class is
 * strictly client-only and is never touched on a dedicated server (the mixin
 * lives in the "client" array of its config, and every entry point is guarded
 * by an {@code isClientSide()} check).
 *
 * <p>Root cause (proven from INSTABILITY's bytecode, not guessed): the disc's
 * {@code onPlayerTick} handler fires on every player tick and, once
 * {@code InstabilityRightclickedOnBlockProcedure} has set {@code isDiscPlaying}
 * (nothing ever resets it), force-applies the same torn window state forever:
 * windowed {@code 854x480} with the hardcoded title
 * {@code "Minecraft Forge* 1.20.1 - Singleplayer"}. The full glitch (jitter,
 * shaking, invert/creeper shaders, scary titles, chat, the fake dialog) plays
 * out for exactly {@link #SETTLE_TICKS} of the mod's own {@code discPlayTicks}
 * counter, then parks the window in the torn state with no restore path, so a
 * player's original window mode, size, position and title are lost for the
 * rest of the game session.
 *
 * <p>This guard lets the entire scripted sequence play out and only acts when
 * the effect is genuinely over. It keys the restore on the mod's own lifecycle
 * counter every tick ({@code discPlayTicks >= SETTLE_TICKS}) rather than on
 * window geometry or an arbitrary timeout, because the mod's quiet intro phase
 * (ticks 0-1040) parks the window in a state that is geometrically identical
 * to the final settle state - a geometry-only signal fires far too early and
 * cuts the effect short. The mod's own counter (read through reflection, so
 * there is no compile-time/runtime dependency on the mod) is the only reliable
 * "this effect actually reached its end-script" signal; {@link #SETTLE_TICKS}
 * is the mod's own final-phase constant taken from its bytecode.
 *
 * <p>Restore paths:
 * <ul>
 *   <li>Completed effect: {@code discPlayTicks >= 5540} for
 *       {@link #CONFIRM_TICKS} consecutive settled ticks (the mod's settle
 *       phase holds the window forever). Then the mod is cancelled so it can
 *       never re-tear the window.</li>
 *   <li>Leftover corruption: the window is torn (windowed {@code 854x480})
 *       while the mod reports {@code isDiscPlaying == false} - e.g. leaving
 *       mid-glitch, or booting into a save that was abandoned while torn.
 *       Restored from saved options after {@link #CONFIRM_TICKS} ticks.</li>
 * </ul>
 *
 * <p>Restore strategy, decided once at the tick the guard first engages:
 * <ul>
 *   <li>Fresh trigger - the mod's {@code discPlayTicks} was 0 when the guard
 *       first saw the disc and the window was not already torn: the exact
 *       original state is snapshotted and restored - fullscreen comes back on
 *       the same monitor, windowed comes back to the same size and position -
 *       plus the game's own title via {@code Minecraft.updateTitle()}.</li>
 *   <li>Already-corrupted session (the disc was left playing in an earlier
 *       launch, or a repeat trigger): the true original is unknowable, so the
 *       window is recovered from saved options - {@code Options.fullscreen}
 *       decides mode, {@code overrideWidth/overrideHeight} (the saved windowed
 *       resolution) is used when present, and the window is re-centered on the
 *       primary monitor.</li>
 * </ul>
 *
 * <p>The guard records a restored effect and keeps cancelling the mod only for
 * as long as that same effect is in its forever-settling tail
 * ({@code isDiscPlaying == true && discPlayTicks >= SETTLE_TICKS}). A truly
 * fresh effect in another world (new save, fresh data: {@code discPlayTicks}
 * back below {@link #SETTLE_TICKS}) re-arms the guard and snapshots the new
 * pristine state, so repeated disc uses keep working without stale state.
 *
 * <p>If the reflective read ever fails (mod class renamed, etc.) the guard
 * degrades to a pure-geometry fallback: the restore is gated on how long the
 * window has been continuously torn (the mod tears it from disc-tick 0 and
 * holds it, so the guard's own torn-duration counter tracks
 * {@code discPlayTicks})
 * plus the settled confirm streak - never a premature cutoff - and the
 * leftover path is disabled (a torn window during the intro cannot be told
 * apart from a stuck one). Every failure path is swallowed - these are
 * cosmetic fixes and must never take the player's client down.
 */
@OnlyIn(Dist.CLIENT)
public final class InstabilityWindowGuard {

    private static final int WINDOWED_W = 854;
    private static final int WINDOWED_H = 480;

    /**
     * Consecutive settled ticks required before restoring. 60 ticks = 3
     * seconds at 20 tps: long enough to ride out any single-frame glitch,
     * short enough to react quickly once the effect is truly over.
     */
    private static final int CONFIRM_TICKS = 60;

    /**
     * INSTABILITY's own scripted-playback ceiling: its settle phase (end of
     * the script, at which point it parks the torn 854x480 window forever)
     * starts when its own {@code discPlayTicks} counter reaches this value.
     * Verified against the mod's bytecode (sipush 5540). Keying the restore on
     * this - not on geometry or a timeout - is what lets the full glitch play
     * out while still guaranteeing a clean recovery, and what excludes the
     * mod's geometrically-identical intro pause (ticks 0-1040).
     */
    private static final double SETTLE_TICKS = 5540.0d;

    private static final int POS_TOLERANCE = 8;
    private static final int SIZE_TOLERANCE = 2;
    private static final int FALLBACK_WINDOWED_W = 1280;
    private static final int FALLBACK_WINDOWED_H = 720;

    private static final String MAP_VARIABLES_CLASS =
            "net.mcreator.instabilitymusicdisk.network.InstabilitymusicdiskModVariables$MapVariables";

    private static boolean restored;
    private static boolean engaged;
    private static boolean pristine;
    private static boolean snapshotValid;
    private static boolean reflectionBroken;
    private static int settleStreak;
    private static int leftoverStreak;
    private static int tornStreak;

    private static long origMonitor;
    private static int origX;
    private static int origY;
    private static int origW;
    private static int origH;
    private static int origVideoW;
    private static int origVideoH;
    private static int origVideoRefresh;

    private static Class<?> mapVariables;
    private static Method mapVariablesGet;
    private static Field discPlayTicks;
    private static Field isDiscPlaying;

    private InstabilityWindowGuard() {
    }

    /**
     * Called from the mixin on every player tick (before the procedure's body,
     * which itself no-ops when {@code isDiscPlaying} is false). Returns whether
     * the procedure call should be cancelled: only while a fully-restored
     * effect is in its forever-settling tail, so the mod can never re-apply its
     * corruption, and never while an effect is still playing out.
     */
    public static boolean shouldCancel(LevelAccessor level) {
        try {
            return tick(level);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean tick(LevelAccessor level) {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        long handle = window.getWindow();
        if (handle == 0L || GLFW.glfwGetCurrentContext() == 0L) {
            return restored;
        }

        InstabilityState state = readState(level);
        Double playTicks = state == null ? null : state.discPlayTicks;
        Boolean playing = state == null ? null : state.isDiscPlaying;

        boolean torn = isTornSignature(handle);
        tornStreak = torn ? tornStreak + 1 : 0;

        // A restored effect was replaced by a genuinely fresh one (a new
        // save/world starts at discPlayTicks 0): re-arm rather than stay frozen.
        if (restored && playing == Boolean.TRUE && playTicks != null && playTicks < SETTLE_TICKS) {
            rearm();
        }

        if (!engaged) {
            engaged = true;
            decideMode(level, window, handle, torn);
        }

        boolean settled = false;
        if (torn) {
            int[] xPos = new int[1];
            int[] yPos = new int[1];
            GLFW.glfwGetWindowPos(handle, xPos, yPos);
            settled = isCentered(xPos[0], yPos[0]);
        }
        settleStreak = settled ? settleStreak + 1 : 0;
        if (playing == null) {
            leftoverStreak = 0;
        } else if (torn && !playing) {
            leftoverStreak++;
        } else {
            leftoverStreak = 0;
        }

        // Keep cancelling only while the same restored effect is still in its
        // settle tail; an inert world falls through so a fresh leftover or a
        // fresh effect in another world can still be handled below.
        if (restored
                && (reflectionBroken
                        || (playing == Boolean.TRUE && playTicks != null && playTicks >= SETTLE_TICKS))) {
            return true;
        }

        boolean completed =
                playTicks != null && playTicks >= SETTLE_TICKS && settleStreak >= CONFIRM_TICKS;
        boolean leftover = playing != null && !playing && leftoverStreak >= CONFIRM_TICKS;
        boolean fallbackCompleted =
                playTicks == null && tornStreak >= (long) SETTLE_TICKS && settleStreak >= CONFIRM_TICKS;

        if (completed || leftover || fallbackCompleted) {
            restore(window, handle);
            restored = true;
            return completed || fallbackCompleted;
        }
        return false;
    }

    private static void rearm() {
        restored = false;
        engaged = false;
        pristine = false;
        snapshotValid = false;
        settleStreak = 0;
        leftoverStreak = 0;
        tornStreak = 0;
    }

    private static void decideMode(LevelAccessor level, Window window, long handle, boolean tornAtEngage) {
        boolean freshFromTicks = false;
        if (!reflectionBroken) {
            InstabilityState state = readState(level);
            freshFromTicks = state != null && state.discPlayTicks != null && state.discPlayTicks < 0.5d;
        }
        if (reflectionBroken) {
            pristine = !tornAtEngage;
        } else {
            pristine = freshFromTicks && !tornAtEngage;
        }
        if (pristine) {
            snapshot(window, handle);
        }
    }

    private static void snapshot(Window window, long handle) {
        int[] xPos = new int[1];
        int[] yPos = new int[1];
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetWindowPos(handle, xPos, yPos);
        GLFW.glfwGetWindowSize(handle, width, height);
        origX = xPos[0];
        origY = yPos[0];
        origW = width[0];
        origH = height[0];
        origMonitor = GLFW.glfwGetWindowMonitor(handle);
        if (origMonitor != 0L) {
            GLFWVidMode mode = GLFW.glfwGetVideoMode(origMonitor);
            if (mode != null) {
                origVideoW = mode.width();
                origVideoH = mode.height();
                origVideoRefresh = mode.refreshRate();
                snapshotValid = true;
            } else {
                snapshotValid = false;
            }
        } else {
            snapshotValid = true;
        }
    }

    private static boolean isTornSignature(long handle) {
        if (GLFW.glfwGetWindowMonitor(handle) != 0L) {
            return false;
        }
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetWindowSize(handle, width, height);
        return Math.abs(width[0] - WINDOWED_W) <= SIZE_TOLERANCE
                && Math.abs(height[0] - WINDOWED_H) <= SIZE_TOLERANCE;
    }

    private static boolean isCentered(int x, int y) {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        GLFWVidMode mode = monitor == 0L ? null : GLFW.glfwGetVideoMode(monitor);
        if (mode == null) {
            return false;
        }
        int centerX = (mode.width() - WINDOWED_W) / 2;
        int centerY = (mode.height() - WINDOWED_H) / 2;
        return Math.abs(x - centerX) <= POS_TOLERANCE && Math.abs(y - centerY) <= POS_TOLERANCE;
    }

    private static void restore(Window window, long handle) {
        if (pristine && snapshotValid) {
            restoreFromSnapshot(handle);
        } else {
            restoreFromOptions(handle);
        }
        Minecraft.getInstance().updateTitle();
    }

    private static void restoreFromSnapshot(long handle) {
        if (origMonitor != 0L) {
            GLFW.glfwSetWindowMonitor(handle, origMonitor, 0, 0, origVideoW, origVideoH, origVideoRefresh);
        } else {
            GLFW.glfwSetWindowSize(handle, origW, origH);
            GLFW.glfwSetWindowPos(handle, origX, origY);
        }
    }

    private static void restoreFromOptions(long handle) {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        GLFWVidMode mode = monitor == 0L ? null : GLFW.glfwGetVideoMode(monitor);
        if (mode == null) {
            return;
        }
        Options options = Minecraft.getInstance().options;
        if (options.fullscreen().get()) {
            GLFW.glfwSetWindowMonitor(handle, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
        } else {
            int width = options.overrideWidth > 0 ? options.overrideWidth : FALLBACK_WINDOWED_W;
            int height = options.overrideHeight > 0 ? options.overrideHeight : FALLBACK_WINDOWED_H;
            GLFW.glfwSetWindowSize(handle, width, height);
            GLFW.glfwSetWindowPos(handle, (mode.width() - width) / 2, (mode.height() - height) / 2);
        }
    }

    /**
     * Per-tick read of the mod's own state through reflection. Returns null
     * (and permanently marks reflection broken) on any failure so the guard
     * degrades to the geometry-based fallback instead of failing loudly.
     */
    private static InstabilityState readState(LevelAccessor level) {
        if (reflectionBroken) {
            return null;
        }
        try {
            if (mapVariables == null) {
                mapVariables = Class.forName(MAP_VARIABLES_CLASS);
                mapVariablesGet = mapVariables.getMethod("get", LevelAccessor.class);
                discPlayTicks = mapVariables.getField("discPlayTicks");
                isDiscPlaying = mapVariables.getField("isDiscPlaying");
            }
            Object variables = mapVariablesGet.invoke(null, level);
            if (variables == null) {
                return new InstabilityState(null, null);
            }
            return new InstabilityState(discPlayTicks.getDouble(variables), isDiscPlaying.getBoolean(variables));
        } catch (Throwable t) {
            reflectionBroken = true;
            return null;
        }
    }

    private static final class InstabilityState {

        private final Double discPlayTicks;
        private final Boolean isDiscPlaying;

        private InstabilityState(Double discPlayTicks, Boolean isDiscPlaying) {
            this.discPlayTicks = discPlayTicks;
            this.isDiscPlaying = isDiscPlaying;
        }
    }
}