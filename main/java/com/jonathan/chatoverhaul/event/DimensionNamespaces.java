package com.jonathan.chatoverhaul.event;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Small shared helper so "is this dimension provided by mod X" is checked
 * the same way everywhere, rather than each handler reimplementing its own
 * namespace comparison.
 */
final class DimensionNamespaces {

    static final String THE_WONDERLAND = "the_wonderland";
    static final String THE_ARG_CONTAINER = "the_arg_container";
    static final String THEBROKENSCRIPT = "thebrokenscript";

    private DimensionNamespaces() {}

    static String namespaceOf(ResourceKey<Level> dimension) {
        return dimension.location().getNamespace();
    }

    static boolean isNamespace(ResourceKey<Level> dimension, String... namespaces) {
        String actual = namespaceOf(dimension);
        for (String ns : namespaces) {
            if (actual.equals(ns)) {
                return true;
            }
        }
        return false;
    }
}
