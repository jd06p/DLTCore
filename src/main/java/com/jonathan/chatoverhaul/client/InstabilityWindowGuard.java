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
 * {@code onPlayerTick} handler runs forever once
 * {@code InstabilityRightclickedOnBlockProcedure} sets {@code isDiscPlaying}
 * (nothing ever resets it), and with every tick the procedure force-applies
 * the same torn window state: windowed {@code 854x480}, centered, with the
 * hardcoded title {@code "Minecraft Forge* 1.20.1 - Singleplayer"} (phases
 * before tick 5540 shake, jitter, apply the invert/creeper shaders and rename
 * the window instead). It never restores anything, so a player's original
 * window mode, size, position and title are lost.
 *
 * <p>This guard lets the full intended glitch play out (particles, rain, time
 * change, chat, shaders, scary titles, the fake error dialog - all preserved)
 * and only acts once the effect has actually settled: the INSTABILITY settle
 * phase parks the window at the centered 854x480 state and holds it there
 * forever, so the guard detects a long, uninterrupted run of that exact window
 * state ({@link #SETTLE_HOLD_TICKS} - long enough to clear the ~160-tick
 * centered pause the intro plays well before the real settle), restores the
 * player's real window, and then cancels the procedure forever via the mixin,
 * so the corruption can never re-apply.
 *
 * <p>Restore strategy:
 * <ul>
 *   <li>Fresh trigger - the mod's own {@code discPlayTicks} was 0 when the
 *       guard first saw the disc (read once through reflection, no
 *       compile-time/runtime dependency on the mod): the window is still
 *       pristine, so the exact original state is snapshotted and restored -
 *       fullscreen comes back on the same monitor, windowed comes back to the
 *       same size and position - plus the game's own title via
 *       {@code Minecraft.updateTitle()}.</li>
 *   <li>Already-corrupted session (the disc was left playing in an earlier
 *       launch, or a repeat trigger): the true original is unknowable, so the
 *       window is recovered from saved options - {@code Options.fullscreen}
 *       decides mode, {@code overrideWidth/overrideHeight} (the saved windowed
 *       resolution) is used when present, and the window is re-centered.</li>
 * </ul>
 *
 * <p>If the reflective read ever fails (mod class renamed, etc.) the guard
 * degrades to judging "pristine" from the window state itself and still
 * recovers; every failure path is swallowed - these are cosmetic fixes and
 * must never take the player's client down.
 */
@OnlyIn(Dist.CLIENT)
public final class InstabilityWindowGuard {

    private static final int WINDOWED_W = 854;
    private static final int WINDOWED_H = 480;

    /**
     * Consecutive settled-signature ticks required before restoring. The mod's
     * settle phase holds the window in that state forever, so 400 ticks (20s)
     * is comfortably past the ~160-tick centered pause in its intro phase - the
     * only other time the window ever looks identical while the disc plays.
     */
    private static final int SETTLE_HOLD_TICKS = 400;
    private static final int POS_TOLERANCE = 8;
    private static final int SIZE_TOLERANCE = 2;
    private static final int FALLBACK_WINDOWED_W = 1280;
    private static final int FALLBACK_WINDOWED_H = 720;

    private static final String MAP_VARIABLES_CLASS =
            "net.mcreator.instabilitymusicdisk.network.InstabilitymusicdiskModVariables$MapVariables";

    private static boolean sealed;
    private static boolean engaged;
    private static boolean pristine;
    private static boolean snapshotValid;
    private static int settleStreak;

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
    private static boolean reflectionBroken;

    private InstabilityWindowGuard() {
    }

    /**
     * Called from the mixin on every tick the INSTABILITY procedure is about to
     * run. Returns true (cancelling the procedure) only after the window has
     * been restored once.
     */
    public static boolean shouldCancel(LevelAccessor level) {
        if (sealed) {
            return true;
        }
        try {
            tick(level);
        } catch (Throwable t) {
            sealed = true;
        }
        return sealed;
    }

    private static void tick(LevelAccessor level) {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        long handle = window.getWindow();
        if (handle == 0L || GLFW.glfwGetCurrentContext() == 0L) {
            return;
        }
        if (!engaged) {
            engaged = true;
            decideMode(level, window, handle);
        }
        int[] xPos = new int[1];
        int[] yPos = new int[1];
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetWindowPos(handle, xPos, yPos);
        GLFW.glfwGetWindowSize(handle, width, height);
        boolean settled = isSettledSignature(handle, xPos[0], yPos[0], width[0], height[0]);
        settleStreak = settled ? settleStreak + 1 : 0;
        if (settleStreak >= SETTLE_HOLD_TICKS) {
            restore(window, handle);
            sealed = true;
        }
    }

    private static void decideMode(LevelAccessor level, Window window, long handle) {
        if (!reflectionBroken) {
            try {
                pristine = phaseAtEngage(level) < 0.5d;
            } catch (Throwable t) {
                reflectionBroken = true;
            }
        }
        if (reflectionBroken) {
            pristine = !sourceAlreadyCorrupted(window, handle);
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

    private static boolean sourceAlreadyCorrupted(Window window, long handle) {
        if (window.isFullscreen() && GLFW.glfwGetWindowMonitor(handle) == 0L) {
            return true;
        }
        int[] xPos = new int[1];
        int[] yPos = new int[1];
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetWindowPos(handle, xPos, yPos);
        GLFW.glfwGetWindowSize(handle, width, height);
        return isSettledSignature(handle, xPos[0], yPos[0], width[0], height[0]);
    }

    private static boolean isSettledSignature(long handle, int x, int y, int width, int height) {
        if (GLFW.glfwGetWindowMonitor(handle) != 0L) {
            return false;
        }
        if (Math.abs(width - WINDOWED_W) > SIZE_TOLERANCE || Math.abs(height - WINDOWED_H) > SIZE_TOLERANCE) {
            return false;
        }
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

    private static double phaseAtEngage(LevelAccessor level) throws ReflectiveOperationException {
        if (mapVariables == null) {
            mapVariables = Class.forName(MAP_VARIABLES_CLASS);
            mapVariablesGet = mapVariables.getMethod("get", LevelAccessor.class);
            discPlayTicks = mapVariables.getField("discPlayTicks");
        }
        Object variables = mapVariablesGet.invoke(null, level);
        if (variables == null) {
            return 0.0d;
        }
        return discPlayTicks.getDouble(variables);
    }
}