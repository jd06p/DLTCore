package com.jonathan.chatoverhaul.mixin.battlemusic;

import com.nekotune.battlemusic.BattleMusicInstance;
import com.jonathan.chatoverhaul.compat.battlemusic.BattleMusicGain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import com.jonathan.chatoverhaul.compat.battlemusic.BattleMusicLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.nekotune.battlemusic.BattleMusicInstance", remap = false)
public abstract class BattleMusicInstanceMixin extends AbstractTickableSoundInstance implements BattleMusicGain {
    private BattleMusicInstanceMixin(SoundEvent event, SoundSource category, RandomSource random) {
        super(event, category, random);
    }

    @Override
    public float getVolume() {
        return super.getVolume() * Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC);
    }

    @Override
    public float dltcore$unscaledGain() {
        return super.getVolume();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dltcore$track(CallbackInfo ci) {
        BattleMusicLifecycle.created((BattleMusicInstance) (Object) this);
    }
    @Inject(method = "destroy", at = @At("TAIL"))
    private void dltcore$releaseTarget(CallbackInfo ci) {
        BattleMusicLifecycle.destroyed((BattleMusicInstance) (Object) this);
    }
    @Inject(method = "fade", at = @At("TAIL"))
    private void dltcore$boundFade(float seconds, CallbackInfo ci) {
        BattleMusicLifecycle.fading((BattleMusicInstance) (Object) this, seconds);
    }
    @Inject(method = "m_7788_", at = @At("HEAD"), cancellable = true)
    private void dltcore$guardSoundTick(CallbackInfo ci) {
        if (BattleMusicLifecycle.stopIfInvalid((BattleMusicInstance) (Object) this)) ci.cancel();
    }
}


