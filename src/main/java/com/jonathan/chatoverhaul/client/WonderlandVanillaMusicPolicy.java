package com.jonathan.chatoverhaul.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Restricts only Minecraft's background-music event family, never modded audio or music discs. */
public final class WonderlandVanillaMusicPolicy {
    public static final Set<ResourceLocation> VANILLA_MUSIC_ALLOWED_DIMENSIONS = Set.of(
            new ResourceLocation("the_wonderland", "wonderland"),
            new ResourceLocation("the_wonderland", "false_wonderland"),
            new ResourceLocation("the_wonderland", "sarcophagus"),
            new ResourceLocation("the_wonderland", "no_mans_land"),
            new ResourceLocation("the_wonderland", "what_once_was"),
            new ResourceLocation("the_wonderland", "accountability"),
            new ResourceLocation("the_wonderland", "endless_field"),
            new ResourceLocation("the_wonderland", "compliance"),
            new ResourceLocation("the_wonderland", "dlrowmaerd"),
            new ResourceLocation("the_wonderland", "corners"),
            new ResourceLocation("the_wonderland", "maze"));

    /** Dimensions where DLTCore supplies one dedicated looping background track. */
    public static final Set<ResourceLocation> CUSTOM_MUSIC_DIMENSIONS = Set.of(
            new ResourceLocation("the_arg_container", "soul_d"),
            new ResourceLocation("the_wonderland", "dlrowmaerd"),
            new ResourceLocation("the_wonderland", "maze"),
            new ResourceLocation("the_wonderland", "no_mans_land"),
            new ResourceLocation("the_wonderland", "endless_forest"));

    /** Dimensions where vanilla background music is silent, with no replacement track. */
    public static final Set<ResourceLocation> SILENT_MUSIC_DIMENSIONS = Set.of(
            new ResourceLocation("thebrokenscript", "clan_void"),
            new ResourceLocation("thebrokenscript", "day_a"),
            new ResourceLocation("thebrokenscript", "day_b"),
            new ResourceLocation("thebrokenscript", "null_torture"));

    private WonderlandVanillaMusicPolicy() {}

    public static boolean shouldBlockVanillaBackgroundMusic(ResourceLocation dimension) {
        if (dimension == null) return false;
        if (CUSTOM_MUSIC_DIMENSIONS.contains(dimension) || SILENT_MUSIC_DIMENSIONS.contains(dimension)) return true;

        // Preserve DLTCore's existing Wonderland music policy for all other dimensions.
        return "the_wonderland".equals(dimension.getNamespace())
                && !VANILLA_MUSIC_ALLOWED_DIMENSIONS.contains(dimension);
    }

    public static boolean shouldBlock(ResourceLocation dimension, ResourceLocation sound) {
        return shouldBlockVanillaBackgroundMusic(dimension) && sound != null
                && "minecraft".equals(sound.getNamespace())
                && sound.getPath().startsWith("music.");
    }
}
