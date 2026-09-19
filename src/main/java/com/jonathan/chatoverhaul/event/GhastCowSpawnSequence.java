package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays a scripted, ominous chat sequence whenever a `ghastcow:ghast_cow`
 * (The Ghast Cow mod's boss) is ACTUALLY created by the mod's own mechanic.
 *
 * HOW THE GHAST COW IS CREATED (investigated from the mod's jar):
 * The Ghast Cow is NOT spawned through a spawn egg or any vanilla natural
 * spawning. The mod's CommonClass.onDeath() runs when a cow dies: if the
 * damage source is a Ghast's LargeFireball, the cow is a Cow, and (depending
 * on config) the cow is named "ghast", it builds the GhastCow with
 * `EntityType.create()` and then calls `Level.addFreshEntity(entity)` to drop
 * it into the world exactly at the cow's death position. That's the whole
 * mechanic.
 *
 * WHY EntityJoinLevelEvent (and NOT MobSpawnEvent.FinalizeSpawn):
 * The previous implementation listened to MobSpawnEvent.FinalizeSpawn and
 * never fired. A sweep of the actual Forge 1.20.1 (47.2.0) jar this project
 * compiles against shows that event is only constructed by BaseSpawner and
 * ForgeEventFactory - i.e. it is tied to the vanilla/NaturalSpawner/spawner
 * block paths. `ServerLevel.addFreshEntity` does NOT route through it, so a
 * mod-created entity dropped in via addFreshEntity (which is exactly what
 * the Ghast Cow mechanic does) never produces a FinalizeSpawn event.
 *
 * Instead, Forge's EntityJoinLevelEvent IS fired by that same path: Forge
 * patches PersistentEntitySectionManager.addEntity(entity, false) to post it
 * for every new entity entering the level, and ServerLevel.addFreshEntity
 * funnels straight into that. It fires once per entity, on the server
 * thread, close to the instant the Ghast Cow enters the world - so the
 * sequence starts only when the cow has actually been created.
 *
 * The loadedFromDisk() flag is the key for "actually created": a Ghast Cow
 * loaded back from a saved chunk re-enters the level with
 * loadedFromDisk()==true, while a fresh mod-mechanic spawn has
 * loadedFromDisk()==false. Checking it means chunk reloads never replay the
 * sequence, and the cow's real creation always does.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class GhastCowSpawnSequence {

    private static final ResourceLocation GHAST_COW_ID = new ResourceLocation("ghastcow", "ghast_cow");

    /** How long after the spawn the first line waits (the spawn needs a beat to fully register). */
    private static final long SPAWN_REGISTER_TICKS = 20;

    /** Gap after <author>'s line (short pause). */
    private static final long PAUSE_AFTER_AUTHOR_TICKS = 20;

    /** Gap after <\u292B>'s line (short pause). */
    private static final long PAUSE_AFTER_X_TICKS = 15;

    /** Gap before the fake leave messages (brief pause). */
    private static final long PAUSE_BEFORE_LEAVE_TICKS = 20;

    /** Gap between each fake leave message (they appear in sequence). */
    private static final long LEAVE_GAP_TICKS = 10;

    private static final char X_SYMBOL = '\u292B';
    private static final char M_SYMBOL = '\uA55A';

    /** UUIDs that already got their sequence - a same-UUID cow can never trigger twice. */
    private static final Set<UUID> TRIGGERED = ConcurrentHashMap.newKeySet();

    private GhastCowSpawnSequence() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity == null) {
            return;
        }

        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (typeId == null || !typeId.equals(GHAST_COW_ID)) {
            return;
        }

        if (!TRIGGERED.add(entity.getUUID())) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        scheduleSequence(server);
    }

    private static void scheduleSequence(MinecraftServer server) {
        long t = SPAWN_REGISTER_TICKS;
        schedule(server, t, Component.literal("<author> What have you done!?").withStyle(ChatFormatting.WHITE));
        t += PAUSE_AFTER_AUTHOR_TICKS;

        schedule(server, t, Component.literal("<" + X_SYMBOL + "> Yeah... We ain't helping you with this one...").withStyle(ChatFormatting.WHITE));
        t += PAUSE_AFTER_X_TICKS;

        schedule(server, t, Component.literal("<" + M_SYMBOL + "> Good luck...").withStyle(ChatFormatting.WHITE));
        t += PAUSE_BEFORE_LEAVE_TICKS;

        schedule(server, t, leaveMessage("author"));
        t += LEAVE_GAP_TICKS;

        schedule(server, t, leaveMessage(String.valueOf(X_SYMBOL)));
        t += LEAVE_GAP_TICKS;

        schedule(server, t, leaveMessage(String.valueOf(M_SYMBOL)));
    }

    private static Component leaveMessage(String playerName) {
        return Component.literal(playerName + " left the game").withStyle(ChatFormatting.YELLOW);
    }

    private static void schedule(MinecraftServer server, long delayTicks, Component message) {
        TickScheduler.scheduleInTicks(delayTicks,
                () -> server.getPlayerList().broadcastSystemMessage(message, false));
    }
}