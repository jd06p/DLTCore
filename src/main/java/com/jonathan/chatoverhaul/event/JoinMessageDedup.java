package com.jonathan.chatoverhaul.event;

import java.util.concurrent.ConcurrentHashMap;

final class JoinMessageDedup {
    private static final ConcurrentHashMap<String, Long> LAST = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 3000;

    private JoinMessageDedup() {}

    static boolean shouldSuppress(String key) {
        long now = System.currentTimeMillis();
        Long last = LAST.get(key);
        if (last != null && now - last < WINDOW_MS) {
            return true;
        }
        LAST.put(key, now);
        LAST.entrySet().removeIf(e -> now - e.getValue() > 60000);
        return false;
    }
}