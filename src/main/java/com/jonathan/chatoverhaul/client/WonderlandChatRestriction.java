package com.jonathan.chatoverhaul.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-side policy for blocking player-entered chat in Wonderland. */
@OnlyIn(Dist.CLIENT)
public final class WonderlandChatRestriction {
    private static final String WONDERLAND_NAMESPACE = "the_wonderland";
    private static final ResourceLocation ACCEPTANCE = new ResourceLocation("the_wonderland", "acceptance");
    private static final Component FEEDBACK = Component.literal("IMPORT minecraft.chatengine");

    private WonderlandChatRestriction() {}

    public static boolean blocksPlayerChat() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return false;
        ResourceLocation dimension = minecraft.player.level().dimension().location();
        return WONDERLAND_NAMESPACE.equals(dimension.getNamespace()) && !ACCEPTANCE.equals(dimension);
    }

    public static void showBlockedFeedback() {
        Minecraft.getInstance().gui.setOverlayMessage(FEEDBACK, false);
    }
}
