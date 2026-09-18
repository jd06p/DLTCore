package com.jonathan.chatoverhaul.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client config for chatoverhaul (a.k.a. DLTCore) - currently just the
 * Overworld/Nether/End overlay text used by VersionOverlow.
 *
 * Standard ForgeConfigSpec, registered as ModConfig.Type.CLIENT in
 * ChatOverhaul's constructor. Forge handles all of "generate the file with
 * defaults if it's missing", "load it safely at startup", and "produce a
 * commented, human-editable TOML file" on its own - this class only
 * declares the one value.
 *
 * File lives at config/chatoverhaul-client.toml once generated.
 */
public final class ChatOverhaulConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> OVERWORLD_OVERLAY_TEXT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("overlay");
        OVERWORLD_OVERLAY_TEXT = builder
                .comment(
                        "Text shown by the top-left Game Version overlay while in the",
                        "Overworld, the Nether, or the End. Change this and reload/rejoin",
                        "to update the overlay - no rebuild needed."
                )
                .define("overworld_overlay_text", "Minecraft Delta vRPG-2.5.9");
        builder.pop();

        SPEC = builder.build();
    }

    private ChatOverhaulConfig() {}
}
