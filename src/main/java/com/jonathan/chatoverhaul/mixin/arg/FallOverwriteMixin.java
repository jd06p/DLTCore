package com.jonathan.chatoverhaul.mixin.arg;

import com.jonathan.chatoverhaul.event.DismantlerToggleHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables The ARG Container's intrinsic Dismantler fall-damage negation ONLY
 * while DLTCore's activated-Dismantler mode is on.
 *
 * <p>Root cause (proven from ARG Container 0.4.3 bytecode, not guessed): the
 * ARG mod negates fall damage via
 * {@code FallOverwriteProcedure.onPlayerTick} (a {@code TickEvent.PlayerTickEvent}
 * {@code Phase.END} handler shared on the Forge event bus). On every tick, when
 * the player's MAIN hand holds the Dismantler, its private
 * {@code execute(Event, Entity)} path sets {@code entity.fallDistance = 0}
 * (SRG field {@code f_19789_}). It never touches {@code LivingFallEvent}, so a
 * plain fall-damage handler in DLTCore could not override it - the zeroing
 * already ran before any landing logic mattered.
 *
 * <p>This mixin targets exactly that private {@code execute} entry point
 * (the one real path; the public {@code execute(Entity)} overload forwards to
 * it with a null event and is a no-op by the ARG's own guard) and cancels it
 * at the head. Scaling the cancellation to the player's activation state from
 * {@link DismantlerToggleHandler}: INACTIVE players keep the ARG's original
 * zeroing (passive fall-damage negation preserved, same behavior as shipped),
 * while ACTIVATED players skip the zeroing every tick, so fallDistance
 * accumulates and the player takes completely normal Minecraft fall damage.
 *
 * <p>Only the fallDistance zeroing is affected - the Dismantler's item code
 * (harvesting, enchantment, etc.) and every unrelated damage type are never
 * touched. Scoped to the ARG's exact procedure class, and the mixin config it
 * lives in is marked "required": false, so chatoverhaul loads fine with or
 * without The ARG Container.
 */
@Mixin(targets = "net.mcreator.minecraftalphaargmod.procedures.FallOverwriteProcedure", remap = false)
public class FallOverwriteMixin {

    @Inject(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void chatoverhaul$skipFallNegationWhileActivated(
            Event event, Entity entity, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player
                && DismantlerToggleHandler.isDismantlerActivated(player)) {
            ci.cancel();
        }
    }
}