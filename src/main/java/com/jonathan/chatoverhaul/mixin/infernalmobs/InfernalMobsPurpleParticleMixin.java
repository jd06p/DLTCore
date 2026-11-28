package com.jonathan.chatoverhaul.mixin.infernalmobs;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Infernal Mobs 1.20.1.11 emits its purple ParticleTypes.WITCH effect from
 * this client overlay lambda. Cancelling only that lambda leaves the overlay's
 * entity scan, modifier data, abilities, and all other particle sources intact.
 */
@Pseudo
@Mixin(targets = "atomicstryker.infernalmobs.client.RendererBossGlow$InfernalMobsBossGlowOverlay", remap = false)
public abstract class InfernalMobsPurpleParticleMixin {

    @Inject(
            method = "lambda$render$1(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void dltcore$cancelInfernalPurpleParticle(Minecraft minecraft,
                                                              LivingEntity entity,
                                                              CallbackInfo ci) {
        ci.cancel();
    }
}
