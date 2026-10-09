package com.jonathan.chatoverhaul.quest;

import com.jonathan.chatoverhaul.network.ChatOverhaulNetwork;
import com.jonathan.chatoverhaul.network.QuestSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-authoritative per-player quest state, stored in the player's own
 * persistent NBT (player.getPersistentData()) and NEVER on any item stack.
 * Because the state belongs to the player entity, every the_arg_container:
 * quest_manager stack the player owns shares a single quest, and dropping,
 * consuming, or losing every copy of the item can never reset progress.
 *
 * Durability: the state is written into the player's saved NBT like any other
 * entity data, so it survives disconnects, dimension changes and server
 * restarts. On death the whole tag is carried over to the respawned player by
 * QuestSystemHandler (PlayerEvent.Clone, wasDeath). The server is the only
 * writer; the client only ever receives read-only sync snapshots over the
 * chatoverhaul network channel for HUD display.
 */
public final class QuestData {

    public static final String DATA_KEY = "chatoverhaul_quest";

    private static final String KEY_INDEX = "quest_index";
    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_COMPLETE = "complete";
    private static final String KEY_CLAIMED = "claimed";

    /** Flat XP-point reward (not levels) for a claimed quest. */
    public static final int REWARD_XP_POINTS = 500;
    private static final int DEFAULT_QUEST_INDEX = 0;

    private QuestData() {}

    /* ---------------- reads ---------------- */

    public static boolean hasQuest(ServerPlayer player) {
        return player.getPersistentData().contains(DATA_KEY, Tag.TAG_COMPOUND);
    }

    public static boolean isComplete(ServerPlayer player) {
        return hasQuest(player) && tagOf(player).getBoolean(KEY_COMPLETE);
    }

    public static boolean isClaimed(ServerPlayer player) {
        return hasQuest(player) && tagOf(player).getBoolean(KEY_CLAIMED);
    }

    private static CompoundTag tagOf(ServerPlayer player) {
        return player.getPersistentData().getCompound(DATA_KEY);
    }

    private static void save(ServerPlayer player, CompoundTag tag) {
        player.getPersistentData().put(DATA_KEY, tag);
    }

    /* ---------------- lifecycle ---------------- */

    /**
     * Creates the default quest state (quest index 0, zero progress) the first
     * time the player obtains or uses a quest manager. A no-op once present, so
     * picking up, re-stacking, dropping or re-obtaining managers never resets
     * or re-assigns the quest.
     */
    public static void ensureStarted(ServerPlayer player) {
        if (hasQuest(player)) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_INDEX, DEFAULT_QUEST_INDEX);
        tag.putInt(KEY_PROGRESS, 0);
        tag.putBoolean(KEY_COMPLETE, false);
        tag.putBoolean(KEY_CLAIMED, false);
        save(player, tag);
        syncTo(player);
    }

    /**
     * Records gained copies of a COLLECT quest's target item. Only the active
     * quest counts, progress is capped at its target, and once the quest is
     * complete no further gains accumulate.
     */
    public static void recordCollect(ServerPlayer player, ResourceLocation itemId, int amount) {
        if (amount <= 0 || !hasQuest(player)) {
            return;
        }
        CompoundTag tag = tagOf(player);
        if (tag.getBoolean(KEY_COMPLETE)) {
            return;
        }
        QuestDefinition def = active(tag);
        if (def.kind != QuestDefinition.Kind.COLLECT || !def.targetId.equals(itemId)) {
            return;
        }
        addProgress(player, tag, def, amount);
    }

    /**
     * Records a single player-attributed death for a KILL quest. Only the exact
     * vanilla entity type of the active quest matches, so lookalike modded mobs
     * are never credited.
     */
    public static void recordKill(ServerPlayer player, EntityType<?> entityType) {
        if (!hasQuest(player)) {
            return;
        }
        CompoundTag tag = tagOf(player);
        if (tag.getBoolean(KEY_COMPLETE)) {
            return;
        }
        QuestDefinition def = active(tag);
        if (def.kind != QuestDefinition.Kind.KILL || !def.entityType.equals(entityType)) {
            return;
        }
        addProgress(player, tag, def, 1);
    }

    private static void addProgress(ServerPlayer player, CompoundTag tag, QuestDefinition def, int amount) {
        int progress = Math.max(0, tag.getInt(KEY_PROGRESS));
        int capped = Math.min(def.target, progress + amount);
        if (capped == progress) {
            return;
        }
        tag.putInt(KEY_PROGRESS, capped);
        if (capped >= def.target) {
            tag.putBoolean(KEY_COMPLETE, true);
        }
        save(player, tag);
        syncTo(player);
    }

    /* ---------------- claim ---------------- */

    /**
     * Claims a completed quest: grants the flat 500-XP reward exactly once, then
     * advances to the next quest (in the fixed QUESTS order, wrapping around)
     * with zero progress. The claimed flag is written before the reward is
     * granted, and the whole method runs inside a single server-side event
     * dispatch, so rapid right-clicks or multiple stacked quest managers cannot
     * double-reward. Returns true if a reward was granted.
     */
    public static boolean claimAndAdvance(ServerPlayer player) {
        if (!hasQuest(player)) {
            return false;
        }
        CompoundTag tag = tagOf(player);
        if (!tag.getBoolean(KEY_COMPLETE) || tag.getBoolean(KEY_CLAIMED)) {
            return false;
        }

        tag.putBoolean(KEY_CLAIMED, true);
        save(player, tag);

        player.giveExperiencePoints(REWARD_XP_POINTS);

        int next = (active(tag).index + 1) % QuestDefinition.QUESTS.length;
        tag.putInt(KEY_INDEX, next);
        tag.putInt(KEY_PROGRESS, 0);
        tag.putBoolean(KEY_COMPLETE, false);
        tag.putBoolean(KEY_CLAIMED, false);
        save(player, tag);

        syncTo(player);
        return true;
    }

    /* ---------------- sync ---------------- */

    /**
     * Sends the current quest state to the owning player's client, if a quest
     * exists. Sent to that one player only - nothing private is broadcast.
     */
    public static void syncTo(ServerPlayer player) {
        if (!hasQuest(player)) {
            return;
        }
        CompoundTag tag = tagOf(player);
        QuestDefinition def = active(tag);
        int progress = Math.max(0, Math.min(tag.getInt(KEY_PROGRESS), def.target));
        ChatOverhaulNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new QuestSyncPacket(def.index, progress,
                        tag.getBoolean(KEY_COMPLETE), tag.getBoolean(KEY_CLAIMED)));
    }

    private static QuestDefinition active(CompoundTag tag) {
        return QuestDefinition.QUESTS[QuestDefinition.clampIndex(tag.getInt(KEY_INDEX))];
    }
}