package com.jonathan.chatoverhaul.mixin.cpapi;

import net.kyrptonaught.customportalapi.portal.PortalIgnitionSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Blocks Custom Portal API portal ignition entirely while inside any
 * Wonderland, Broken Script, or Arg Container dimension - except for a
 * small list of specific dimensions carved out as exceptions, currently
 * `the_arg_container:moonfalldimension` and `the_arg_container:soul_d`,
 * where portal creation stays allowed so players have a way out if their
 * existing portal there breaks.
 *
 * A previous attempt hooked Forge's generic BlockEvent.PortalSpawnEvent,
 * assuming Custom Portal API would use that shared infrastructure like a
 * well-behaved Forge portal mod. It doesn't: inspecting its actual jar
 * shows PortalPlacer.createPortal() builds and lights portals entirely on
 * its own, without firing any Forge event at all - so that hook was a
 * silent no-op.
 *
 * This targets the REAL choke point instead. Every ignition path in this
 * mod - item-igniter use (CustomPortalsMod#onRightClickItem), fire
 * placement (AbstractFireMixin), fluid placement (FluidBlockPlacedMixin),
 * and potion dowsing (PotionEntityMixin) - all call this exact same
 * method, PortalPlacer#attemptPortalLight(Level, BlockPos,
 * PortalIgnitionSource), confirmed directly in the decompiled sources of
 * each. Short-circuiting it to return false when the world's dimension is
 * protected (and not exempted) reads as "ignition simply didn't take" to
 * every one of those call sites - the same as using the wrong item on the
 * wrong frame - so nothing downstream needs special-casing, and no portal
 * type or ignition method can bypass this by construction.
 *
 * Compiled against libs/cpapireforged-1.0.4.jar (compileOnly, not bundled
 * or required at runtime). Lives in its own "required": false mixin
 * config (mixins.chatoverhaul.cpapi.json) so chatoverhaul still loads fine
 * in a pack without Custom Portal API installed - this mixin just has
 * nothing to attach to in that case.
 */
@Mixin(targets = "net.kyrptonaught.customportalapi.portal.PortalPlacer", remap = false)
public class PortalPlacerMixin {

    /** Specific dimension IDs exempted from the namespace block below. */
    private static final Set<String> ALLOWED_EXCEPTIONS = Set.of(
            "the_arg_container:moonfalldimension",
            "the_arg_container:soul_d"
    );

    @Inject(method = "attemptPortalLight(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/kyrptonaught/customportalapi/portal/PortalIgnitionSource;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void chatoverhaul$blockProtectedDimensions(Level world, BlockPos portalPos,
                                                                PortalIgnitionSource ignitionSource,
                                                                CallbackInfoReturnable<Boolean> cir) {
        String dimensionId = world.dimension().location().toString();
        if (ALLOWED_EXCEPTIONS.contains(dimensionId)) {
            return;
        }

        String namespace = world.dimension().location().getNamespace();
        if (namespace.equals("the_wonderland")
                || namespace.equals("thebrokenscript")
                || namespace.equals("the_arg_container")) {
            cir.setReturnValue(false);
        }
    }
}
