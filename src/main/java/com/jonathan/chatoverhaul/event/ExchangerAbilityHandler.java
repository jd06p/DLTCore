package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Exchanger ability for the_arg_container:exchanger.
 *
 * <p>Inspection of The ARG Container 0.4.3 bytecode showed the item ships
 * with NO right-click mechanic at all - {@code ExchangerItem} only overrides
 * {@code isFoil} (always glints) and {@code appendHoverText} (adds the
 * decorative "Exchanges your Jump levels..." line). There is no use override,
 * no XP handling, no potion effects, and no cooldown anywhere in the ARG jar
 * for this item. This handler therefore implements the new mechanic from
 * scratch in DLTCore, entirely through Forge events:
 *
 * <ul>
 *   <li>Right-click item (server-side, via {@link PlayerInteractEvent.RightClickItem}):
 *       if the player has at least 10 experience levels, deduct exactly 10 levels,
 *       play the_wonderland:thespawn_laugh at the player, grant Strength II
 *       (amplifier 1) and Resistance III (amplifier 2), each for 1400 ticks
 *       (70 seconds) with particles disabled, then put the item on a 1400-tick
 *       cooldown and cancel the event so nothing else reacts to the click.
 *       With fewer than 10 levels the interaction fails safely: no XP is spent,
 *       no sound, no effects, no cooldown, no state change.</li>
 *   <li>Item tooltip (client-side, via {@link ItemTooltipEvent}): replaces the
 *       shipped "Exchanges your Jump levels..." description line with the new
 *       exact text; other tooltip lines are left untouched.</li>
 * </ul>
 *
 * <p>No mixin is required: the tooltip is mutateably replaceable in the Forge
 * tooltip event, and the ARG item has no competing right-click behavior to
 * override. The vanilla server already refuses to deliver RightClickItem while
 * an item is on cooldown (ServerPlayerGameMode.useItem checks cooldowns before
 * firing the event), and the explicit cooldown + level guards keep the
 * guarantee idempotent regardless of event ordering. All effects, XP, sound,
 * and cooldown are applied server-side so a dedicated server is authoritative
 * and multiplayer needs no extra synchronization.
 *
 * <p>The sound is looked up from the Forge sound registry by its ID (registered
 * by The Wonderland's sounds.json/init); if the registration is ever absent the
 * same key is wrapped into a variable-range SoundEvent so the call still
 * behaves predictably.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class ExchangerAbilityHandler {

    private static final ResourceLocation EXCHANGER_ID =
            new ResourceLocation("the_arg_container", "exchanger");
    private static final ResourceLocation LAUGH_SOUND_ID =
            new ResourceLocation("the_wonderland", "thespawn_laugh");
    private static final String OLD_DESCRIPTION =
            "Exchanges your Jump levels for Durability of the item in your primary slot!";
    private static final String NEW_DESCRIPTION =
            "Sacrifices 10 Experience Levels for an Small Boost!";

    private static final int XP_COST_LEVELS = 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final int EFFECT_DURATION_TICKS = 70 * TICKS_PER_SECOND; // 1,400
    private static final int COOLDOWN_TICKS = 70 * TICKS_PER_SECOND;        // 1,400
    private static final int STRENGTH_AMPLIFIER = 1;   // Strength II
    private static final int RESISTANCE_AMPLIFIER = 2; // Resistance III

    private ExchangerAbilityHandler() {}

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
        if (itemId == null || !EXCHANGER_ID.equals(itemId)) {
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

        player.playNotifySound(resolveLaughSound(), SoundSource.PLAYERS, 1.0F, 1.0F);

        player.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_BOOST, EFFECT_DURATION_TICKS,
                STRENGTH_AMPLIFIER, false, false, true));
        player.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE, EFFECT_DURATION_TICKS,
                RESISTANCE_AMPLIFIER, false, false, true));

        player.getCooldowns().addCooldown(item, COOLDOWN_TICKS);

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null || !EXCHANGER_ID.equals(itemId)) {
            return;
        }

        List<Component> toolTip = event.getToolTip();
        toolTip.removeIf(component -> component.getString().equals(OLD_DESCRIPTION));
        for (Component component : toolTip) {
            if (component.getString().equals(NEW_DESCRIPTION)) {
                return;
            }
        }
        toolTip.add(Component.literal(NEW_DESCRIPTION));
    }

    private static SoundEvent resolveLaughSound() {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(LAUGH_SOUND_ID);
        return sound != null ? sound : SoundEvent.createVariableRangeEvent(LAUGH_SOUND_ID);
    }
}