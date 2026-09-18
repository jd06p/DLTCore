package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A vanilla-style boss bar (like the Ender Dragon/Wither) for
 * `thebrokenscript:integrity_bossfight` - red, tracking its current
 * health/max-health ratio, shown to any player within range and hidden
 * from anyone who wanders away, same as vanilla's own boss health bars.
 *
 * One ServerBossEvent per LIVING INSTANCE of this entity type, keyed by
 * UUID - if multiple ever exist simultaneously, each gets its own bar
 * rather than one bar being shared/overwritten.
 *
 * Health/progress updates every tick (cheap - a single float division),
 * but the more expensive "who's in range" player scan is throttled to
 * once every 10 ticks per entity, matching the efficiency convention
 * already used elsewhere in this mod (see ElytraRestrictionHandler).
 *
 * Cleaned up on both LivingDeathEvent (killed) and EntityLeaveLevelEvent
 * (unloaded/discarded/removed any other way) so a bar can never be left
 * orphaned, visible to players with no entity behind it.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class IntegrityBossBarHandler {

    private static final ResourceLocation ENTITY_ID = new ResourceLocation("thebrokenscript", "integrity_bossfight");
    private static final double VISIBILITY_RADIUS = 96.0;
    private static final int SCAN_INTERVAL_TICKS = 10;

    private static final Map<UUID, ServerBossEvent> BARS = new ConcurrentHashMap<>();

    private IntegrityBossBarHandler() {}

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }

        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (typeId == null || !typeId.equals(ENTITY_ID)) {
            return;
        }

        ServerBossEvent bar = BARS.computeIfAbsent(entity.getUUID(), id ->
                new ServerBossEvent(entity.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));

        float maxHealth = entity.getMaxHealth();
        float progress = maxHealth > 0.0f ? Math.max(0.0f, Math.min(1.0f, entity.getHealth() / maxHealth)) : 0.0f;
        bar.setProgress(progress);

        if (entity.tickCount % SCAN_INTERVAL_TICKS != 0 || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        double radiusSq = VISIBILITY_RADIUS * VISIBILITY_RADIUS;
        for (ServerPlayer player : level.players()) {
            boolean inRange = player.distanceToSqr(entity) <= radiusSq;
            boolean tracked = bar.getPlayers().contains(player);
            if (inRange && !tracked) {
                bar.addPlayer(player);
            } else if (!inRange && tracked) {
                bar.removePlayer(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        removeBar(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        removeBar(event.getEntity().getUUID());
    }

    private static void removeBar(UUID uuid) {
        ServerBossEvent bar = BARS.remove(uuid);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }
}
