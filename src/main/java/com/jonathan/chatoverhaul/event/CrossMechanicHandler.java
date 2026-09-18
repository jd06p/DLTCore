package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Extends The Wonderland's light_cross / silver_cross exorcism mechanic to
 * specific entities from The Broken Script and The Arg Container.
 *
 * ADAPTED FROM (not mixed into) net.mcreator.thewonderland.procedures
 * .LightCrossRightclickedProcedure - same forward-scan geometry (9 steps of
 * 0.5 blocks along the player's look vector, entities within a small radius
 * checked at each step, stopping early if a block is hit) and the same two
 * outcomes Wonderland itself uses for its own entities: instant removal for
 * "kill" ranks (matching their entityiterator.discard() calls), or a strong
 * temporary Slowness for "stop" ranks (matching their handling of
 * NoOneEntity/TheEntityChasingEntity - Slowness amplifier 9).
 *
 * Deliberately NOT a mixin: this only reads public registry data (item and
 * entity-type IDs), so it needs no compile-time dependency on either mod's
 * classes and can't affect their bytecode or loading - unlike the
 * ModCheckProcedure mixin attempt, this carries none of that risk. Entities
 * are matched by registry ID rather than by importing the other mods'
 * entity classes, so no multi-hundred-MB mod jars need to be bundled here.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class CrossMechanicHandler {

    private static final Set<String> CROSS_ITEM_IDS = Set.of(
            "the_wonderland:light_cross",
            "the_wonderland:silver_cross"
    );

    // Broken Script entities: temporarily stopped (Slowness X, same as
    // Wonderland's own strongest freeze for its NoOneEntity/TheEntityChasingEntity).
    private static final Set<String> STOP_ENTITY_IDS = Set.of(
            "thebrokenscript:circuit",
            "thebrokenscript:siluet",          // displays in-game as "r2"
            "thebrokenscript:siluet_chase",    // also displays as "r2"
            "thebrokenscript:nothingiswatching",
            "thebrokenscript:the_broken_end"
    );

    // Broken Script's every "Null" variant + Faraway + Curved (displays as
    // "DyeXD412"), plus The Arg Container's Void Entity: killed instantly.
    private static final Set<String> KILL_ENTITY_IDS = Set.of(
            "thebrokenscript:nulll",
            "thebrokenscript:null_scare",
            "thebrokenscript:null_flying",
            "thebrokenscript:null_watching",
            "thebrokenscript:null_endgame",
            "thebrokenscript:null_invade_base",
            "thebrokenscript:null_tp_beacon",
            "thebrokenscript:null_is_here",
            "thebrokenscript:null_mining",
            "thebrokenscript:null_unbeatable_bossfight",
            "thebrokenscript:faraway",
            "thebrokenscript:curved",
            "the_arg_container:void_entity_geckolib"
    );

    private static final ResourceLocation EXORCISM_SOUND = new ResourceLocation("the_wonderland", "exorcism");
    private static final int STOP_DURATION_TICKS = 100; // 5 seconds
    private static final int STOP_AMPLIFIER = 9;         // Slowness X

    // Matches Wonderland's own light_cross/silver_cross cost: 1 durability
    // point and a short cooldown per successful use, so it can't be spammed.
    private static final int DURABILITY_COST = 1;
    private static final int COOLDOWN_TICKS = 100; // 5 seconds, same value Wonderland uses for most of its own entities

    private CrossMechanicHandler() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null || !CROSS_ITEM_IDS.contains(itemId.toString())) {
            return;
        }

        Level level = event.getLevel();
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Player player = event.getEntity();
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }

        InteractionHand hand = event.getHand();
        Vec3 look = player.getLookAngle();
        double range = 0.0;

        for (int step = 0; step < 9; step++) {
            Vec3 center = new Vec3(
                    player.getX() + look.x * range,
                    player.getY() + player.getBbHeight() * 0.9 + look.y * range,
                    player.getZ() + look.z * range
            );

            List<Entity> nearby = level.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(0.5), e -> true)
                    .stream()
                    .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(center)))
                    .toList();

            for (Entity target : nearby) {
                if (target == player) {
                    continue;
                }
                if (handleEntity(serverLevel, target)) {
                    applyCost(player, stack, hand);
                    return;
                }
            }

            range += 0.5;
            Vec3 eye = player.getEyePosition(1.0f);
            HitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                break;
            }
        }
    }

    /** Durability loss + cooldown, applied only once a matching entity was actually hit - same as Wonderland's own crosses. */
    private static void applyCost(Player player, ItemStack stack, InteractionHand hand) {
        player.getCooldowns().addCooldown(stack.getItem(), COOLDOWN_TICKS);
        stack.hurtAndBreak(DURABILITY_COST, player, p -> p.broadcastBreakEvent(hand));
    }

    /** Returns true once a matching entity has been handled, so the caller can stop scanning. */
    private static boolean handleEntity(ServerLevel level, Entity target) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (entityId == null) {
            return false;
        }
        String id = entityId.toString();

        if (STOP_ENTITY_IDS.contains(id)) {
            playEffects(level, target);
            if (target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STOP_DURATION_TICKS, STOP_AMPLIFIER, false, false));
            }
            return true;
        }

        if (KILL_ENTITY_IDS.contains(id)) {
            playEffects(level, target);
            target.discard();
            return true;
        }

        return false;
    }

    private static void playEffects(ServerLevel level, Entity target) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(EXORCISM_SOUND);
        if (sound != null) {
            level.playSound(null, BlockPos.containing(target.getX(), target.getY(), target.getZ()),
                    sound, SoundSource.HOSTILE, 3.0f, 1.0f);
        }
        level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(), target.getZ(), 30, 1.0, 1.0, 1.0, 0.0);
        level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY(), target.getZ(), 60, 1.0, 1.0, 1.0, 0.0);
    }
}
