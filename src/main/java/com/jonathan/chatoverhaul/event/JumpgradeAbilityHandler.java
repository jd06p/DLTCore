package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Jumpgrade ability: right-clicking the_arg_container:jumpgrade trades 5
 * experience levels for 120s of Jump Boost III (amplifier 2), with no
 * particles, a 120s item cooldown, and the vanilla level-up sound.
 *
 * Implemented entirely in DLTCore via the Forge item-interaction event, so
 * the ARG Container item itself is not touched. Server-side only (guarded by
 * the ServerPlayer check); the vanilla server already refuses to deliver this
 * event while the item is on cooldown, and the explicit cooldown check keeps
 * that guarantee if that ordering ever changes.
 *
 * If the player has fewer than 5 levels nothing happens at all - no XP spent,
 * no effect, no sound, and no cooldown.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class JumpgradeAbilityHandler {

    private static final ResourceLocation JUMPGRADE_ID =
            new ResourceLocation("the_arg_container", "jumpgrade");
    private static final int XP_COST_LEVELS = 5;
    private static final int EFFECT_DURATION_SECONDS = 120;
    private static final int COOLDOWN_SECONDS = 120;
    private static final int TICKS_PER_SECOND = 20;
    private static final int JUMP_BOOST_AMPLIFIER = 2;

    private JumpgradeAbilityHandler() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null || !JUMPGRADE_ID.equals(itemId)) {
            return;
        }

        Item item = stack.getItem();
        if (player.getCooldowns().isOnCooldown(item)) {
            return;
        }
        if (player.experienceLevel < XP_COST_LEVELS) {
            return;
        }

        player.giveExperienceLevels(-XP_COST_LEVELS);

        int durationTicks = EFFECT_DURATION_SECONDS * TICKS_PER_SECOND;
        player.addEffect(new MobEffectInstance(
                MobEffects.JUMP, durationTicks, JUMP_BOOST_AMPLIFIER,
                false, false, true));

        player.getCooldowns().addCooldown(item, COOLDOWN_SECONDS * TICKS_PER_SECOND);

        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
