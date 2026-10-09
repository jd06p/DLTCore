package com.jonathan.chatoverhaul.mixin.instability;

import com.jonathan.chatoverhaul.client.InstabilityWindowGuard;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes the INSTABILITY music-disc's permanent window corruption (its tick
 * procedure reruns forever and never restores the player's window).
 *
 * <p>Root cause (proven from INSTABILITY's bytecode, not guessed):
 * {@code InstabilityRightclickedOnBlockProcedure} sets the map variable
 * {@code isDiscPlaying = true} and nothing ever resets it, so
 * {@code GItchtimerupdateProcedure.onPlayerTick} (a {@code PlayerTickEvent}
 * {@code Phase.END} handler) keeps calling its private
 * {@code execute(Event, LevelAccessor)} on the client permanently. Every call
 * raw-{@code GLFW}s the window to a torn state (windowed 854x480, centered,
 * hardcoded title "Minecraft Forge* 1.20.1 - Singleplayer" once it has played
 * for 5540 ticks) with no restore path, so a player's original window mode,
 * size, position and title are lost for the rest of the game session.
 *
 * <p>An event handler could not fix this - a sibling bus subscriber's per-tick
 * {@code GLFW} writes are not cancellable from another listener - so this mixin
 * targets the procedure's one real entry point (the private
 * {@code execute(Event, LevelAccessor)} static; the public
 * {@code execute(LevelAccessor)} overload forwards to it) and lets
 * {@link InstabilityWindowGuard} decide: the full glitch is allowed to play out
 * normally, and only once the effect stabilizes (restored window + sealed) does
 * the mixin cancel the procedure forever via the guard's return value.
 *
 * <p>Client-side only: this class is registered in the "client" array of
 * {@code mixins.chatoverhaul.instability.json}, so a dedicated server neither
 * applies the mixin nor ever loads the client-only guard class, and the
 * procedure's server-side bookkeeping ({@code discPlayTicks}, world sync)
 * keeps working untouched. The config is "required": false, so chatoverhaul
 * loads fine with or without the INSTABILITY mod.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net.mcreator.instabilitymusicdisk.procedures.GItchtimerupdateProcedure", remap = false)
public class GItchtimerupdateProcedureMixin {

    @Inject(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void chatoverhaul$instabilityWindowGuard(Event event, LevelAccessor level, CallbackInfo ci) {
        if (level != null && level.isClientSide() && InstabilityWindowGuard.shouldCancel(level)) {
            ci.cancel();
        }
    }
}