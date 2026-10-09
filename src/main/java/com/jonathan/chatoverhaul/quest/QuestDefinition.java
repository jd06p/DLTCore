package com.jonathan.chatoverhaul.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * The seven quests the the_arg_container:quest_manager drives, in their fixed,
 * documented order (index 0 through 6). A player's single active quest is
 * selected purely by index into {@link #QUESTS} and always advances to
 * {@code QUESTS[(index + 1) % QUESTS.length]} when a completed quest is
 * claimed, so quest selection is deterministic and never depends on which copy
 * of the item (or how many copies) the player owns.
 *
 * COLLECT quests count item acquisition by the owning player; KILL quests count
 * player-attributed deaths of the exact vanilla entity type listed (zombie /
 * creeper / skeleton - not modded mobs that merely resemble them).
 *
 * This is a common-side class with no client-only references, so both the
 * server-side quest logic and the client-side HUD overlay can read it.
 */
public final class QuestDefinition {

    public enum Kind { COLLECT, KILL }

    public static final QuestDefinition[] QUESTS = {
            collect(0, "Collect 600 Cobblestone", "minecraft", "cobblestone", 600),
            collect(1, "Collect 600 Sand", "minecraft", "sand", 600),
            collect(2, "Collect 600 Noise Gem Tiles", "the_arg_container", "noise_gem_tile", 600),
            collect(3, "Collect 64 Noise Gems", "the_arg_container", "void_gem", 64),
            kill(4, "Kill 100 Zombies", EntityType.ZOMBIE, 100),
            kill(5, "Kill 100 Creepers", EntityType.CREEPER, 100),
            kill(6, "Kill 100 Skeletons", EntityType.SKELETON, 100)
    };

    public final int index;
    public final String label;
    public final Kind kind;
    /** COLLECT: the registered item/block-item id. KILL: null. */
    public final ResourceLocation targetId;
    /** KILL: the exact vanilla entity type matched for credit. COLLECT: null. */
    public final EntityType<?> entityType;
    public final int target;

    private QuestDefinition(int index, String label, Kind kind,
                            ResourceLocation targetId, EntityType<?> entityType, int target) {
        this.index = index;
        this.label = label;
        this.kind = kind;
        this.targetId = targetId;
        this.entityType = entityType;
        this.target = target;
    }

    private static QuestDefinition collect(int index, String label,
                                           String namespace, String itemId, int target) {
        return new QuestDefinition(index, label, Kind.COLLECT,
                new ResourceLocation(namespace, itemId), null, target);
    }

    private static QuestDefinition kill(int index, String label,
                                        EntityType<?> entityType, int target) {
        return new QuestDefinition(index, label, Kind.KILL, null, entityType, target);
    }

    /** Clamps an arbitrary index into the valid quest range (corrupt-data safety). */
    public static int clampIndex(int index) {
        return Math.max(0, Math.min(index, QUESTS.length - 1));
    }
}