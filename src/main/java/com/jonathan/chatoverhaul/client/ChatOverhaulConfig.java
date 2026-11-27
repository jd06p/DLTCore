package com.jonathan.chatoverhaul.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client config for chatoverhaul (a.k.a. DLTCore), including the dimension
 * overlay and optional Custom Skyboxes compatibility controls.
 *
 * Standard ForgeConfigSpec, registered as ModConfig.Type.CLIENT in
 * ChatOverhaul's constructor. Forge handles all of "generate the file with
 * defaults if it's missing", "load it safely at startup", and "produce a
 * commented, human-editable TOML file" on its own - this class only
 * declares the values.
 *
 * File lives at config/DLTCore-client.toml once generated. The old
 * config/chatoverhaul-client.toml is copied to that name on first launch
 * by {@link com.jonathan.chatoverhaul.config.ConfigMigration}.
 */
public final class ChatOverhaulConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> OVERWORLD_OVERLAY_TEXT;
    public static final ForgeConfigSpec.BooleanValue CUSTOM_SKYBOXES_OVERWORLD_ONLY;
    public static final ForgeConfigSpec.BooleanValue CUSTOM_SKYBOXES_AUTO_ROTATION;
    public static final ForgeConfigSpec.IntValue CUSTOM_SKYBOXES_DAY_ROTATION_TICKS;
    public static final ForgeConfigSpec.IntValue CUSTOM_SKYBOXES_NIGHT_ROTATION_TICKS;

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

        builder.push("compatibility").push("customskyboxes");
        CUSTOM_SKYBOXES_OVERWORLD_ONLY = builder
                .comment(
                        "When Custom Skyboxes is installed, only apply its skybox overrides in",
                        "minecraft:overworld. Set false to retain Custom Skyboxes' normal all-dimensions behavior."
                )
                .define("overworldOnly", true);
        CUSTOM_SKYBOXES_AUTO_ROTATION = builder
                .comment("Automatically restore the configured day and night rotation effects after joining a world.")
                .define("autoRotation", true);
        CUSTOM_SKYBOXES_DAY_ROTATION_TICKS = builder
                .comment("Loop duration for the automatically restored day_sky rotation, in ticks.")
                .defineInRange("dayRotationTicks", 12000, 1, 72000);
        CUSTOM_SKYBOXES_NIGHT_ROTATION_TICKS = builder
                .comment("Loop duration for the automatically restored night_sky rotation, in ticks.")
                .defineInRange("nightRotationTicks", 24000, 1, 72000);
        builder.pop(2);

        SPEC = builder.build();
    }

    private ChatOverhaulConfig() {}
}
