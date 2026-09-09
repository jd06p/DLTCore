package com.jonathan.chatoverhaul;

import com.jonathan.chatoverhaul.client.ChatOverhaulConfig;
import com.jonathan.chatoverhaul.config.ChatOverhaulServerConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

/**
 * DLTCore (mod ID kept as "chatoverhaul" internally - see README)
 *
 * All the actual work happens elsewhere:
 *  - mixin/PlayerDisplayMessageMixin, mixin/PlayerListBroadcastMixin: chat
 *    message rewrites (The Arg Container / The Wonderland / vanilla join-leave).
 *  - event/FirstJoinSequence, event/TickScheduler: the scripted first-join
 *    chat sequence.
 *  - event/CrossMechanicHandler: the Wonderland cross compat mechanic.
 *  - event/DimensionKeepInventoryHandler: dimension-scoped keep inventory.
 *  - event/ElytraRestrictionHandler: Elytra shutoff near specific entities.
 *  - event/DimensionEntrySlowFallingHandler: safety Slow Falling on
 *    entering Broken Script/Arg Container dimensions.
 *  - mixin/cpapi/PortalPlacerMixin: blocks Custom Portal API portal
 *    ignition inside Wonderland/Broken Script/Arg Container dimensions.
 *  - mixin/rus/CodesProcedureMixin: disables The Broken Script's Null
 *    chat-response system.
 *  - event/WonderlandAdventureModeHandler: forces Adventure Mode inside
 *    four specific Wonderland dimensions, restores Survival on exit.
 *  - event/WonderlandDeathRespawnHandler: keeps a player in the same
 *    Wonderland dimension they died in, across respawn.
 *  - event/NotRealEventHandler: the "im not real" chat-triggered sequence.
 *  - event/NullJoinCommandInterceptor: definitive fix for "null joined the
 *    game" via CommandEvent, independent of the two mixin-based guesses.
 *  - client/VersionOverlay, client/ChatOverhaulConfig: the dimension-aware
 *    HUD overlay and its (client) config.
 *  - config/ChatOverhaulServerConfig: the (server) config for the Elytra
 *    restriction radius.
 *
 * Both config classes reference no client-only classes (they're plain
 * ForgeConfigSpec), so registering them here in the common constructor is
 * safe on both physical sides - Forge only actually generates/loads each
 * file on the side its own ModConfig.Type applies to.
 */
@Mod(ChatOverhaul.MODID)
public class ChatOverhaul {
    public static final String MODID = "chatoverhaul";

    public ChatOverhaul() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ChatOverhaulConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ChatOverhaulServerConfig.SPEC);
    }
}
