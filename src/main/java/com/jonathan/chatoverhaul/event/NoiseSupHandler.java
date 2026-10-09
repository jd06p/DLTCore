package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Applies the "Noise Sup" on-use behavior via the right-click event, without
 * touching the ARG Container item class: when the_arg_container:noise_sup is
 * right-clicked, consume exactly one (succeeding only if we can/should), play
 * minecraft:entity.player.burp at the player's location (server broadcasts),
 * apply Speed II (amplifier 1) for 10 seconds with particles disabled, and
 * grant a 30-second cooldown to that item type.
 *
 * All authoritative changes (consume, effect, cooldown) happen server-side;
 * the sound is played server-side to match vanilla food-consumption behavior.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class NoiseSupHandler {

    private static final ResourceLocation NOISE_SUP_ID =
            new ResourceLocation("the_arg_container", "noise_sup");

    private static final int EFFECT_DURATION_TICKS = 200; // 10 seconds at 20 TPS
    private static final int COOLDOWN_SECONDS = 30;
    private static final int COOLDOWN_TICKS = COOLDOWN_SECONDS * 20;
    private static final int SPEED_AMPLIFIER = 1; // Speed II

    private NoiseSupHandler() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null || !NOISE_SUP_ID.equals(id)) {
            return;
        }

        if (player.getCooldowns().isOnCooldown(item)) {
            return;
        }

        InteractionHand hand = event.getHand();
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || !NOISE_SUP_ID.equals(ForgeRegistries.ITEMS.getKey(held.getItem()))) {
            return;
        }

        // Consume exactly one. Using the API that respects the exact held stack.
        if (held.getCount() <= 0) {
            return;
        }

        held.shrink(1);
        if (held.isEmpty()) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        } else {
            player.setItemInHand(hand, held);
        }

        // Effects
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, EFFECT_DURATION_TICKS, SPEED_AMPLIFIER,
                false, false, true));

        // Sound: minecraft:entity.player.burp at player's location
        player.level().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_BURP,
                SoundSource.PLAYERS,
                1.0F, 1.0F
        );

        // Cooldown for 30 seconds on this item type
        player.getCooldowns().addCooldown(item, COOLDOWN_TICKS);

        event.setCanceled(true);
    }
}