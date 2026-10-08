package com.jonathan.chatoverhaul.mixin;

import com.jonathan.chatoverhaul.event.OperatorNoticeHandler;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opening a singleplayer world to LAN with "Allow Cheats" enabled has no
 * Forge event. IntegratedServer#publishServer drives it through
 * PlayerList#setAllowCheatsForAllPlayers(boolean), so that is the one place
 * this feature taps the vanilla permission system - nothing is rewritten,
 * cancelled, or otherwise altered; the original call still runs normally and
 * we only observe the true case on the way out.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListCheatsNoticeMixin {

    @Inject(method = "setAllowCheatsForAllPlayers", at = @At("RETURN"))
    private void chatoverhaul$noticeOnAllowCheats(boolean allowCheats, CallbackInfo ci) {
        if (allowCheats) {
            OperatorNoticeHandler.onAllowCheatsEnabled((PlayerList) (Object) this);
        }
    }
}