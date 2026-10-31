package com.jonathan.chatoverhaul.mixin;

import com.jonathan.chatoverhaul.client.WonderlandExitGuard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class WonderlandPauseScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void dltcore$disableWonderlandLeaveButton(CallbackInfo ci) {
        WonderlandExitGuard.updateLeaveButton((PauseScreen) (Object) this);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void dltcore$refreshWonderlandLeaveButton(GuiGraphics graphics, int mouseX, int mouseY,
                                                      float partialTick, CallbackInfo ci) {
        WonderlandExitGuard.updateLeaveButton((PauseScreen) (Object) this);
    }
}
