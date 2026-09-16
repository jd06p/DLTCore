package com.jonathan.chatoverhaul.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All state for one running Entity303 encounter. Package-private - only
 * Entity303RitualManager touches this directly.
 *
 * Now spans the WHOLE encounter, not just the clone-fighting phase: this
 * instance stays alive (and ACTIVE in the manager's global reference)
 * through the clone phase, the victory dialogue, and the boss's actual
 * lifetime, only being cleared once the boss is genuinely defeated or the
 * player dies. bossUuid is null until spawnFinalBoss() runs, and is what
 * lets the manager recognize "the boss itself died" as distinct from "one
 * of its clones died", and what the escape/block-placement restrictions
 * key off of during the post-clone-phase portion of the fight.
 *
 * Holds its own MinecraftServer reference directly (captured once at
 * ritual start) rather than trying to look one up later from elsewhere -
 * every scheduled callback throughout the ritual's lifetime needs one, and
 * this is the simplest reliable way to have it on hand everywhere.
 *
 * ownedClones is how clone kills get attributed to THIS specific ritual
 * rather than any user_0_clone anywhere in the dimension (see
 * Entity303RitualManager's entity-join handler for how clones get added
 * here) - a stray/pre-existing clone that was never tagged as belonging to
 * this ritual is simply never in this set, and so never counts.
 */
final class RitualInstance {

    final MinecraftServer server;
    final UUID playerUuid;
    final ResourceKey<Level> dimension;
    final BlockPos jukeboxPos;

    volatile RitualState state = RitualState.STARTING;
    final Set<UUID> ownedClones = ConcurrentHashMap.newKeySet();
    volatile int killCount = 0;
    volatile long ticksRemaining = -1;
    volatile boolean oneMinuteWarningFired = false;
    ServerBossEvent bossBar;

    /** Set once Entity303 actually spawns; null before that and after it's resolved (killed or discarded). */
    volatile UUID bossUuid = null;
    /** Ensures the 50% health second-phase sequence can only ever fire once per boss instance. */
    volatile boolean secondPhaseTriggered = false;

    RitualInstance(MinecraftServer server, UUID playerUuid, ResourceKey<Level> dimension, BlockPos jukeboxPos) {
        this.server = server;
        this.playerUuid = playerUuid;
        this.dimension = dimension;
        this.jukeboxPos = jukeboxPos;
    }

    boolean isTerminal() {
        return state == RitualState.FAILED || state == RitualState.CANCELLED || state == RitualState.COMPLETED;
    }
}
