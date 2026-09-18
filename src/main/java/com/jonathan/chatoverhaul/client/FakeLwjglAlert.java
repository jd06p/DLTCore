package com.jonathan.chatoverhaul.client;

import com.jonathan.chatoverhaul.network.ClientAlertPacket;
import com.mojang.blaze3d.platform.Window;
import com.sun.jna.Library;
import com.sun.jna.Native;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-side fake technical-error window, the exact mechanism The Broken
 * Script RUS Patch and the Integrity bossfight mod already use:
 *
 * <ul>
 *   <li>Windowed mode: {@code Window.isFullscreen()} -> {@code toggleFullScreen()}
 *       (identical to TBS's {@code SetWindowedPacket}).</li>
 *   <li>Fake "LWJGL Alert": a native Windows {@code MessageBoxA} via JNA,
 *       caption "LWJGL Alert" (identical to the RUS Patch {@code ErrorPopup}
 *       and Integrity's {@code IntegrityWindowFx}).</li>
 * </ul>
 *
 * Triggered by the server through {@link ClientAlertPacket} on Entity303's
 * death: the client first drops to windowed mode, then (on a daemon thread,
 * so the render thread is never blocked by the modal box) displays exactly
 * one alert:
 *
 * <pre>I WILL BE BACK</pre>
 *
 * This class is inherently client-only and is only ever loaded on the client
 * (guarded by DistExecutor in the packet handler), so a dedicated server
 * never touches it. Any failure here (e.g. running on a non-Windows
 * platform) is deliberately swallowed - these are cosmetic effects and must
 * never take the player's client down.
 */
@OnlyIn(Dist.CLIENT)
public final class FakeLwjglAlert {

    private static final long ALERT_DELAY_MS = 450;

    private FakeLwjglAlert() {
    }

    /**
     * Spawns a daemon thread that performs the windowed-mode switch and the
     * alert; the caller (the packet handler's enqueued work on the client
     * thread) returns immediately.
     */
    public static void trigger(int kind) {
        try {
            forceWindowed();
            Thread fxThread = new Thread(() -> showAlerts(kind), "ChatOverhaul-FakeLwjglAlert");
            fxThread.setDaemon(true);
            fxThread.start();
        } catch (Throwable t) {
            // Never let cosmetic effects take the client down.
        }
    }

    private static void forceWindowed() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (window.isFullscreen()) {
            window.toggleFullScreen();
        }
    }

    private static void showAlerts(int kind) {
        try {
            Thread.sleep(ALERT_DELAY_MS);
            if (kind == ClientAlertPacket.KIND_ENTITY303_DEATH) {
                showAlert("I WILL BE BACK");
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void showAlert(String message) {
        try {
            User32.INSTANCE.MessageBoxA(0L, message, "LWJGL Alert", 0);
        } catch (Throwable t) {
            // If the native call fails (e.g. non-Windows platform), stay quiet.
        }
    }

    /**
     * Minimal JNA binding for user32!MessageBoxA - the same call the RUS Patch
     * ErrorPopup and Integrity's IntegrityWindowFx use. hWnd is declared
     * 64-bit to match HWND on x64 Windows.
     */
    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);

        int MessageBoxA(long hWnd, String lpText, String lpCaption, int uType);
    }
}