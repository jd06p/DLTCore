package com.jonathan.chatoverhaul.mixin;

import com.jonathan.chatoverhaul.client.WonderlandChatRestriction;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class WonderlandChatScreenMixin {
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void dltcore$blockWonderlandPlayerChat(String message, boolean addToHistory,
                                                   CallbackInfoReturnable<Boolean> cir) {
        String submitted = message.trim();
        if (submitted.isEmpty() || submitted.startsWith("/")) return;
        if (!WonderlandChatRestriction.blocksPlayerChat()) return;

        WonderlandChatRestriction.showBlockedFeedback();
        // Match normal submission behavior and close the chat screen, without
        // adding the blocked message to history or sending it to the server.
        cir.setReturnValue(true);
    }
}
