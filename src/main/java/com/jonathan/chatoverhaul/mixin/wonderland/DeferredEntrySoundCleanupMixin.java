package com.jonathan.chatoverhaul.mixin.wonderland;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = {
        "net.mcreator.thewonderland.procedures.TunnelsEnterProcedure",
        "net.mcreator.thewonderland.procedures.WhatOnceWasEnterProcedure"
}, remap = false)
public class DeferredEntrySoundCleanupMixin {
    @Redirect(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/mcreator/thewonderland/TheWonderlandMod;queueServerWork(ILjava/lang/Runnable;)V", ordinal = 1),
            require = 1, expect = 1, allow = 1)
    private static void chatoverhaul$removeLateStopAll(int ticks, Runnable work) {
        // The FIRST queued task is an essential one-tick teleport. Leave it entirely unchanged.
        // Only the SECOND task is the destructive three-tick stop-all command.
    }
}
