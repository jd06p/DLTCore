package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies a one-shot Slow Falling effect on entering any Broken Script or
 * Arg Container dimension, as a safety net against falling into the void
 * while a custom dimension's chunks are still loading. Deliberately NOT
 * applied for The Wonderland - only these two namespaces, per the request.
 *
 * Applied on two triggers, both "entering a dimension" in the sense that
 * matters for the stated safety purpose:
 *
 *  - PlayerChangedDimensionEvent: the normal case, moving into one of
 *    these dimensions from anywhere else (including from one Broken
 *    Script/Arg Container dimension straight into another - the "to"
 *    namespace is checked independently of the "from" one, so that still
 *    counts as entering and reapplies the effect).
 *  - PlayerLoggedInEvent: covers logging in with the player's last saved
 *    position already inside one of these dimensions (e.g. after a server
 *    restart) - chunks there are just as freshly-loading in that case, so
 *    the same safety concern applies even though no dimension change
 *    technically occurred this session.
 *
 * One-shot by construction: this only runs on those two discrete events,
 * never on a tick, so the effect is applied exactly once per entry and
 * naturally expires after 15 seconds rather than being refreshed.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class DimensionEntrySlowFallingHandler {

    private static final int DURATION_TICKS = 15 * 20;
    private static final int AMPLIFIER = 150;

    private DimensionEntrySlowFallingHandler() {}

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyIfProtected(player, event.getTo());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyIfProtected(player, player.level().dimension());
        }
    }

    private static void applyIfProtected(ServerPlayer player, ResourceKey<Level> dimension) {
        if (DimensionNamespaces.isNamespace(dimension,
                DimensionNamespaces.THEBROKENSCRIPT,
                DimensionNamespaces.THE_ARG_CONTAINER)) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, DURATION_TICKS, AMPLIFIER, false, false, true));
        }
    }
}
