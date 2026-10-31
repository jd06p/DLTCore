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

    private WonderlandVanillaMusicPolicy() {}

    public static boolean shouldBlock(ResourceLocation dimension, ResourceLocation sound) {
        return dimension != null && sound != null
                && "the_wonderland".equals(dimension.getNamespace())
                && !VANILLA_MUSIC_ALLOWED_DIMENSIONS.contains(dimension)
                && "minecraft".equals(sound.getNamespace())
                && sound.getPath().startsWith("music.");
    }
}
