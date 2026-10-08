package com.jonathan.chatoverhaul.mixin;

import com.jonathan.chatoverhaul.event.BedSleepCompat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects only the canSetSpawn call inside BedBlock#use. Vanilla BedBlock#use
 * explodes the bed (and the player) when canSetSpawn is false; the hub dimension
 * reports bedWorks = false, so we report true there. Targeted at this single call
 * site so RespawnAnchorBlock and Player, which also use BedBlock.canSetSpawn, are
 * left completely untouched.
 */
@Mixin(BedBlock.class)
public abstract class BedBlockHubSleepMixin {

    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/BedBlock;canSetSpawn(Lnet/minecraft/world/level/Level;)Z"))
    private boolean chatoverhaul$allowHubBed(Level level) {
        return BedSleepCompat.canSetSpawn(level);
    }
}