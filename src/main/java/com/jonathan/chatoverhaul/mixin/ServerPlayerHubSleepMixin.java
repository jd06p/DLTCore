package com.jonathan.chatoverhaul.mixin;

import com.jonathan.chatoverhaul.event.BedSleepCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ServerPlayer#startSleepInBed rejects sleeping with NOT_POSSIBLE_HERE whenever
 * the dimension is not "natural". The hub reports natural = false, so sleeping
 * is refused even once the bed no longer explodes. This redirects only that one
 * natural() check for the hub; all other dimensions keep the vanilla check.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerHubSleepMixin {

    @Redirect(
            method = "startSleepInBed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/dimension/DimensionType;natural()Z"))
    private boolean chatoverhaul$allowHubSleep(DimensionType dimensionType) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (BedSleepCompat.isHub(self.level())) {
            return true;
        }
        return dimensionType.natural();
    }
}