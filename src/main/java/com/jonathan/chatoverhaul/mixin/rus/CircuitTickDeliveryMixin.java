package com.jonathan.chatoverhaul.mixin.rus;

import com.jonathan.chatoverhaul.event.CircuitChaseDelivery;
import net.mcreator.interpritation.network.ThebrokenscriptModVariables.MapVariables;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.mcreator.interpritation.procedures.CircuitOnEntityTickUpdateProcedure", remap = false)
public class CircuitTickDeliveryMixin {
    // The entity ticks on both sides; its NBT timer is NOT network-synchronized.
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private static void chatoverhaul$serverAuthority(LevelAccessor level, double x, double y, double z,
                                                    Entity circuit, CallbackInfo ci) {
        if (level.isClientSide()) ci.cancel();
    }

    @Redirect(method = "execute", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/mcreator/interpritation/network/ThebrokenscriptModVariables$MapVariables;nowayoutframe:D"))
    private static double chatoverhaul$readFrame(MapVariables ignored, LevelAccessor level,
                                               double x, double y, double z, Entity circuit) {
        return CircuitChaseDelivery.frame(circuit);
    }

    @Redirect(method = "execute", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/mcreator/interpritation/network/ThebrokenscriptModVariables$MapVariables;nowayoutframe:D"))
    private static void chatoverhaul$writeFrame(MapVariables ignored, double frame, LevelAccessor level,
                                              double x, double y, double z, Entity circuit) {
        CircuitChaseDelivery.frame(circuit, frame);
    }

    @Redirect(method = "execute", at = @At(value = "INVOKE",
            target = "Lnet/mcreator/interpritation/network/ThebrokenscriptModVariables$MapVariables;syncData(Lnet/minecraft/world/level/LevelAccessor;)V"))
    private static void chatoverhaul$noGlobalFrameSync(MapVariables ignored, LevelAccessor level) {
        // The original sync calls only follow frame writes. A per-player snapshot is sent at tick END.
    }

    @Redirect(method = "execute", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_5594_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private static void chatoverhaul$targetSound(Level level, Player excluded, BlockPos pos, SoundEvent sound,
                                               SoundSource source, float volume, float pitch,
                                               LevelAccessor world, double x, double y, double z, Entity circuit) {
        CircuitChaseDelivery.sound(circuit, pos, sound, source, volume, pitch);
    }

    @Redirect(method = "execute", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;m_7618_(Lnet/minecraft/commands/arguments/EntityAnchorArgument$Anchor;Lnet/minecraft/world/phys/Vec3;)V"))
    private static void chatoverhaul$targetLook(Entity ignored, EntityAnchorArgument.Anchor anchor, Vec3 pos,
                                              LevelAccessor level, double x, double y, double z, Entity circuit) {
        Player player = CircuitChaseDelivery.target(circuit);
        if (player != null) player.lookAt(anchor, pos);
    }

    @Redirect(method = "execute", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityType;m_262496_(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;)Lnet/minecraft/world/entity/Entity;"))
    private static Entity chatoverhaul$trackFlash(EntityType<?> type, ServerLevel server, BlockPos pos,
                                                MobSpawnType reason, LevelAccessor level,
                                                double x, double y, double z, Entity circuit) {
        Entity spawned = type.spawn(server, pos, reason);
        if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()
                .equals("circuit_jumpscare_frame_entity")) CircuitChaseDelivery.flash(circuit, spawned);
        return spawned;
    }

    @Inject(method = "execute", at = @At("TAIL"))
    private static void chatoverhaul$trackChase(LevelAccessor level, double x, double y, double z,
                                              Entity circuit, CallbackInfo ci) {
        if (circuit != null) CircuitChaseDelivery.frame(circuit, CircuitChaseDelivery.frame(circuit));
    }
}
