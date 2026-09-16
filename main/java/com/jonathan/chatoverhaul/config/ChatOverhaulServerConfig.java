package com.jonathan.chatoverhaul.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server config for chatoverhaul (a.k.a. DLTCore) - values read by
 * server-side gameplay logic (currently just the Elytra restriction
 * radius). Kept separate from client/ChatOverhaulConfig on purpose: that
 * one is ModConfig.Type.CLIENT (per-player, only exists on a client), this
 * one is ModConfig.Type.SERVER (per-world, exists on a dedicated server
 * too) - a value read by server logic has to live in a SERVER/COMMON
 * config, not a CLIENT one, or a dedicated server would have no file to
 * read it from at all.
 *
 * File lives at config/chatoverhaul-server.toml once generated (per-world
 * on a dedicated server, same as any other Forge SERVER config).
 */
public final class ChatOverhaulServerConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue ELYTRA_RESTRICTION_RADIUS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("elytra_restriction");
        ELYTRA_RESTRICTION_RADIUS = builder
                .comment(
                        "How close (in blocks) a player can be to circuit, the_broken_end,",
                        "integrity_bossfight, the_entity_chasing, or wondertree_chasing",
                        "before their Elytra is disabled. Large on purpose - the point is",
                        "that flying away from these shouldn't be a viable escape."
                )
                .defineInRange("elytra_restriction_radius", 32.0, 0.0, 256.0);
        builder.pop();

        SPEC = builder.build();
    }

    private ChatOverhaulServerConfig() {}
}
