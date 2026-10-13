package com.jonathan.chatoverhaul.mixin.rus;

import com.mojang.logging.LogUtils;
import net.mcreator.interpritation.network.ThebrokenscriptModVariables;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.Event;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes the duplicated "null joined the game" (rendered as the U+273A symbol)
 * line that rus-patch emits more than once per world.
 *
 * The failure this fixes:
 *
 *   FirstJoinProcedure.execute resets a handful of its "has spawned" flags,
 *   then, only while its per-world {@code firstjoin} flag is true, captures
 *   the joining player's position and schedules
 *   {@code queueServerWork(18000, lambda$execute$0)}. That lambda is what
 *   prints the "null joined the game" tellraw (and plays the cave sound) and
 *   only THEN sets {@code firstjoin = false}.
 *
 *   The flag is therefore still true for the whole 15 minutes that follow the
 *   first login, so every additional player who logs in during that window
 *   passes the check and schedules another copy of the same one-time event.
 *   Two logins 40 seconds apart produce two "null joined the game" lines
 *   ~15 minutes later, exactly 41 seconds apart (observed), even though only
 *   one first join has actually happened.
 *
 * The fix:
 *
 *   Consume {@code firstjoin} at the end of the very first execute, the
 *   moment that first join has been acknowledged, instead of waiting 18000
 *   ticks for the scheduled lambda to run. {@code firstjoin} is only ever
 *   read in this one method (verified: the field name appears in exactly
 *   FirstJoinProcedure and MapVariables in the closed rus-patch jar), so
 *   clearing it here cannot affect any other Broken Script behaviour. The
 *   single already-scheduled lambda still runs after its own 15 minutes and
 *   performs its full side effects (isnullspawned, nullishere, the tellraw
 *   and the cave sound) exactly once - later logins simply see
 *   {@code firstjoin == false} and no longer schedule a second one.
 *
 * This is deliberately a per-world flag change at the source, not a timed
 * cooldown and not a suppression of the recurring message: rus-patch's own
 * random-event path can still legitimately produce "null joined the game"
 * again later, and that still flows through untouched.
 *
 * The DLTCore first-time Local Play sequence (the scripted "[@] ..." / angle
 * bracket intro) is a completely separate mechanism and is not involved here.
 */
@Mixin(targets = "net.mcreator.interpritation.procedures.FirstJoinProcedure", remap = false)
public class FirstJoinProcedureMixin {

    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Unique
    private static boolean chatoverhaul$firstJoinConsumedReported = false;

    @Inject(
            method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",
            at = @At("TAIL"),
            remap = false
    )
    private static void chatoverhaul$consumeFirstJoin(Event event, LevelAccessor level,
                                                      double x, double y, double z,
                                                      CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        ThebrokenscriptModVariables.MapVariables vars = ThebrokenscriptModVariables.MapVariables.get(level);
        if (vars != null && vars.firstjoin) {
            vars.firstjoin = false;
            vars.syncData(level);
            if (!chatoverhaul$firstJoinConsumedReported) {
                chatoverhaul$firstJoinConsumedReported = true;
                LOGGER.info("[DLTCore] Broken Script FirstJoinProcedure deduplicated: "
                        + "null-join tellraw will be scheduled once per world");
            }
        }
    }
}
