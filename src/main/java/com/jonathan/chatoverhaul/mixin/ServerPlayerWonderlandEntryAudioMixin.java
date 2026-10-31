package com.jonathan.chatoverhaul.mixin;

import com.jonathan.chatoverhaul.event.WonderlandEntryAudio;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.ITeleporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerWonderlandEntryAudioMixin {
    // Only successful cross-dimension branches construct this packet. Same-world teleports bypass it.
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",
            at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundRespawnPacket"),
            require = 1, expect = 1, allow = 1)
    private void chatoverhaul$cleanupBeforeTeleport(ServerLevel destination, double x, double y, double z,
                                                  float yaw, float pitch, CallbackInfo ci) {
        WonderlandEntryAudio.beforeRespawn((ServerPlayer) (Object) this, destination);
    }

    // Forge's extra ITeleporter overload is not a vanilla/SRG method; its selector must not be remapped.
    @Inject(method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",
            at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundRespawnPacket"),
            remap = false, require = 1, expect = 1, allow = 1)
    private void chatoverhaul$cleanupBeforePortal(ServerLevel destination, ITeleporter teleporter,
                                                CallbackInfoReturnable<Entity> cir) {
        WonderlandEntryAudio.beforeRespawn((ServerPlayer) (Object) this, destination);
    }
}
