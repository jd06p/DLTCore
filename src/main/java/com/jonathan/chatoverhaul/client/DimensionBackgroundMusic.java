package com.jonathan.chatoverhaul.client;

import com.jonathan.chatoverhaul.registry.DLTCoreSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/** Owns the single looping DLTCore background track for the configured dimensions. */
public final class DimensionBackgroundMusic {
    private static ClientLevel previousLevel;
    private static ResourceLocation previousDimension;
    private static CustomMusicInstance activeTrack;

    private DimensionBackgroundMusic() {}

    /** Called from MusicManager.tick, so lifecycle changes use the same client update cadence. */
    public static void update(Level level, SoundInstance managerTrack, SoundManager soundManager) {
        ClientLevel clientLevel = level instanceof ClientLevel client ? client : null;
        if (clientLevel == null) {
            stopCustomTrack(soundManager);
            previousLevel = null;
            previousDimension = null;
            return;
        }

        ResourceLocation dimension = clientLevel.dimension().location();
        boolean changed = clientLevel != previousLevel || !dimension.equals(previousDimension);
        if (changed) {
            stopCustomTrack(soundManager);
            previousLevel = clientLevel;
            previousDimension = dimension;

            // A vanilla track from the previous dimension may still be active on this frame.
            if (WonderlandVanillaMusicPolicy.shouldBlockVanillaBackgroundMusic(dimension)
                    && isVanillaBackgroundTrack(managerTrack)) {
                soundManager.stop(managerTrack);
            }
        }

        if (WonderlandVanillaMusicPolicy.CUSTOM_MUSIC_DIMENSIONS.contains(dimension)) {
            if (activeTrack == null || !soundManager.isActive(activeTrack)) {
                activeTrack = new CustomMusicInstance();
                soundManager.play(activeTrack);
            }
        }
    }

    private static void stopCustomTrack(SoundManager soundManager) {
        if (activeTrack != null) {
            soundManager.stop(activeTrack);
            activeTrack = null;
        }
    }

    private static boolean isVanillaBackgroundTrack(SoundInstance sound) {
        if (sound == null) return false;
        ResourceLocation id = sound.getLocation();
        return "minecraft".equals(id.getNamespace()) && id.getPath().startsWith("music.");
    }

    private static final class CustomMusicInstance extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private CustomMusicInstance() {
            super(DLTCoreSounds.THE_VOID_MUSIC.get(), SoundSource.MUSIC,
                    net.minecraft.util.RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0F;
            this.relative = true;
        }

        @Override
        public void tick() {
            // SoundManager handles looping the streamed OGG; no per-tick work is needed here.
        }
    }
}
