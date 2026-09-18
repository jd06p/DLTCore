package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a player's ENTIRE inventory - main, hotbar, armor, and offhand - on
 * death while inside any dimension from The Wonderland, The Broken Script,
 * or The Arg Container, without ever touching the `keepInventory` gamerule.
 *
 * PREVIOUS VERSION'S BUG: it cancelled PlayerDropsEvent and restored items
 * from event.getDrops(). That correctly covered the main inventory/hotbar,
 * but equipped armor still hit the ground - vanilla doesn't necessarily
 * route every equipment slot category through that same single event the
 * same way, so cancelling one drop event isn't guaranteed to cover all of
 * them.
 *
 * THE FIX: rather than trying to identify and cancel every individual drop
 * event vanilla might use for different slot categories, this snapshots
 * the player's ENTIRE inventory (Inventory#save - the exact same
 * serialization vanilla itself uses for writing player data to disk, so
 * main/armor/offhand are always placed back in the correct slots on
 * restore) and CLEARS it on LivingDeathEvent - which fires before ANY
 * drop-related code runs at all. With the inventory already empty by the
 * time death processing continues, there is nothing left for any drop
 * mechanism, for any slot category, to find - so what specifically drops
 * armor vs. regular items no longer matters.
 *
 * The snapshot is restored during PlayerEvent.Clone (fired once the new,
 * respawned player object exists) via Inventory#load - matching #save,
 * this correctly restores armor and offhand to their actual equipment
 * slots, not just into the general inventory. Restoration is keyed off
 * whether a snapshot actually exists for that UUID (not off
 * event.isWasDeath()), so it also cleans up correctly even in the unusual
 * case of a player disconnecting while dead and reconnecting later rather
 * than respawning normally.
 *
 * LOWEST priority: gives any other mod a chance to cancel LivingDeathEvent
 * first (e.g. a "totem of undying"-style save) before this clears anything
 * - if death ends up cancelled, our listener never runs at all.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class DimensionKeepInventoryHandler {

    private static final Map<UUID, ListTag> PENDING_RESTORE = new ConcurrentHashMap<>();

    private DimensionKeepInventoryHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide() || !isProtected(player)) {
            return;
        }

        ListTag saved = player.getInventory().save(new ListTag());
        PENDING_RESTORE.put(player.getUUID(), saved);
        player.getInventory().clearContent();
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        ListTag saved = PENDING_RESTORE.remove(event.getOriginal().getUUID());
        if (saved != null) {
            event.getEntity().getInventory().load(saved);
        }
    }

    private static boolean isProtected(Player player) {
        return DimensionNamespaces.isNamespace(player.level().dimension(),
                DimensionNamespaces.THE_WONDERLAND,
                DimensionNamespaces.THE_ARG_CONTAINER,
                DimensionNamespaces.THEBROKENSCRIPT);
    }
}
