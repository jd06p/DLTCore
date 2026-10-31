package com.jonathan.chatoverhaul.mixin;

import com.jonathan.chatoverhaul.client.WonderlandVanillaMusicPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/** Client-only: reject disallowed selections before replacement or SoundInstance creation. */
@Mixin(MusicManager.class)
public abstract class MusicManagerWonderlandPolicyMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private SoundInstance currentMusic;
    @Shadow private int nextSongDelay;

    @Unique
    private boolean chatoverhaul$blocked(Music selected) {
        return minecraft.level != null && selected != null
                && WonderlandVanillaMusicPolicy.shouldBlock(minecraft.level.dimension().location(),
                        selected.getEvent().value().getLocation());
    }

    // The selected Music has just been stored in local 1. Intercept BEFORE currentMusic comparison:
    // a rejected vanilla selection must not stop an existing modded track with replaceCurrentMusic.
    @Inject(method = "tick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/client/sounds/MusicManager;currentMusic:Lnet/minecraft/client/resources/sounds/SoundInstance;", ordinal = 0),
            locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true, require = 1, expect = 1, allow = 1)
    private void chatoverhaul$rejectVanillaSelection(CallbackInfo ci, Music selected) {
        if (!chatoverhaul$blocked(selected)) return;
        // Keep an active custom track; retire only completed instances. Never call stop/play here.
        if (currentMusic != null && !minecraft.getSoundManager().isActive(currentMusic)) currentMusic = null;
        // Maintain the existing countdown without starting a rejected track or inventing a cooldown.
        // Leave a due timer ready (0 or negative), rather than underflowing during a long stay.
        nextSongDelay = Math.min(nextSongDelay, selected.getMaxDelay());
        if (currentMusic == null && nextSongDelay > 0) nextSongDelay--;
        ci.cancel();
    }

    // Covers direct MusicManager requests as well as tick(). No currentMusic assignment, MAX_VALUE
    // delay or SimpleSoundInstance.forMusic call may occur for a blocked selection.
    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true, require = 1, expect = 1, allow = 1)
    private void chatoverhaul$rejectDirectVanillaStart(Music selected, CallbackInfo ci) {
        if (chatoverhaul$blocked(selected)) ci.cancel();
    }
}
