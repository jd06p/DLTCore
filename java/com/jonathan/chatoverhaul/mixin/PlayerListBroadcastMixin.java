package com.jonathan.chatoverhaul.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rewrites The Wonderland's "no one" entity dialogue - hardcoded lines like
 * "< > oS" sent directly via PlayerList#broadcastSystemMessage (no command,
 * no event - straight Java call from that mod).
 *
 * ALSO rewrites two chat-speaker names from The Arg Container, using the
 * same approach: evil_user_0's "user0" speaker name (its damage/defeat
 * dialogue in EvilUser0EntityIsHurtProcedure/EvilUser0EntityDiesProcedure,
 * confirmed via its actual source) becomes "Entity303" everywhere it
 * appears in a broadcast line - not just the "<user0>" prefix, since one of
 * its lines mentions "user0" as plain text ("made user0 a server
 * operator") rather than as a speaker tag, and the request asked for the
 * name changed wherever it appears in these messages. Steven's "<Steven>"
 * speaker prefix (StevenOnEntityTickUpdateProcedure) becomes "<BLANK>".
 *
 * Vanilla join/leave customization was removed here - it kept colliding with
 * other mods (and datapack/mod sequences) that fake the same "X joined/left
 * the game" wording, producing duplicate lines that weren't worth chasing.
 * Vanilla's own join/leave message is left alone.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListBroadcastMixin {

    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void chatoverhaul$rewriteBroadcasts(Component message, boolean bypassHiddenChat, CallbackInfo ci) {
        PlayerList self = (PlayerList) (Object) this;
        String text = message.getString();

        // --- "no one" entity dialogue: "< > oS", "< > pleh ruoy deen I", etc. ---
        // \u273A ("✺") written as a Unicode escape, not a raw literal
        // character - escapes are plain ASCII in the source file itself
        // (backslash, u, 4 hex digits), so they're immune to the
        // host-machine source-encoding issue described above, on top of
        // the build.gradle fix.
        if (text.startsWith("< > ")) {
            String replaced = "<\u273A> " + text.substring(4);
            ci.cancel();
            self.broadcastSystemMessage(Component.literal(replaced).withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }

        // --- evil_user_0's "user0" speaker name -> "Entity303" ---
        if (text.contains("user0")) {
            String replaced = text.replace("user0", "Entity303");
            ci.cancel();
            self.broadcastSystemMessage(Component.literal(replaced).withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }

        // --- Steven's "<Steven>" speaker prefix -> "<BLANK>" ---
        if (text.startsWith("<Steven>")) {
            String replaced = "<BLANK>" + text.substring("<Steven>".length());
            ci.cancel();
            self.broadcastSystemMessage(Component.literal(replaced).withStyle(message.getStyle()), bypassHiddenChat);
        }
    }
}
