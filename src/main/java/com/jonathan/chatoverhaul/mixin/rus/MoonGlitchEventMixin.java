package com.jonathan.chatoverhaul.mixin.rus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.mcreator.interpritation.procedures.EventEngineProcedure", remap = false)
public class MoonGlitchEventMixin {
    @Unique
    private static boolean chatoverhaul$isMoon(SoundEvent sound) {
        return sound != null && sound.getLocation().toString().equals("thebrokenscript:moonglitch");
    }

    // Only event #47 is globalized: other random events retain their per-player behavior.
    // The first online server player contributes the original frequency/1-in-55 roll.
    // No elapsed-time gate: separate successful rolls may still play on consecutive ticks.
    @Redirect(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_5594_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private static void chatoverhaul$singleServerRoll(Level level, Player excluded, BlockPos pos,
                                                    SoundEvent sound, SoundSource source, float volume, float pitch,
                                                    Event event, LevelAccessor world, double x, double y, double z,
                                                    Entity entity) {
        if (chatoverhaul$isMoon(sound) && event != null) {
            if (!(entity instanceof ServerPlayer player)
                    || player.server.getPlayerList().getPlayers().get(0) != player) return;
        }
        level.playSound(excluded, pos, sound, source, volume, pitch);
    }

    @Redirect(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7785_(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private static void chatoverhaul$noClientMoonRoll(Level level, double x, double y, double z,
                                                    SoundEvent sound, SoundSource source, float volume,
                                                    float pitch, boolean distanceDelay) {
        if (!chatoverhaul$isMoon(sound)) level.playLocalSound(x, y, z, sound, source, volume, pitch, distanceDelay);
    }
}
