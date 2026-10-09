package com.jonathan.chatoverhaul.client;

import com.jonathan.chatoverhaul.ChatOverhaul;
import com.jonathan.chatoverhaul.quest.QuestDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Client-only HUD overlay showing the player's active quest in the top-right
 * corner, but ONLY while the_arg_container:quest_manager is held in the main
 * hand (moved to offhand, inventory, or dropped - the overlay hides, because
 * the check is done fresh from the main hand every frame).
 *
 * Rendered with the vanilla Minecraft font (the same pixel font used by the
 * vanilla HUD) at a thin margin from the top and right edges: light cyan for
 * progress ({@code Quest: Collect 600 Cobblestone: 237/600}), switching to an
 * aqua message on completion ({@code Quest: Collect 600 Cobblestone:
 * Complete!}). drawString's drop-shadow gives the subtle dark outline, and no
 * background panel is drawn. Registered as its own overlay element, so the
 * vanilla HUD, boss bars, chat and every other overlay are left untouched.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class QuestHudOverlay {

    private static final ResourceLocation QUEST_MANAGER_ID =
            new ResourceLocation("the_arg_container", "quest_manager");
    private static final int MARGIN = 4;
    /** Light cyan - normal quest progress text. */
    private static final int COLOR_PROGRESS = 0x8EDCFF;
    /** Aqua - completed-quest message. */
    private static final int COLOR_COMPLETE = 0x55FFFF;

    private QuestHudOverlay() {}

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("chatoverhaul_quest",
                (gui, guiGraphics, partialTick, width, height) -> render(guiGraphics, width));
    }

    private static void render(GuiGraphics guiGraphics, int screenWidth) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!mainHandHoldsQuestManager(mc)) {
            return;
        }
        if (!ClientQuestState.hasData()) {
            return;
        }

        QuestDefinition definition =
                QuestDefinition.QUESTS[QuestDefinition.clampIndex(ClientQuestState.questIndex())];
        boolean done = ClientQuestState.complete();
        int progress = Math.max(0, Math.min(ClientQuestState.progress(), definition.target));
        String text = done
                ? "Quest: " + definition.label + ": Complete!"
                : "Quest: " + definition.label + ": " + progress + "/" + definition.target;
        int color = done ? COLOR_COMPLETE : COLOR_PROGRESS;

        guiGraphics.drawString(mc.font, text,
                screenWidth - mc.font.width(text) - MARGIN, MARGIN, color, true);
    }

    private static boolean mainHandHoldsQuestManager(Minecraft mc) {
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return QUEST_MANAGER_ID.equals(id);
    }
}