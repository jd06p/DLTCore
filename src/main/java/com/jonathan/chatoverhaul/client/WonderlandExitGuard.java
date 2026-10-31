package com.jonathan.chatoverhaul.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;

/** Client-side exit guard based on the server-synchronized local player dimension. */
@OnlyIn(Dist.CLIENT)
public final class WonderlandExitGuard {
    private static final String WONDERLAND_NAMESPACE = "the_wonderland";
    private static final String ALERT_MESSAGE = "<o>";
    private static final AtomicBoolean ALERT_OPEN = new AtomicBoolean();

    private WonderlandExitGuard() {}

    public static boolean isInsideWonderland() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return false;
        ResourceLocation dimension = minecraft.player.level().dimension().location();
        return WONDERLAND_NAMESPACE.equals(dimension.getNamespace());
    }

    public static void updateLeaveButton(PauseScreen screen) {
        boolean disabled = isInsideWonderland();
        for (var listener : screen.children()) {
            if (!(listener instanceof Button button)) continue;
            if (!(button.getMessage().getContents() instanceof TranslatableContents translated)) continue;
            String key = translated.getKey();
            if ("menu.returnToMenu".equals(key) || "menu.disconnect".equals(key) || "menu.quit".equals(key)) {
                button.active = !disabled;
            }
        }
    }

    /** Called before Minecraft consumes GLFW's close flag in its game loop. */
    public static void interceptWindowClose() {
        if (!isInsideWonderland()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        long handle = window.getWindow();
        if (handle == 0L || !GLFW.glfwWindowShouldClose(handle)) return;

        // Consume the OS close request so Minecraft's normal loop keeps running.
        GLFW.glfwSetWindowShouldClose(handle, false);
        showAlertOnce();
    }

    private static void showAlertOnce() {
        if (!ALERT_OPEN.compareAndSet(false, true)) return;
        Thread alertThread = new Thread(() -> {
            try {
                FakeLwjglAlert.showMessage(ALERT_MESSAGE);
            } catch (Throwable ignored) {
                // Native Windows alerts are unavailable on some platforms; keep the client running.
            } finally {
                ALERT_OPEN.set(false);
            }
        }, "DLTCore-WonderlandExitAlert");
        alertThread.setDaemon(true);
        alertThread.start();
    }
}
