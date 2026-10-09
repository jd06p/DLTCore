package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dismantler toggle ability for the_arg_container:dismantler.
 *
 * <p>Chat code {@code A7d5} toggles the Dismantler held in the player's main
 * hand between two server-side modes:
 * <ul>
 *   <li>Inactive (default): the Dismantler's passive fall-damage negation
 *       works while it is held in the main hand.</li>
 *   <li>Activated: while held in the main hand the player gains infinite
 *       Haste III with no particles, and the fall-damage negation is off.</li>
 * </ul>
 * The mode only ever applies while the Dismantler is actually held: switching
 * away removes the effect (and disables fall negation in the activated mode);
 * switching back while activated re-applies Haste. The mode resets to Inactive
 * whenever the player disconnects (including kicks/crashes: on login any stale
 * Haste left by an ungraceful disconnect is removed).
 *
 * <p>Everything is triggered and enforced server-side via Forge events, and
 * the ARG item itself is never touched. The Dismantler is matched by its exact
 * runtime item class name so this mod has no compile-time or hard dependency
 * on the ARG Container. The toggle code is consumed silently - it is cancelled
 * before broadcasting, and the confirmation + sound go only to the player who
 * typed it.
 *
 * <p>Haste is granted with an infinite duration and our own signature
 * (Haste III, amplifier 2, no particles, visible icon). Removal only ever
 * touches a haste effect matching that exact signature, so unrelated haste
 * from other sources is never removed. Fall damage is negated by cancelling
 * {@link LivingFallEvent}, so it also covers the void-height fall cap and
 * respects every other fall-damage rule.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class DismantlerToggleHandler {

    private static final String DISMANTLER_ITEM_CLASS =
            "net.mcreator.minecraftalphaargmod.item.DismantlerItem";
    private static final String TOGGLE_CODE = "A7d5";
    private static final String MESSAGE_ACTIVATED = "Dismantler Activated!";
    private static final String MESSAGE_DEACTIVATED = "Dismantler Deactivated!";
    private static final int HASTE_AMPLIFIER = 2;

    private static final Map<UUID, Boolean> ACTIVATED = new ConcurrentHashMap<>();

    private DismantlerToggleHandler() {}

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        if (!TOGGLE_CODE.equals(event.getRawText())) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        if (!isDismantlerHeldInMainHand(player)) {
            return;
        }
        boolean nowActivated = !isDismantlerActivated(player);
        ACTIVATED.put(player.getUUID(), nowActivated);
        event.setCanceled(true);

        if (nowActivated) {
            applyHaste(player);
            player.sendSystemMessage(Component.literal(MESSAGE_ACTIVATED));
        } else {
            removeOurHaste(player);
            player.sendSystemMessage(Component.literal(MESSAGE_DEACTIVATED));
        }
        player.playNotifySound(
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!isDismantlerActivated(player)) {
            return;
        }
        if (isDismantlerHeldInMainHand(player)) {
            applyHaste(player);
        } else {
            removeOurHaste(player);
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isDismantlerHeldInMainHand(player) || isDismantlerActivated(player)) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !isDismantlerActivated(player)) {
            removeOurHaste(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ACTIVATED.remove(player.getUUID());
            removeOurHaste(player);
        }
    }

    public static boolean isDismantlerActivated(ServerPlayer player) {
        return ACTIVATED.getOrDefault(player.getUUID(), Boolean.FALSE);
    }

    public static boolean isDismantlerHeldInMainHand(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return false;
        }
        return DISMANTLER_ITEM_CLASS.equals(held.getItem().getClass().getName());
    }

    private static void applyHaste(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.DIG_SPEED);
        if (current == null || !current.isInfiniteDuration()
                || current.getAmplifier() != HASTE_AMPLIFIER) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SPEED, MobEffectInstance.INFINITE_DURATION,
                    HASTE_AMPLIFIER, false, false, true));
        }
    }

    private static void removeOurHaste(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.DIG_SPEED);
        if (current != null && current.isInfiniteDuration()
                && current.getAmplifier() == HASTE_AMPLIFIER) {
            player.removeEffect(MobEffects.DIG_SPEED);
        }
    }
}