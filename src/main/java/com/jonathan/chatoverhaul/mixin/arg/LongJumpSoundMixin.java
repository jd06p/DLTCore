package com.jonathan.chatoverhaul.mixin.arg;

import com.jonathan.chatoverhaul.event.DashSoundCompat;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses the redundant client-side playback of the ARG Container Long
 * Jump sound (the keybind labelled "Long Jump", driven by SmallDashMessage
 * and executed by SpaceDashProcedure). See DashSoundCompat for the full
 * analysis; this is the same defect in the mod's second dash-like ability,
 * so it delegates to the same shared handler rather than repeating the fix.
 *
 * The server-side broadcast in execute() is left untouched.
 */
@Mixin(targets = "net.mcreator.minecraftalphaargmod.procedures.SpaceDashProcedure", remap = false)
public class LongJumpSoundMixin {

    @Redirect(
            method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;m_7785_(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
                    remap = false),
            remap = false)
    private static void chatoverhaul$suppressDuplicateLongJumpSound(Level level, double x, double y, double z,
                                                                      SoundEvent sound, SoundSource source,
                                                                      float volume, float pitch, boolean random) {
        DashSoundCompat.suppressDuplicateClientPlayback(level, sound);
    }
}