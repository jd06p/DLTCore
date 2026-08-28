package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import com.jonathan.chatoverhaul.config.ChatOverhaulServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * Disables Elytra gliding near a specific set of entities, so they can't be
 * trivially outrun by just flying away.
 *
 * Efficiency: the entity-radius scan only ever runs for a player who is
 * ALREADY gliding (player.isFallFlying()) - the overwhelmingly common case
 * (not gliding) exits on that single boolean check with no scan at all.
 * While gliding, the scan itself is further throttled to once every 5
 * ticks per player (still well under a second of reaction time, more than
 * fast enough to stop an escape attempt) rather than on every single tick.
 *
 * There's no vanilla/Forge event for "player is about to start gliding" to
 * cancel outright, so this works by immediately calling stopFallFlying()
 * the moment gliding is detected near a restricted entity - in practice
 * this means gliding can never be sustained near one, which is the
 * intended effect.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class ElytraRestrictionHandler {

    private static final Set<String> RESTRICTED_ENTITY_IDS = Set.of(
            "thebrokenscript:circuit",
            "thebrokenscript:the_broken_end",
            "thebrokenscript:integrity_bossfight",
            "the_wonderland:the_entity_chasing",
            "the_wonderland:wondertree_chasing"
    );

    private static final int SCAN_INTERVAL_TICKS = 5;

    private ElytraRestrictionHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isFallFlying()) {
            return;
        }
        if (player.tickCount % SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        double radius = ChatOverhaulServerConfig.ELYTRA_RESTRICTION_RADIUS.get();
        boolean nearRestrictedEntity = player.level()
                .getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(radius),
                        ElytraRestrictionHandler::isRestricted)
                .stream()
                .findAny()
                .isPresent();

        if (nearRestrictedEntity) {
            player.stopFallFlying();
        }
    }

    private static boolean isRestricted(Entity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && RESTRICTED_ENTITY_IDS.contains(id.toString());
    }
}
