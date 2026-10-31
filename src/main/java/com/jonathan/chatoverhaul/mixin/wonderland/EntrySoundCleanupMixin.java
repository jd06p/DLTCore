package com.jonathan.chatoverhaul.mixin.wonderland;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** These initializers queue exactly one task, whose sole effect is a late "stopsound @p". */
@Mixin(targets = {
        "net.mcreator.thewonderland.procedures.AcceptanceEnterProcedure",
        "net.mcreator.thewonderland.procedures.CornersEnterProcedure",
        "net.mcreator.thewonderland.procedures.DepthsEnterProcedure",
        "net.mcreator.thewonderland.procedures.DlrowmaerdEnterProcedure",
        "net.mcreator.thewonderland.procedures.Dream6EnterProcedure",
        "net.mcreator.thewonderland.procedures.DreamworldEnterProcedure",
        "net.mcreator.thewonderland.procedures.EndlessForestEnterProcedure",
        "net.mcreator.thewonderland.procedures.FoolsParadiseEnterProcedure",
        "net.mcreator.thewonderland.procedures.MazeEnterProcedure",
        "net.mcreator.thewonderland.procedures.NoMansLandEnterProcedure"
}, remap = false)
public class EntrySoundCleanupMixin {
    @Redirect(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/mcreator/thewonderland/TheWonderlandMod;queueServerWork(ILjava/lang/Runnable;)V", ordinal = 0),
            require = 1, expect = 1, allow = 1)
    private static void chatoverhaul$removeLateStopAll(int ticks, Runnable work) {
        // Cleanup was moved before ClientboundRespawnPacket; never stop the NEW biome loop here.
    }
}
