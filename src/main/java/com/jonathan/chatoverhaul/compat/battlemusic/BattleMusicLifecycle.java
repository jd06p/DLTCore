package com.jonathan.chatoverhaul.compat.battlemusic;

import com.nekotune.battlemusic.BattleMusic;
import com.nekotune.battlemusic.BattleMusicInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Repairs the native singleton/queue lifecycle; Battle Music still selects and plays every track. */
public final class BattleMusicLifecycle {
    private static final class State {
        final long queuedAt;
        long fadeDeadline;
        State(long queuedAt) { this.queuedAt = queuedAt; }
    }
    private static final Map<BattleMusicInstance, State> INSTANCES = new IdentityHashMap<>();
    private static ClientLevel lastLevel;
    private static long ticks;

    public static boolean invalidTarget(Mob mob) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.player == null || mc.player.isDeadOrDying()
                || mob == null || mob.isRemoved() || !mob.isAlive() || mob.isDeadOrDying()
                || mob.level() != mc.level;
    }

    public static void created(BattleMusicInstance sound) { INSTANCES.put(sound, new State(ticks)); }

    public static void fading(BattleMusicInstance sound, float seconds) {
        State state = INSTANCES.get(sound);
        if (state != null && seconds > 0)
            state.fadeDeadline = ticks + (long) Math.ceil(seconds * 20.0) + 2;
    }

    public static void destroyed(BattleMusicInstance sound) {
        INSTANCES.remove(sound);
        // The original destroy already stops the tickable instance/channel and clears playing by identity.
        sound.entity = null;
        sound.fadeOut = false;
        sound.fadeLength = 0;
    }

    public static boolean stopIfInvalid(BattleMusicInstance sound) {
        if (sound != BattleMusic.playing || invalidTarget(sound.entity)) {
            sound.destroy();
            return true;
        }
        return false;
    }

    /** Called before the native priority comparison, so a dead boss cannot block its successor. */
    public static void beforeSelection() {
        BattleMusic.validEntities.removeIf(mob -> invalidTarget(mob));
        BattleMusicInstance current = BattleMusic.playing;
        if (current != null && (invalidTarget(current.entity) || current.isStopped())) current.destroy();
    }

    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) { mc.execute(BattleMusicLifecycle::reset); return; }
        BattleMusicInstance current = BattleMusic.playing;
        if (current != null) current.destroy();
        for (BattleMusicInstance sound : List.copyOf(INSTANCES.keySet())) sound.destroy();
        INSTANCES.clear();
        BattleMusic.playing = null;
        BattleMusic.validEntities.clear();
        lastLevel = null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ModList.get().isLoaded("battlemusic")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || (lastLevel != null && lastLevel != mc.level)) reset();
        lastLevel = mc.level;
        beforeSelection();
        if (mc.isPaused()) return; // SoundEngine also pauses its tickable sounds in a paused local game.
        ticks++;
        BattleMusicInstance current = BattleMusic.playing;
        for (BattleMusicInstance sound : List.copyOf(INSTANCES.keySet())) {
            State state = INSTANCES.get(sound);
            if (!sound.fadeOut) state.fadeDeadline = 0;
            // queueTickingSound is processed later by SoundEngine; allow it to start before testing isActive.
            // A zero volume makes the native fade subtract zero forever. Bound that fade by its duration.
            if (sound != current || (ticks - state.queuedAt > 2 && !mc.getSoundManager().isActive(sound))
                    || (sound.fadeOut && state.fadeDeadline > 0 && ticks >= state.fadeDeadline))
                sound.destroy();
        }
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide() && ModList.get().isLoaded("battlemusic")) reset();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (ModList.get().isLoaded("battlemusic")) reset();
    }
    @SubscribeEvent public static void clonePlayer(ClientPlayerNetworkEvent.Clone event) {
        if (ModList.get().isLoaded("battlemusic")) reset();
    }
}
