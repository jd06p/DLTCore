package com.jonathan.chatoverhaul.client;

/**
 * Client-only cache of the owning player's quest state, refreshed every time a
 * QuestSyncPacket arrives from the server. Entirely cosmetic - the client never
 * mutates or writes this back; the server stays authoritative. Cleared on world
 * disconnect (see ClientQuestLifecycle) so a different server/player cannot see
 * stale quest data.
 */
public final class ClientQuestState {

    private static volatile boolean hasData = false;
    private static volatile int questIndex;
    private static volatile int progress;
    private static volatile boolean complete;
    private static volatile boolean claimed;

    private ClientQuestState() {}

    public static void apply(int questIndex, int progress, boolean complete, boolean claimed) {
        ClientQuestState.questIndex = questIndex;
        ClientQuestState.progress = progress;
        ClientQuestState.complete = complete;
        ClientQuestState.claimed = claimed;
        ClientQuestState.hasData = true;
    }

    public static void clear() {
        hasData = false;
    }

    public static boolean hasData() {
        return hasData;
    }

    public static int questIndex() {
        return questIndex;
    }

    public static int progress() {
        return progress;
    }

    public static boolean complete() {
        return complete;
    }

    public static boolean claimed() {
        return claimed;
    }
}