package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a player inside the SAME Wonderland dimension they died in, rather
 * than letting vanilla send them to the Overworld - across EVERY
 * `the_wonderland:*` dimension, detected dynamically by namespace rather
 * than a hardcoded list (so a Wonderland dimension added later is covered
 * automatically, with no changes needed here).
 *
 * DESIGN - now genuinely respects a valid respawn point rather than
 * ignoring beds/anchors entirely:
 *
 * 1. LivingDeathEvent (fires before respawn is even decided) records the
 *    death position AND dimension, keyed by UUID - only when the death
 *    dimension's namespace is "the_wonderland".
 * 2. PlayerEvent.PlayerRespawnEvent fires once the new player object
 *    already exists, AFTER vanilla's own respawn logic has already run -
 *    meaning it has ALREADY tried the player's stored bed/respawn-anchor,
 *    falling back to world spawn only if none was valid. So rather than
 *    always overriding to the death position, this only steps in if the
 *    player's resulting dimension does NOT match the dimension they died
 *    in. If it DOES match - because they had a valid bed/anchor set
 *    exactly there - nothing is touched at all, and vanilla's own bed/
 *    anchor-driven placement (position, orientation, everything) is left
 *    completely alone. Override only happens for the actual "no valid
 *    respawn point in that dimension" case, which is the only situation
 *    that would otherwise result in an Overworld escape.
 * 3. The fallback position (used only in that override case) is found via
 *    the dimension's own heightmap at the death X/Z - standing on the
 *    actual surface there, not just floating at a clamped Y - so a void
 *    death doesn't repeat itself, and it's a closer match to "follow
 *    Minecraft's normal respawn rules" than an arbitrary fixed offset.
 *
 * Nothing is ever recorded for a death outside a the_wonderland:*
 * dimension, so normal respawn behavior in the Overworld, Nether, End, or
 * any other modded dimension is completely untouched - there's no logic
 * path here that can affect them.
 *
 * The override teleport (when needed) happens synchronously within the
 * PlayerRespawnEvent handler itself, in the same server tick vanilla's own
 * placement just happened in - before any packet reflecting that
 * intermediate Overworld position is ever sent to the client, so there is
 * no perceptible flicker or visible round-trip through the Overworld.
 *
 * Registered at EventPriority.HIGH on PlayerRespawnEvent specifically so
 * it runs BEFORE WonderlandAdventureModeHandler's own (LOW priority)
 * respawn check - gamemode needs to be re-evaluated against the player's
 * FINAL dimension, after this relocation, not their transient
 * just-respawned-in-the-Overworld one. This is the one and only respawn
 * handler for Wonderland in this mod - it is not duplicated anywhere else.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class WonderlandDeathRespawnHandler {

    private record DeathLocation(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {}

    private static final Map<UUID, DeathLocation> PENDING_RESPAWN = new ConcurrentHashMap<>();

    private WonderlandDeathRespawnHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }

        ResourceKey<Level> dimension = player.level().dimension();
        if (DimensionNamespaces.isNamespace(dimension, DimensionNamespaces.THE_WONDERLAND)) {
            PENDING_RESPAWN.put(player.getUUID(), new DeathLocation(
                    dimension, player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        DeathLocation location = PENDING_RESPAWN.remove(player.getUUID());
        if (location == null) {
            return;
        }

        // Vanilla's own respawn logic has already run by this point - if it
        // already placed them back in the exact dimension they died in
        // (because they had a valid bed/anchor set there), there's nothing
        // to do: leave that placement completely alone.
        if (player.level().dimension().equals(location.dimension())) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        ServerLevel targetLevel = server.getLevel(location.dimension());
        if (targetLevel == null) {
            return;
        }

        int surfaceY = targetLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(location.x()), (int) Math.floor(location.z()));

        player.teleportTo(targetLevel, location.x(), surfaceY, location.z(), location.yRot(), location.xRot());
    }
}
