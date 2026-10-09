package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import com.jonathan.chatoverhaul.quest.QuestData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * Server-side quest system driven by the existing the_arg_container:
 * quest_manager item (its shipped class has no right-click behavior of its
 * own, so this owns the whole mechanic without touching the item).
 *
 * Events used (all Forge-bus, all server-side):
 *  - RightClickItem: initialize on first use, and claim a completed quest
 *    (flat 500-XP reward) then advance to the next quest.
 *  - ItemPickupEvent: counts gains from mining drops, mob drops and any
 *    item-entity pickup, and initializes the quest the first time a player
 *    obtains a quest manager this way. Slot-only moves fire no event and are
 *    never counted.
 *  - ItemCraftedEvent / ItemSmeltedEvent: count items gained through crafting
 *    and furnace output. Pickup, crafting and smelting are disjoint flows, so
 *    the same item is never added by more than one event.
 *  - LivingDeathEvent: player-attributed kills of the exact vanilla entity
 *    type of the active KILL quest (zombie / creeper / skeleton).
 *  - PlayerLoggedInEvent / PlayerRespawnEvent: push the current state to that
 *    player's HUD.
 *  - PlayerEvent.Clone (wasDeath): carries the quest tag onto the respawned
 *    player so progress survives death.
 *
 * Quest state, progress and rewards all live in per-player persistent NBT (see
 * QuestData); this class contains no client references, so it is safe on a
 * dedicated server.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class QuestSystemHandler {

    private static final ResourceLocation QUEST_MANAGER_ID =
            new ResourceLocation("the_arg_container", "quest_manager");

    /** The exact registries the COLLECT quests count, for fast gating. */
    private static final Set<ResourceLocation> COLLECT_ITEM_IDS = Set.of(
            new ResourceLocation("minecraft", "cobblestone"),
            new ResourceLocation("minecraft", "sand"),
            new ResourceLocation("the_arg_container", "noise_gem_tile"),
            new ResourceLocation("the_arg_container", "void_gem")
    );

    private QuestSystemHandler() {}

    /* ---------------- item interaction ---------------- */

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isQuestManager(event.getItemStack())) {
            return;
        }
        // The quest manager has no native right-click use, so the whole
        // mechanic belongs here; cancel so nothing else ever runs for it.
        event.setCanceled(true);

        QuestData.ensureStarted(player);
        if (QuestData.isComplete(player) && !QuestData.isClaimed(player)) {
            QuestData.claimAndAdvance(player);
        }
    }

    /* ---------------- collection objectives ---------------- */

    @SubscribeEvent
    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getStack();
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        if (QUEST_MANAGER_ID.equals(id)) {
            QuestData.ensureStarted(player);
            return;
        }
        if (COLLECT_ITEM_IDS.contains(id)) {
            QuestData.recordCollect(player, id, stack.getCount());
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        recordCollected(player, event.getCrafting());
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        recordCollected(player, event.getSmelting());
    }

    private static void recordCollected(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && COLLECT_ITEM_IDS.contains(id)) {
            QuestData.recordCollect(player, id, stack.getCount());
        }
    }

    /* ---------------- kill objectives ---------------- */

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        ServerPlayer killer = playerAttributingKiller(event.getSource());
        if (killer == null) {
            return;
        }
        QuestData.recordKill(killer, event.getEntity().getType());
    }

    /**
     * Resolves which player should get kill credit: melee and direct damage use
     * the source entity; indirect sources (arrows, projectiles, thrown items)
     * attribute to their owning player via the damage source's direct entity.
     */
    private static ServerPlayer playerAttributingKiller(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        if (source.getDirectEntity() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    /* ---------------- lifecycle / sync ---------------- */

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestData.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestData.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        CompoundTag source = event.getOriginal().getPersistentData();
        if (source.contains(QuestData.DATA_KEY, Tag.TAG_COMPOUND)) {
            event.getEntity().getPersistentData()
                    .put(QuestData.DATA_KEY, source.getCompound(QuestData.DATA_KEY));
        }
    }

    private static boolean isQuestManager(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return QUEST_MANAGER_ID.equals(id);
    }
}