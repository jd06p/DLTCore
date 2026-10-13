package com.jonathan.chatoverhaul.mixin;

import com.jonathan.chatoverhaul.util.LocalPlayNames;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class PlayerDisplayMessageMixin {

    private static final String CONNECT = " has connected to Local Play!";
    private static final String DISCONNECT = " has disconnected from Local Play!";

    /**
     * Returns the replacement text for a join/leave system message, or null
     * when the message must be shown as-is.
     *
     * Only two things are rewritten: mod-specific identities (The Broken
     * Script's "null" -> U+273A, The ARG Container's "The_Creator" ->
     * "author") and the vanilla "X joined the game" / "X left the game"
     * wording. The identity match is exact on the extracted name, so normal
     * player names and unrelated messages containing "null" are untouched.
     */
    private String chatoverhaul$remap(String text) {
        if (text.endsWith(CONNECT)) {
            String raw = text.substring(0, text.length() - CONNECT.length());
            String mapped = LocalPlayNames.normalize(raw);
            return mapped.equals(raw) ? null : mapped + CONNECT;
        }
        if (text.endsWith(DISCONNECT)) {
            String raw = text.substring(0, text.length() - DISCONNECT.length());
            String mapped = LocalPlayNames.normalize(raw);
            return mapped.equals(raw) ? null : mapped + DISCONNECT;
        }

        String lower = text.toLowerCase();
        if (lower.contains(" joined the game")) {
            int idx = lower.indexOf(" joined the game");
            String name = LocalPlayNames.normalize(text.substring(0, idx).trim());
            if (name.isEmpty() || name.toLowerCase().contains("joined the game")) {
                return null;
            }
            return name + CONNECT;
        }
        if (lower.contains(" left the game")) {
            int idx = lower.indexOf(" left the game");
            String name = LocalPlayNames.normalize(text.substring(0, idx).trim());
            if (name.isEmpty() || name.toLowerCase().contains("left the game")) {
                return null;
            }
            return name + DISCONNECT;
        }
        if (lower.contains("the_creator")) {
            return text.replace("The_Creator", "author");
        }
        // The Entity dialogue replacements - exact literal replacements as specified
        // Normal dialogue
        if (text.equals("friends are waiting for us.")) return "ZnJpZW5kcyBhcmUgd2FpdGluZyBmb3IgdXMu";
        if (text.equals("come here.")) return "Y29tZSBoZXJlLg==";
        if (text.equals("look at me.")) return "bG9vayBhdCBtZS4=";
        if (text.equals("i know who you are.")) return "aSBrbm93IHdobyB5b3UgYXJlLg==";
        if (text.equals("hello.")) return "aGVsbG8u";
        if (text.equals("don't be afraid")) return "ZG9uJ3QgYmUgYWZyYWlk";
        if (text.equals("don't run")) return "ZG9uJ3QgcnVu";
        if (text.equals("there is nothing to be afraid of")) return "dGhlcmUgaXMgbm90aGluZyB0byBiZSBhZnJhaWQgb2Y=";
        if (text.equals("can you see me?")) return "Y2FuIHlvdSBzZWUgbWU/";
        if (text.equals("coming.")) return "Y29taW5nLgo=";
        if (text.equals("i see you")) return "aSBzZWUgeW91";
        if (text.equals("follow me")) return "Zm9sbG93IG1l";
        if (text.equals("where are you, friend?")) return "d2hlcmUgYXJlIHlvdSwgZnJpZW5kPw==";
        if (text.equals("it is time to go, my friend.")) return "aXQgaXMgdGltZSB0byBnbywgbXkgZnJpZW5kLg==";
        if (text.equals("over here, my friend.")) return "b3ZlciBoZXJlLCBteSBmcmllbmQu";
        if (text.equals("you can trust me, do not worry.")) return "eW91IGNhbiB0cnVzdCBtZSwgZG8gbm90IHdvcnJ5Lg==";
        if (text.equals("keep playing.")) return "a2VlcCBwbGF5aW5nLg==";
        // Seeking sequence
        if (text.equals("let's play a game")) return "bGV0J3MgcGxheSBhIGdhbWU=";
        if (text.equals("you have 10 seconds to run away or hide")) return "eW91IGhhdmUgMTAgc2Vjb25kcyB0byBydW4gYXdheSBvciBoaWRl";
        if (text.equals("10")) return "10";
        if (text.equals("9")) return "9";
        if (text.equals("8")) return "8";
        if (text.equals("7")) return "7";
        if (text.equals("6")) return "6";
        if (text.equals("5")) return "5";
        if (text.equals("4")) return "4";
        if (text.equals("3")) return "3";
        if (text.equals("2")) return "2";
        if (text.equals("1")) return "1";
        if (text.equals("ready or not, here i come!")) return "cmVhZHkgb3Igbm90LCBoZXJlIGkgY29tZSE=";
        // Responses
        if (text.equals("hello")) return "aGVsbG8=";
        if (text.equals("i want you to follow me.")) return "aSB3YW50IHlvdSB0byBmb2xsb3cgbWUu";
        if (text.equals("look at me.")) return "bG9vayBhdCBtZS4=";
        if (text.equals("somewhere")) return "c29tZXdoZXJl";
        if (text.equals("friend")) return "ZnJpZW5k";
        if (text.equals("yes. of course.")) return "eWVzLiBvZiBjb3Vyc2Uu";
        if (text.equals("i just want to help.")) return "aSBqdXN0IHdhbnQgdG8gaGVscC4=";
        if (text.equals("it is okay, come here.")) return "aXQgaXMgb2theSwgY29tZSBoZXJlLg==";
        if (text.equals("do not treat a friend like that.")) return "ZG8gbm90IHRyZWF0IGEgZnJpZW5kIGxpa2UgdGhhdC4=";
        if (text.equals("our friend. don't listen to him.")) return "b3VyIGZyaWVuZC4gZG9uJ3QgbGlzdGVuIHRvIGhpbS4=";
        if (text.equals("our good friend.")) return "b3VyIGdvb2QgZnJpZW5kLg==";
        if (text.equals("our friend.")) return "b3VyIGZyaWVuZC4=";
        if (text.equals("it's me.")) return "aXQncyBtZS4=";
        if (text.equals("HOME")) return "HOME";
        return null;
    }

    @Inject(method = "displayClientMessage", at = @At("HEAD"), cancellable = true)
    private void chatoverhaul$onDisplayClientMessage(Component message, boolean actionBar, CallbackInfo ci) {
        String text = message.getString();
        String replacement = chatoverhaul$remap(text);
        if (replacement == null || replacement.equals(text)) {
            return;
        }
        ci.cancel();
        ServerPlayer self = (ServerPlayer) (Object) this;
        self.displayClientMessage(Component.literal(replacement).withStyle(ChatFormatting.WHITE), actionBar);
    }

    @Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void chatoverhaul$onSendSystemMessage(Component message, CallbackInfo ci) {
        String text = message.getString();
        String replacement = chatoverhaul$remap(text);
        if (replacement == null || replacement.equals(text)) {
            return;
        }
        ci.cancel();
        ServerPlayer self = (ServerPlayer) (Object) this;
        self.sendSystemMessage(Component.literal(replacement).withStyle(ChatFormatting.WHITE));
    }
}
