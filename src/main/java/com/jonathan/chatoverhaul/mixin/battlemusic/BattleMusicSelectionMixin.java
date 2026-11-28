package com.jonathan.chatoverhaul.mixin.battlemusic;

import com.jonathan.chatoverhaul.compat.battlemusic.BattleMusicLifecycle;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.LogicalSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.nekotune.battlemusic.BattleMusic$ForgeEvents", remap = false)
public abstract class BattleMusicSelectionMixin {
    @Inject(method = "onLevelTick", at = @At("HEAD"))
    private static void dltcore$clearInvalidCurrent(TickEvent.LevelTickEvent event, CallbackInfo ci) {
        if (event.phase == TickEvent.Phase.END && event.side == LogicalSide.CLIENT)
            BattleMusicLifecycle.beforeSelection();
    }
    // This Forge event runs on the integrated SERVER thread. The native handler incorrectly touches
    // client audio there. The client unload/clone/tick hooks handle the actual client transition instead.
    @Inject(method = "onPlayerChangedDimension", at = @At("HEAD"), cancellable = true)
    private static void dltcore$useClientLifecycle(PlayerEvent.PlayerChangedDimensionEvent event, CallbackInfo ci) {
        ci.cancel();
    }
}
