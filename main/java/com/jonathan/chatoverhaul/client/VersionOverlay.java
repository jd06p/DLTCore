package com.jonathan.chatoverhaul.client;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Top-left "game version" text, driven entirely by the player's current
 * dimension. Reads Minecraft.getInstance().level.dimension() fresh every
 * frame - no cached/stored dimension state, no separate "dimension changed"
 * event needed, and no possibility of stale text.
 *
 * CLIENT-ONLY: this class is annotated `value = Dist.CLIENT`, which is
 * exactly the annotation the_arg_container's ModCheckProcedure was MISSING
 * - that omission is what let Forge try to load a class referencing
 * client-only code on a dedicated server, crashing it. With this present,
 * Forge's annotation scanner skips this class entirely on a dedicated
 * server without ever attempting to load it, so this carries none of that
 * risk and needs no server-side counterpart at all.
 *
 * The dimension -> text mapping is a handful of Set<String> constants
 * below (matched by registry ID, not display name) plus one small
 * textFor() method - add, remove, or retarget a dimension there without
 * touching the render/registration logic.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VersionOverlay {

    private static final String WARNING_TEXT = "\u00a74Please remove this version from your system.";
    private static final String DREAMWORLD_TEXT = "???";
    private static final String VOIDEXP_TEXT = "Minecraft VoidExp v0.0.1";
    private static final String HUB_TEXT = "Minecraft Alpha v1.0.16.05_11";

    /** Documents which dimensions get the configurable text; the final fallback in textFor() covers them (and anything unrecognized) either way. */
    private static final Set<String> VANILLA_DIMENSIONS = Set.of(
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end"
    );

    /** Rendered as "???" - the one dimension carved out of the Wonderland warning set. */
    private static final Set<String> DREAMWORLD_DIMENSIONS = Set.of(
            "the_wonderland:dreamworld"
    );

    private static final Set<String> VOIDEXP_DIMENSIONS = Set.of(
            "the_arg_container:moonfalldimension",
            "thebrokenscript:day_b"
    );

    private static final Set<String> HUB_DIMENSIONS = Set.of(
            "the_arg_container:hub"
    );

    /** Overlay renders nothing at all here. */
    private static final Set<String> DISABLED_DIMENSIONS = Set.of(
            "the_arg_container:ash"
    );

    /**
     * Every dimension from the_wonderland's data/the_wonderland/dimension/
     * folder (43 total, confirmed by listing the actual files - dreamworld
     * excluded here since it has its own entry above), plus the extra
     * non-Wonderland dimensions the pack wants flagged the same way.
     */
    private static final Set<String> WARNING_DIMENSIONS = Set.of(
            "the_wonderland:abyss",
            "the_wonderland:acceptance",
            "the_wonderland:accountability",
            "the_wonderland:answer_dimension",
            "the_wonderland:bedrock_hallways",
            "the_wonderland:brickways",
            "the_wonderland:bridges",
            "the_wonderland:catacombs",
            "the_wonderland:chasms",
            "the_wonderland:compliance",
            "the_wonderland:corners",
            "the_wonderland:depths",
            "the_wonderland:dlrowmaerd",
            "the_wonderland:dream_6",
            "the_wonderland:endless_field",
            "the_wonderland:endless_forest",
            "the_wonderland:endless_house",
            "the_wonderland:endless_library",
            "the_wonderland:entrails",
            "the_wonderland:false_wonderland",
            "the_wonderland:fools_paradise",
            "the_wonderland:hellways",
            "the_wonderland:inconsistency",
            "the_wonderland:jungle_warehouse",
            "the_wonderland:lapis_labyrinth",
            "the_wonderland:maze",
            "the_wonderland:no_mans_land",
            "the_wonderland:pipes",
            "the_wonderland:remembrance",
            "the_wonderland:repetition",
            "the_wonderland:reservoir",
            "the_wonderland:sarcophagus",
            "the_wonderland:sequence",
            "the_wonderland:shattered_dreams",
            "the_wonderland:skyways",
            "the_wonderland:snowy_bridges",
            "the_wonderland:the_slip",
            "the_wonderland:the_vault",
            "the_wonderland:torture",
            "the_wonderland:tunnels",
            "the_wonderland:what_once_was",
            "the_wonderland:winter_wonderland",
            "the_wonderland:wonderland",
            "thebrokenscript:clan_void",
            "thebrokenscript:day_a",
            "thebrokenscript:null_torture",
            "the_arg_container:soul_d"
    );

    private VersionOverlay() {}

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("chatoverhaul_version",
                (gui, guiGraphics, partialTick, width, height) -> render(guiGraphics));
    }

    private static void render(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        ResourceLocation dimensionId = mc.level.dimension().location();
        String text = textFor(dimensionId.toString());
        if (text == null) {
            return;
        }

        guiGraphics.drawString(mc.font, text, 4, 4, 0xFFFFFF, true);
    }

    /**
     * The actual dimension -> text mapping. Returns null to render nothing.
     * Falls back to the vanilla text for any dimension not listed above
     * (rather than nothing/a crash), so entering an unrecognized custom
     * dimension is always safe.
     */
    private static String textFor(String dimensionId) {
        if (DISABLED_DIMENSIONS.contains(dimensionId)) {
            return null;
        }
        if (DREAMWORLD_DIMENSIONS.contains(dimensionId)) {
            return DREAMWORLD_TEXT;
        }
        if (VOIDEXP_DIMENSIONS.contains(dimensionId)) {
            return VOIDEXP_TEXT;
        }
        if (HUB_DIMENSIONS.contains(dimensionId)) {
            return HUB_TEXT;
        }
        if (WARNING_DIMENSIONS.contains(dimensionId)) {
            return WARNING_TEXT;
        }
        // Overworld/Nether/End get the configurable text explicitly; anything
        // else unrecognized falls back to the same value rather than nothing.
        return ChatOverhaulConfig.OVERWORLD_OVERLAY_TEXT.get();
    }
}
