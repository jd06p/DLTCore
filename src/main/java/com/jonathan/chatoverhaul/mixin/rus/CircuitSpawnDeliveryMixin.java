package com.jonathan.chatoverhaul.mixin.rus;

import com.jonathan.chatoverhaul.event.CircuitChaseDelivery;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.mcreator.interpritation.procedures.CircuitOnInitialEntitySpawnProcedure", remap = false)
public class CircuitSpawnDeliveryMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private static void chatoverhaul$spawnTarget(LevelAccessor level, double x, double y, double z,
                                               Entity circuit, CallbackInfo ci) {
        if (level.isClientSide()) ci.cancel();
        else CircuitChaseDelivery.spawned(circuit);
    }

    @Redirect(method = "execute", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_5594_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private static void chatoverhaul$targetSound(Level level, Player excluded, BlockPos pos, SoundEvent sound,
                                               SoundSource source, float volume, float pitch,
                                               LevelAccessor world, double x, double y, double z, Entity circuit) {
        CircuitChaseDelivery.sound(circuit, pos, sound, source, volume, pitch);
    }
}
