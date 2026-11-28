package com.jonathan.chatoverhaul.client;

import com.jonathan.chatoverhaul.ChatOverhaul;
import com.jonathan.chatoverhaul.client.CriticalBarHudConfig.Anchor;
import com.jonathan.chatoverhaul.client.CriticalBarHudConfig.Appearance;
import com.jonathan.chatoverhaul.client.CriticalBarHudConfig.Style;
import com.jonathan.chatoverhaul.network.CriticalBarSyncPacket;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CriticalBarOverlay {
    private static final int EDGE_MARGIN = 10;
    private static final int HOTBAR_GAP = 3;
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int LEFT_OFFHAND_WIDTH = 29;
    private static final int LEFT_ATTACK_INDICATOR_WIDTH = 22;
    private static volatile Appearance appearance;
    private static CriticalBarSyncPacket snapshot;

    private CriticalBarOverlay() {}

    public static void apply(CriticalBarSyncPacket packet) {
        snapshot = packet;
    }

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        readAppearance(event);
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        readAppearance(event);
    }

    private static void readAppearance(ModConfigEvent event) {
        if (event.getConfig().getSpec() == ChatOverhaulConfig.SPEC) {
            appearance = ChatOverhaulConfig.CRITICAL_BAR.read();
        }
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "critical_bar",
                (gui, graphics, partialTick, width, height) -> render(gui.getMinecraft(), graphics, width, height));
    }

    private static void render(Minecraft mc, GuiGraphics graphics, int width, int height) {
        CriticalBarSyncPacket state = snapshot;
        Appearance options = appearance;
        if (state == null || !state.enabled() || state.capacity() <= 0 || options == null
                || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()
                || mc.options.hideGui || !(mc.getCameraEntity() instanceof Player cameraPlayer)) {
            return;
        }

        int barWidth = Math.min(options.width(), width - 2 * EDGE_MARGIN);
        int verticalMargin = options.glow() ? 1 : 0;
        int barHeight = Math.min(options.height(), height - 2 * verticalMargin);
        int left;
        int top;
        Anchor anchor = options.anchor();
        if (anchor == Anchor.HOTBAR_LEFT) {
            int occupiedLeft = hotbarOccupiedLeft(mc, cameraPlayer, width);
            int defaultRight = occupiedLeft - HOTBAR_GAP;
            if (defaultRight - EDGE_MARGIN < 3 || height < HOTBAR_HEIGHT) {
                return;
            }
            int gap = options.glow() ? 2 : 1;
            int right = Mth.clamp(defaultRight + options.xOffset(), EDGE_MARGIN + 3, occupiedLeft - gap);
            barWidth = Math.min(barWidth, right - EDGE_MARGIN);
            left = right - barWidth;
            top = height - HOTBAR_HEIGHT + (HOTBAR_HEIGHT - barHeight) / 2;
        } else {
            int horizontalSpace = width - 2 * EDGE_MARGIN - barWidth;
            int verticalSpace = height - 2 * EDGE_MARGIN - barHeight;
            left = EDGE_MARGIN + horizontalSpace * anchor.horizontal / 2 + options.xOffset();
            top = EDGE_MARGIN + verticalSpace * anchor.vertical / 2;
            left = Mth.clamp(left, EDGE_MARGIN, width - EDGE_MARGIN - barWidth);
        }
        if (barWidth < 3 || barHeight < 3) {
            return;
        }
        top = Mth.clamp(top + options.yOffset(), verticalMargin, height - verticalMargin - barHeight);
        paint(graphics, state, options, left, top, barWidth, barHeight);
    }

    private static int hotbarOccupiedLeft(Minecraft mc, Player cameraPlayer, int width) {
        int hotbarLeft = width / 2 - HOTBAR_HALF_WIDTH;
        if (cameraPlayer.getMainArm() == HumanoidArm.RIGHT && !cameraPlayer.getOffhandItem().isEmpty()) {
            return hotbarLeft - LEFT_OFFHAND_WIDTH;
        }
        if (cameraPlayer.getMainArm() == HumanoidArm.LEFT
                && mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            return hotbarLeft - LEFT_ATTACK_INDICATOR_WIDTH;
        }
        // The selected first slot extends one pixel past the hotbar background.
        return hotbarLeft - 1;
    }

    private static void paint(GuiGraphics graphics, CriticalBarSyncPacket state, Appearance options,
                              int left, int top, int width, int height) {
        int right = left + width;
        int bottom = top + height;
        int fill = state.active() ? options.charged() : options.progress();
        int accent = state.active() ? options.chargedHighlight() : options.primary();
        if (state.active() && options.glow()) {
            int glow = (fill & 0xFFFFFF) | (((fill >>> 24) / 4) << 24);
            outline(graphics, left - 1, top - 1, right + 1, bottom + 1, true, glow);
        }
        if (options.border()) {
            int borderColor = state.active() ? options.chargedBorder() : options.borderColor();
            outline(graphics, left, top, right, bottom, options.rounded(), borderColor);
            left++;
            top++;
            right--;
            bottom--;
        }
        if (options.showBackground()) {
            surface(graphics, left, top, right, bottom, options.rounded(), options.background(), options.background());
        }
        int filled = ((right - left) * state.progress() + state.capacity() / 2) / state.capacity();
        if (filled <= 0) {
            return;
        }

        int fillRight = left + filled;
        switch (options.style()) {
            case CLASSIC -> {
                surface(graphics, left, top, fillRight, bottom, options.rounded(), fill, fill);
                if (bottom - top > 1) {
                    int cut = cornerCut(left, top, fillRight, bottom, options.rounded());
                    graphics.fill(left + cut, top, fillRight - cut, top + 1, accent);
                }
            }
            case SOLID -> surface(graphics, left, top, fillRight, bottom, options.rounded(), fill, fill);
            case GRADIENT -> surface(graphics, left, top, fillRight, bottom, options.rounded(), accent, fill);
            case OUTLINED -> outline(graphics, left, top, fillRight, bottom, options.rounded(), fill);
        }
    }

    private static int cornerCut(int left, int top, int right, int bottom, boolean rounded) {
        return rounded && right - left >= 3 && bottom - top >= 3 ? 1 : 0;
    }

    private static void outline(GuiGraphics graphics, int left, int top, int right, int bottom,
                                boolean rounded, int color) {
        if (right - left <= 2 || bottom - top <= 2) {
            graphics.fill(left, top, right, bottom, color);
            return;
        }
        int cut = cornerCut(left, top, right, bottom, rounded);
        graphics.fill(left + cut, top, right - cut, top + 1, color);
        graphics.fill(left + cut, bottom - 1, right - cut, bottom, color);
        graphics.fill(left, top + 1, left + 1, bottom - 1, color);
        graphics.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    private static void surface(GuiGraphics graphics, int left, int top, int right, int bottom,
                                boolean rounded, int topColor, int bottomColor) {
        if (cornerCut(left, top, right, bottom, rounded) != 0) {
            graphics.fill(left + 1, top, right - 1, top + 1, topColor);
            graphics.fill(left + 1, bottom - 1, right - 1, bottom, bottomColor);
            top++;
            bottom--;
        }
        if (topColor == bottomColor || bottom - top == 1) {
            graphics.fill(left, top, right, bottom, bottomColor);
        } else {
            graphics.fillGradient(left, top, right, bottom, topColor, bottomColor);
        }
    }

    @Mod.EventBusSubscriber(modid = ChatOverhaul.MODID, value = Dist.CLIENT)
    public static final class Lifecycle {
        private Lifecycle() {}

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            snapshot = null;
        }
    }
}
