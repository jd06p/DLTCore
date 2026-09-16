package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * The Entity303 (evil_user_0) ritual/bossfight, triggered by playing the
 * `the_arg_container:user_0` music disc in a jukebox while inside
 * `the_arg_container:moonfalldimension`.
 *
 * ENCOUNTER LIFECYCLE (revised): `ACTIVE` now represents the WHOLE
 * encounter, not just the clone-fighting phase. It's set the moment a
 * ritual starts and stays set through the clone phase, the victory
 * dialogue, and Entity303's actual lifetime as a spawned entity - it is
 * only cleared once the encounter is genuinely OVER: the boss is killed by
 * the player, the ritual's owner dies, they disconnect mid-encounter, or
 * the ritual fails/is cancelled before the boss ever spawns. This is what
 * makes "you can't start a second ritual while Entity303 exists" and "you
 * can't escape Moonfall until the boss is dead or you are" both correct -
 * both checks just ask "is ACTIVE non-null", and it stays non-null for
 * exactly as long as an encounter genuinely needs to block those things.
 *
 * `RitualInstance.bossUuid` distinguishes the two phases: null during the
 * clone fight, set once spawnFinalBoss() runs. onLivingDeath() branches on
 * whichever of (a tracked clone, the boss itself, the ritual's own player)
 * actually died.
 *
 * GLOBAL SINGLE-RITUAL ENFORCEMENT (unchanged reasoning from before): only
 * one encounter can exist on the server at a time, which is what makes the
 * multiplayer-safety requirements trivially true - there's only ever one
 * of everything to cross-contaminate.
 *
 * ENTRY POINT: mixin/arg/JukeboxRitualGateMixin injects into The Arg
 * Container's own User0RightclickedOnBlockProcedure and unconditionally
 * cancels it, calling tryStartRitual() here instead - see that class for
 * why.
 *
 * TIMER: uses ServerBossEvent (a vanilla boss bar), not a custom
 * client-side overlay - see the class doc history in README for why (no
 * client-only code, so no dedicated-server risk).
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class Entity303RitualManager {

    private static final ResourceLocation CLONE_ID = new ResourceLocation("the_arg_container", "user_0_clone");
    private static final ResourceLocation BOSS_ID = new ResourceLocation("the_arg_container", "evil_user_0");
    private static final ResourceLocation CURSE_EFFECT_ID = new ResourceLocation("the_arg_container", "curseof_user_0");
    private static final ResourceLocation SIREN_SOUND_ID = new ResourceLocation("the_wonderland", "siren_scream_remake");
    private static final String MOONFALL_DIMENSION = "the_arg_container:moonfalldimension";

    private static final int TOTAL_CLONES = 50;
    private static final long TIMER_TICKS = (2L * 60 + 32) * 20; // 2:32
    private static final long ONE_MINUTE_TICKS = 60L * 20;
    private static final long TICK_INTERVAL = 20; // update the boss bar once a second

    private static final String KICK_MESSAGE = "Kicked by an operator.";
    private static final String REJECT_WRONG_DIMENSION = "Looks like It rejects this place.";
    private static final String REJECT_ALREADY_RUNNING = "err.youcant";
    private static final String ESCAPE_BLOCKED_MESSAGE = "You can't escape";

    private static volatile RitualInstance ACTIVE = null;

    private Entity303RitualManager() {}

    // =========================================================================
    // Entry point (called from JukeboxRitualGateMixin)
    // =========================================================================

    public static void tryStartRitual(ServerPlayer player, BlockPos jukeboxPos) {
        String dimensionId = player.level().dimension().location().toString();

        if (!dimensionId.equals(MOONFALL_DIMENSION)) {
            player.displayClientMessage(Component.literal(REJECT_WRONG_DIMENSION), true);
            return;
        }

        if (ACTIVE != null) {
            // An encounter is already running somewhere - clone phase OR
            // Entity303 is already alive. Global single-encounter
            // enforcement, see class doc.
            player.displayClientMessage(Component.literal(REJECT_ALREADY_RUNNING), true);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        RitualInstance instance = new RitualInstance(server, player.getUUID(), player.level().dimension(), jukeboxPos.immutable());
        ACTIVE = instance;

        long t = 0;
        t += schedule(server, t, "You don't know what you are trying to do.");
        t += schedule(server, t, "But as they say...");
        t += schedule(server, t, "Curiosity killed the cat.");
        t += schedule(server, t, "Very well.");

        TickScheduler.scheduleInTicks(t + seconds(2), () -> beginBossfight(instance));
    }

    /** Schedules one line at delay `t`, returns the gap to add before the next line. */
    private static long schedule(MinecraftServer server, long t, String line) {
        TickScheduler.scheduleInTicks(t, () -> broadcastEntity303(server, line));
        return seconds(3);
    }

    private static long seconds(int s) {
        return s * 20L;
    }

    private static void broadcastEntity303(MinecraftServer server, String line) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("<Entity303> " + line), false);
    }

    // =========================================================================
    // Bossfight start (clone phase)
    // =========================================================================

    private static void beginBossfight(RitualInstance instance) {
        if (instance.isTerminal()) {
            return; // jukebox was broken / player left during the intro dialogue
        }

        ServerPlayer player = instance.server.getPlayerList().getPlayer(instance.playerUuid);
        if (player == null) {
            endEncounter(instance);
            return;
        }

        instance.state = RitualState.ACTIVE;
        instance.ticksRemaining = TIMER_TICKS;

        MobEffect curse = ForgeRegistries.MOB_EFFECTS.getValue(CURSE_EFFECT_ID);
        if (curse != null) {
            player.addEffect(new MobEffectInstance(curse, (int) TIMER_TICKS, 1, false, false));
        }

        instance.bossBar = new ServerBossEvent(Component.literal(formatTime(instance.ticksRemaining)),
                BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        instance.bossBar.addPlayer(player);
        instance.bossBar.setProgress(1.0f);

        tickTimer(instance);
    }

    private static void tickTimer(RitualInstance instance) {
        if (instance.isTerminal()) {
            return;
        }

        instance.ticksRemaining -= TICK_INTERVAL;

        if (instance.bossBar != null) {
            instance.bossBar.setName(Component.literal(formatTime(Math.max(instance.ticksRemaining, 0))));
            instance.bossBar.setProgress(Math.max(0.0f, (float) instance.ticksRemaining / TIMER_TICKS));
        }

        if (!instance.oneMinuteWarningFired && instance.ticksRemaining <= ONE_MINUTE_TICKS) {
            instance.oneMinuteWarningFired = true;
            if (instance.bossBar != null) {
                instance.bossBar.setColor(BossEvent.BossBarColor.RED);
            }
            long t = 0;
            t += schedule(instance.server, t, "Even if you keep trying to fight it, this world doesn't belong to you.");
            schedule(instance.server, t, "But you already know that, don't you?");
        }

        if (instance.ticksRemaining <= 0) {
            triggerFailure(instance);
            return;
        }

        TickScheduler.scheduleInTicks(TICK_INTERVAL, () -> tickTimer(instance));
    }

    private static String formatTime(long ticks) {
        long totalSeconds = ticks / 20;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // =========================================================================
    // Entity tracking - clone attribution and stray-boss suppression
    // =========================================================================

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        RitualInstance instance = ACTIVE;
        if (instance == null || instance.state != RitualState.ACTIVE) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(instance.dimension)) {
            return;
        }

        Entity entity = event.getEntity();
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (typeId == null) {
            return;
        }

        if (typeId.equals(CLONE_ID)) {
            instance.ownedClones.add(entity.getUUID());
        } else if (typeId.equals(BOSS_ID)) {
            // Suppress the mod's own chance-based early evil_user_0 spawn
            // during the clone fight - the only legitimate spawn is the
            // one this class creates itself at the true end of a
            // successful clone phase.
            event.setCanceled(true);
        }
    }

    // =========================================================================
    // Death handling - clone kills, the boss's own death, and the player's
    // =========================================================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        RitualInstance instance = ACTIVE;
        if (instance == null) {
            return;
        }

        Entity entity = event.getEntity();

        // --- Case 1: a tracked clone died (clone-fighting phase only) ---
        if (instance.state == RitualState.ACTIVE && instance.ownedClones.remove(entity.getUUID())) {
            instance.killCount++;
            if (instance.killCount >= TOTAL_CLONES) {
                triggerVictory(instance);
            }
            return;
        }

        // --- Case 2: the boss itself was killed by the player - the true final victory ---
        if (instance.bossUuid != null && entity.getUUID().equals(instance.bossUuid)) {
            instance.bossUuid = null;
            endEncounter(instance);
            return;
        }

        // --- Case 3: the ritual's own player died during the encounter (either phase) ---
        if (entity instanceof Player && entity.getUUID().equals(instance.playerUuid)) {
            boolean killedByEntity303 = wasKilledByBoss(event.getSource());
            resetEncounterAfterPlayerExit(instance);

            if (killedByEntity303) {
                ServerPlayer player = instance.server.getPlayerList().getPlayer(instance.playerUuid);
                if (player != null) {
                    player.connection.disconnect(Component.literal(KICK_MESSAGE));
                }
            }
        }
    }

    private static boolean wasKilledByBoss(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker == null) {
            return false;
        }
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(attacker.getType());
        return typeId != null && typeId.equals(BOSS_ID);
    }

    /** Discards Entity303 if it has spawned, tears down any still-running clone-phase state, and ends the encounter. No dialogue/kick here - callers handle that. */
    private static void resetEncounterAfterPlayerExit(RitualInstance instance) {
        if (instance.bossUuid != null) {
            ServerLevel level = instance.server.getLevel(instance.dimension);
            if (level != null) {
                Entity boss = level.getEntity(instance.bossUuid);
                if (boss != null) {
                    boss.discard();
                }
            }
            instance.bossUuid = null;
        }

        if (!instance.isTerminal()) {
            cleanupCombatState(instance, RitualState.CANCELLED);
        }

        endEncounter(instance);
    }

    // =========================================================================
    // Jukebox-break cancellation (clone phase only - matches original scope)
    // =========================================================================

    @SubscribeEvent
    public static void onJukeboxBreak(BlockEvent.BreakEvent event) {
        RitualInstance instance = ACTIVE;
        if (instance == null || instance.isTerminal()) {
            return;
        }
        if (!(event.getLevel() instanceof Level level) || !level.dimension().equals(instance.dimension)) {
            return;
        }
        if (!event.getPos().equals(instance.jukeboxPos)) {
            return;
        }

        triggerCancelled(instance);
    }

    // =========================================================================
    // Escape prevention - covers ANY dimension change, regardless of method
    // =========================================================================

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        RitualInstance instance = ACTIVE;
        if (instance == null) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getUUID().equals(instance.playerUuid)) {
            return;
        }

        boolean wasInMoonfall = event.getFrom().location().toString().equals(MOONFALL_DIMENSION);
        boolean nowInMoonfall = event.getTo().location().toString().equals(MOONFALL_DIMENSION);
        if (!wasInMoonfall || nowInMoonfall) {
            return;
        }

        ServerLevel moonfall = instance.server.getLevel(instance.dimension);
        if (moonfall != null) {
            player.teleportTo(moonfall,
                    instance.jukeboxPos.getX() + 0.5, instance.jukeboxPos.getY() + 1, instance.jukeboxPos.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
        }
        player.displayClientMessage(Component.literal(ESCAPE_BLOCKED_MESSAGE), true);
    }

    // =========================================================================
    // Block-placement prevention in Moonfall for the duration of the encounter
    // =========================================================================

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        RitualInstance instance = ACTIVE;
        if (instance == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!(event.getLevel() instanceof Level level) || !level.dimension().equals(instance.dimension)) {
            return;
        }
        event.setCanceled(true);
    }

    /** Multi-block placements (beds, doors, and similar) fire this instead of EntityPlaceEvent - covered the same way. */
    @SubscribeEvent
    public static void onMultiBlockPlace(BlockEvent.EntityMultiPlaceEvent event) {
        RitualInstance instance = ACTIVE;
        if (instance == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!(event.getLevel() instanceof Level level) || !level.dimension().equals(instance.dimension)) {
            return;
        }
        event.setCanceled(true);
    }

    // =========================================================================
    // Player disconnect - quiet cleanup, no dramatic sequence (no one to see it)
    // =========================================================================

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RitualInstance instance = ACTIVE;
        if (instance == null) {
            return;
        }
        if (!event.getEntity().getUUID().equals(instance.playerUuid)) {
            return;
        }

        resetEncounterAfterPlayerExit(instance);
    }

    // =========================================================================
    // Terminal outcomes
    // =========================================================================

    private static void triggerVictory(RitualInstance instance) {
        if (instance.isTerminal()) {
            return;
        }
        // Clone phase won - do NOT end the encounter yet, it continues into
        // the boss phase. cleanupCombatState only tears down the clone-fight
        // bookkeeping (curse, remaining clones, timer bar).
        cleanupCombatState(instance, RitualState.COMPLETED);

        MinecraftServer server = instance.server;

        long t = 0;
        t += schedule(server, t, "So you did it, huh?");
        t += schedule(server, t, "I see.");
        t += schedule(server, t, "Very well, then.");
        t += schedule(server, t, "I should reward you.");

        long finalLineDelay = t;
        TickScheduler.scheduleInTicks(finalLineDelay, () ->
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("\u00a74<Entity303> \u00a7kTHE END IS NIGH"), false));

        long soundDelay = finalLineDelay + seconds(2);
        TickScheduler.scheduleInTicks(soundDelay, () -> {
            ServerPlayer current = server.getPlayerList().getPlayer(instance.playerUuid);
            if (current != null) {
                current.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 2.0f, 0.0f);
                current.addEffect(new MobEffectInstance(MobEffects.DARKNESS, (int) seconds(10), 0, false, false));
            }
        });

        long secondSoundDelay = soundDelay + seconds(2);
        TickScheduler.scheduleInTicks(secondSoundDelay, () -> {
            ServerPlayer current = server.getPlayerList().getPlayer(instance.playerUuid);
            if (current != null) {
                current.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 2.0f, 0.0f);
            }
        });

        long hereIAmDelay = secondSoundDelay + seconds(2);
        TickScheduler.scheduleInTicks(hereIAmDelay, () ->
                server.getPlayerList().broadcastSystemMessage(Component.literal("\u00a74<Entity303> HERE I AM"), false));

        long sirenDelay = hereIAmDelay + seconds(1);
        TickScheduler.scheduleInTicks(sirenDelay, () -> {
            ServerPlayer current = server.getPlayerList().getPlayer(instance.playerUuid);
            SoundEvent siren = ForgeRegistries.SOUND_EVENTS.getValue(SIREN_SOUND_ID);
            if (current != null && siren != null) {
                current.playNotifySound(siren, SoundSource.HOSTILE, 3.0f, 1.0f);
            }
        });

        long spawnDelay = sirenDelay + seconds(3);
        TickScheduler.scheduleInTicks(spawnDelay, () -> spawnFinalBoss(instance));
    }

    private static void spawnFinalBoss(RitualInstance instance) {
        if (ACTIVE != instance) {
            return; // encounter already ended (e.g. player disconnected) during the victory dialogue
        }

        MinecraftServer server = instance.server;
        ServerLevel level = server.getLevel(instance.dimension);
        if (level == null) {
            return;
        }
        EntityType<?> bossType = ForgeRegistries.ENTITY_TYPES.getValue(BOSS_ID);
        if (bossType == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(instance.playerUuid);
        BlockPos spawnPos = player != null ? player.blockPosition() : instance.jukeboxPos;

        Entity boss = bossType.create(level);
        if (boss == null) {
            return;
        }
        boss.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0f, 0.0f);
        level.addFreshEntity(boss);

        instance.bossUuid = boss.getUUID();
    }

    private static void triggerFailure(RitualInstance instance) {
        if (instance.isTerminal()) {
            return;
        }
        cleanupCombatState(instance, RitualState.FAILED);
        endEncounter(instance);
        runFailOrCancelSequence(instance, "Looks like you failed.", "As expected of an NPC.");
    }

    private static void triggerCancelled(RitualInstance instance) {
        if (instance.isTerminal()) {
            return;
        }
        cleanupCombatState(instance, RitualState.CANCELLED);
        endEncounter(instance);
        runFailOrCancelSequence(instance, "Looks like you got scared.", "I guess I was hoping you would at least try.");
    }

    private static void runFailOrCancelSequence(RitualInstance instance, String line1, String line2) {
        MinecraftServer server = instance.server;

        long t = 0;
        t += schedule(server, t, line1);
        t += schedule(server, t, line2);

        long soundDelay = t + seconds(2);
        TickScheduler.scheduleInTicks(soundDelay, () -> {
            ServerPlayer current = server.getPlayerList().getPlayer(instance.playerUuid);
            if (current != null) {
                current.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 2.0f, 0.0f);
                current.addEffect(new MobEffectInstance(MobEffects.DARKNESS, (int) seconds(8), 0, false, false));
            }
        });

        long kickDelay = soundDelay + seconds(3);
        TickScheduler.scheduleInTicks(kickDelay, () -> {
            ServerPlayer current = server.getPlayerList().getPlayer(instance.playerUuid);
            if (current != null) {
                current.connection.disconnect(Component.literal(KICK_MESSAGE));
            }
        });
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    /** Immediate, silent clone-phase cleanup - removes the curse, kills owned clones, stops the timer/boss bar. Does NOT touch the global ACTIVE reference or play any dialogue/effects/kick. */
    private static void cleanupCombatState(RitualInstance instance, RitualState finalState) {
        instance.state = finalState;

        MinecraftServer server = instance.server;
        ServerPlayer player = server.getPlayerList().getPlayer(instance.playerUuid);
        if (player != null) {
            MobEffect curse = ForgeRegistries.MOB_EFFECTS.getValue(CURSE_EFFECT_ID);
            if (curse != null) {
                player.removeEffect(curse);
            }
        }

        ServerLevel level = server.getLevel(instance.dimension);
        if (level != null) {
            for (UUID uuid : instance.ownedClones) {
                Entity clone = level.getEntity(uuid);
                if (clone != null) {
                    clone.discard();
                }
            }
        }
        instance.ownedClones.clear();

        if (instance.bossBar != null) {
            instance.bossBar.removeAllPlayers();
            instance.bossBar = null;
        }
    }

    /** Marks the WHOLE encounter over - only after this does tryStartRitual() allow a new one, escape prevention stop, and block placement resume. */
    private static void endEncounter(RitualInstance instance) {
        if (ACTIVE == instance) {
            ACTIVE = null;
        }
    }
}
