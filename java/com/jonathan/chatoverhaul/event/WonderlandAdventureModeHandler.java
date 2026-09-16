package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Forces Adventure Mode while a player is inside any of three specific
 * Wonderland dimensions (torture, dlrowmaerd, dreamworld - `corners` was
 * deliberately removed from this set per a later request, and should stay
 * excluded unless asked otherwise), and restores Survival the moment they're no
 * longer in one - by any means (normal travel, death+respawn, a crash and
 * reconnect, or anything else), rather than trying to special-case each
 * way of leaving individually.
 *
 * The check is re-run from scratch on every one of three separate events -
 * dimension change, login, and respawn - always comparing the player's
 * CURRENT dimension against the protected set and correcting the gamemode
 * if it doesn't match, rather than tracking "was in Adventure before" as
 * separate state. This is what makes it self-correcting: it doesn't
 * matter how a player ended up outside these dimensions, only where they
 * actually are right now, so there's no specific transition that can be
 * missed.
 *
 * Deliberately conservative in both directions, to avoid clobbering an
 * op's own gamemode choice: this only escalates SURVIVAL -> ADVENTURE on
 * entry, and only reverts ADVENTURE -> SURVIVAL on exit. A player already
 * in Creative or Spectator (an op testing something, say) is left alone
 * either way.
 *
 * PlayerRespawnEvent priority matters here: WonderlandDeathRespawnHandler
 * (which may relocate a just-died player back into the same protected
 * dimension) is registered at HIGH priority, and this class's onRespawn
 * is at LOW - so by the time gamemode gets re-checked here, any
 * relocation has already happened, and this sees the player's true final
 * dimension rather than a stale pre-relocation one.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class WonderlandAdventureModeHandler {

    private static final Set<String> ADVENTURE_DIMENSIONS = Set.of(
            "the_wonderland:torture",
            "the_wonderland:dlrowmaerd",
            "the_wonderland:dreamworld"
    );

    private WonderlandAdventureModeHandler() {}

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, event.getTo());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, player.level().dimension());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, player.level().dimension());
        }
    }

    private static void sync(ServerPlayer player, ResourceKey<Level> dimension) {
        boolean shouldBeAdventure = ADVENTURE_DIMENSIONS.contains(dimension.location().toString());
        GameType current = player.gameMode.getGameModeForPlayer();

        if (shouldBeAdventure && current == GameType.SURVIVAL) {
            player.setGameMode(GameType.ADVENTURE);
        } else if (!shouldBeAdventure && current == GameType.ADVENTURE) {
            player.setGameMode(GameType.SURVIVAL);
        }
    }
}
