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
            return;
        }

        // --- The Entity dialogue replacements (Wonderland) ---
        // Normal dialogue
        if (text.equals("friends are waiting for us.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("ZnJpZW5kcyBhcmUgd2FpdGluZyBmb3IgdXMu").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("come here.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("Y29tZSBoZXJlLg==").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("look at me.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("bG9vayBhdCBtZS4=").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("i know who you are.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("aSBrbm93IHdobyB5b3UgYXJlLg==").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("hello.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("aGVsbG8u").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("don't be afraid")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("ZG9uJ3QgYmUgYWZyYWlk").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("don't run")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("ZG9uJ3QgcnVu").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("there is nothing to be afraid of")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("dGhlcmUgaXMgbm90aGluZyB0byBiZSBhZnJhaWQgb2Y=").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("can you see me?")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("Y2FuIHlvdSBzZWUgbWU/").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("coming.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("Y29taW5nLgo=").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("i see you")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("aSBzZWUgeW91").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("follow me")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("Zm9sbG93IG1l").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("where are you, friend?")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("d2hlcmUgYXJlIHlvdSwgZnJpZW5kPw==").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("it is time to go, my friend.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("aXQgaXMgdGltZSB0byBnbywgbXkgZnJpZW5kLg==").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("over here, my friend.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("b3ZlciBoZXJlLCBteSBmcmllbmQu").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("you can trust me, do not worry.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("eW91IGNhbiB0cnVzdCBtZSwgZG8gbm90IHdvcnJ5Lg==").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("keep playing.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("a2VlcCBwbGF5aW5nLg==").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        // Seeking
        if (text.equals("let's play a game")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("bGV0J3MgcGxheSBhIGdhbWU=").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("you have 10 seconds to run away or hide")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("eW91IGhhdmUgMTAgc2Vjb25kcyB0byBydW4gYXdheSBvciBoaWRl").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("10")) { return; }
        if (text.equals("9")) { return; }
        if (text.equals("8")) { return; }
        if (text.equals("7")) { return; }
        if (text.equals("6")) { return; }
        if (text.equals("5")) { return; }
        if (text.equals("4")) { return; }
        if (text.equals("3")) { return; }
        if (text.equals("2")) { return; }
        if (text.equals("1")) { return; }
        if (text.equals("ready or not, here i come!")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("cmVhZHkgb3Igbm90LCBoZXJlIGkgY29tZSE=").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        // Responses
        if (text.equals("hello")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("aGVsbG8=").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("i want you to follow me.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("aSB3YW50IHlvdSB0byBmb2xsb3cgbWUu").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("look at me.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("bG9vayBhdCBtZS4=").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("somewhere")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("c29tZXdoZXJl").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("friend")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("ZnJpZW5k").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("yes. of course.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("eWVzLiBvZiBjb3Vyc2Uu").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("i just want to help.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("aSBqdXN0IHdhbnQgdG8gaGVscC4=").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("it is okay, come here.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("aXQgaXMgb2theSwgY29tZSBoZXJlLg==").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("do not treat a friend like that.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("ZG8gbm90IHRyZWF0IGEgZnJpZW5kIGxpa2UgdGhhdC4=").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("our friend. don't listen to him.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("b3VyIGZyaWVuZC4gZG9uJ3QgbGlzdGVuIHRvIGhpbS4=").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("our good friend.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("b3VyIGdvb2QgZnJpZW5kLg==").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("our friend.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("b3VyIGZyaWVuZC4=").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("it's me.")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("aXQncyBtZS4=").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
        if (text.equals("HOME")) {
            ci.cancel();
            self.broadcastSystemMessage(Component.literal("HOME").withStyle(message.getStyle()), bypassHiddenChat);
            return;
        }
    }
}
