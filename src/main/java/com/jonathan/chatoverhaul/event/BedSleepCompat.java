package com.jonathan.chatoverhaul.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;

/**
 * Allows normal bed usage in the Arg Container "hub" dimension, where custom
 * dimension flags would otherwise make beds explode (bedWorks = false) and/or
 * forbid sleeping outright (natural = false).
 *
 * Scoped to the exact dimension key "the_arg_container:hub", so every other
 * dimension keeps vanilla behavior, including the explosive beds in dimensions
 * that intentionally use bedWorks = false.
 */
public final class BedSleepCompat {

    private static final ResourceLocation HUB = new ResourceLocation("the_arg_container", "hub");

    private BedSleepCompat() {}

    public static boolean isHub(Level level) {
        return level.dimension().location().equals(HUB);
    }

    public static boolean canSetSpawn(Level level) {
        if (isHub(level)) {
            return true;
        }
        return BedBlock.canSetSpawn(level);
    }
}