package com.jonathan.chatoverhaul.client;

import com.jonathan.chatoverhaul.network.CircuitChasePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;

/** A snapshot belongs to this connection/player instance and dimension, not a static world flag. */
public final class CircuitChaseState {
    private static LocalPlayer owner;
    private static CircuitChasePacket state;

    private CircuitChaseState() {}

    public static void accept(CircuitChasePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.level().dimension().location().equals(packet.dimension())) return;
        if (active() && !packet.active()) {
            for (String sound : new String[]{"nullchase", "circuit_jumpscare", "nulljumpscareloud"}) {
                mc.getSoundManager().stop(new ResourceLocation("thebrokenscript", sound), SoundSource.NEUTRAL);
            }
        }
        owner = mc.player;
        state = packet;
    }

    public static boolean active() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player == owner && state != null && state.active()
                && player.level().dimension().location().equals(state.dimension());
    }

    public static double frame() {
        return active() ? state.frame() : 0;
    }

    public static boolean flash() {
        return active() && state.flash();
    }
}
