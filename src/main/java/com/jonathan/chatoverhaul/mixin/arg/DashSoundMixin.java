package com.jonathan.chatoverhaul.mixin.arg;

import com.jonathan.chatoverhaul.event.DashSoundCompat;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses the redundant client-side playback of the ARG Container Dash
 * sound. See DashSoundCompat for the full analysis.
 *
 * Both client-side sound call sites inside execute() are redirected: the
 * normal dash (volume 0.6f) and the second dash/jump branch (volume 1.0f),
 * so a single activation of either can never play the sound twice. The
 * server-side broadcast in the same method is left untouched, which is what
 * still makes the sound audible to the dasher and to everyone else nearby.
 *
 * Descriptor is spelled out explicitly because ARG Container declares two
 * execute() overloads, and an ambiguous "execute" selector silently resolves
 * to the wrong one.
 */
@Mixin(targets = "net.mcreator.minecraftalphaargmod.procedures.DashProcedureProcedure", remap = false)
public class DashSoundMixin {

    @Redirect(
            method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;m_7785_(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
                    remap = false),
            remap = false)
    private static void chatoverhaul$suppressDuplicateDashSound(Level level, double x, double y, double z,
                                                                SoundEvent sound, SoundSource source,
                                                                float volume, float pitch, boolean random) {
        DashSoundCompat.suppressDuplicateClientPlayback(level, sound);
    }
}