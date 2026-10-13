package com.jonathan.chatoverhaul.event;

import net.minecraft.server.MinecraftServer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class JoinMessageDedup {
    private static final ConcurrentHashMap<String, Long> LAST = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 3000;

    private static int lastTick = Integer.MIN_VALUE;
    private static final Set<String> TICK_SEEN = ConcurrentHashMap.newKeySet();

    private JoinMessageDedup() {}

    public static boolean shouldSuppress(String key) {
        long now = System.currentTimeMillis();
        Long last = LAST.get(key);
        if (last != null && now - last < WINDOW_MS) {
            return true;
        }
        LAST.put(key, now);
        LAST.entrySet().removeIf(e -> now - e.getValue() > 60000);
        return false;
    }

    /**
     * Returns true when an identical message was already emitted during the
     * current server tick. Several mods (e.g. The Broken Script's random
     * events) run a per-player tick handler that can independently produce
     * the same line for multiple players in one simulation step; this folds
     * that fan-out into a single global broadcast without any timed cooldown
     * and without suppressing genuinely separate (later-tick) events.
     */
    public static boolean isDuplicateThisTick(MinecraftServer server, String key) {
        int tick = server.getTickCount();
        if (tick != lastTick) {
            lastTick = tick;
            TICK_SEEN.clear();
        }
        return !TICK_SEEN.add(key);
    }
}
