package com.jonathan.chatoverhaul.mixin.rus;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@Mixin(targets = "net.mcreator.interpritation.procedures.PlayerJoinsMoonGlitchProcedure", remap = false)
public class MoonGlitchJoinMixin {
    @Unique
    private static final Set<MinecraftServer> chatoverhaul$scheduled =
            Collections.newSetFromMap(new WeakHashMap<>());

    // One global ten-minute sequence per running server, not one broadcast per login.
    // Keep RUS's original runnable, delay, sound, position, volume and pitch.
    @Inject(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void chatoverhaul$oneGlobalSchedule(Event event, LevelAccessor level,
                                                     double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (entity == null) return;
        if (!(level instanceof ServerLevel serverLevel)
                || !chatoverhaul$scheduled.add(serverLevel.getServer())) ci.cancel();
    }
}
