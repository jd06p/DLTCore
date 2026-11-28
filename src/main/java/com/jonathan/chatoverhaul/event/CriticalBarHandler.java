package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import com.jonathan.chatoverhaul.config.ChatOverhaulServerConfig;
import com.jonathan.chatoverhaul.network.ChatOverhaulNetwork;
import com.jonathan.chatoverhaul.network.CriticalBarSyncPacket;
import com.jonathan.chatoverhaul.registry.DLTCoreSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class CriticalBarHandler {
    private static final int CAPACITY = 10;
    private static final int CHARGE_PER_KILL = 1;
    private static final int ACTIVE_TICKS = 25 * 20;
    private static final int CHARGING_IDLE_TICKS = 25 * 20;
    private static final String CHARGING_TIMER = "ChargingTicks";
    private static final int AMPLIFIER = 1;
    private static final String DATA = "dltcore_critical_bar";

    private static final Map<ServerPlayer, CriticalBarSyncPacket> LAST_SENT = new WeakHashMap<>();

    private static final Set<ServerPlayer> PENDING_RESPAWN_RESET_SOUND =
            Collections.newSetFromMap(new WeakHashMap<>());

    private CriticalBarHandler() {}

    // Forge dispatches this after death is committed, even without mob loot.
    // Cancelling the drops does not cancel the kill.
    @SubscribeEvent(receiveCanceled = true)
    public static void onMobDeath(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob victim) || victim.level().isClientSide
                || !ChatOverhaulServerConfig.CRITICAL_BAR_ENABLED.get()) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        Entity owner = attacker instanceof OwnableEntity owned ? owned.getOwner() : attacker;
        if (!(owner instanceof ServerPlayer player) || player instanceof FakePlayer || !player.isAlive()
                || victim.getPersistentData().getBoolean(DATA)) {
            return;
        }

        victim.getPersistentData().putBoolean(DATA, true);
        CompoundTag state = getOrCreateState(player);
        int progress = Math.min(CAPACITY, state.getInt("Charge") + CHARGE_PER_KILL);
        state.putInt("Charge", progress);
        if (progress == CAPACITY) {
            int previousTicks = normalizeActiveTimer(player, state);
            maintainEffect(player, state, MobEffects.DAMAGE_BOOST, "Strength", previousTicks, ACTIVE_TICKS);
            maintainEffect(player, state, MobEffects.DAMAGE_RESISTANCE, "Resistance", previousTicks, ACTIVE_TICKS);
            state.remove(CHARGING_TIMER);
            state.putInt("Ticks", ACTIVE_TICKS);
            if (previousTicks == 0) {
                playSound(player, DLTCoreSounds.CRITICAL_ACTIVATE.get());
            }
        } else {
            state.putInt(CHARGING_TIMER, CHARGING_IDLE_TICKS);
        }
        sync(player, false);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player instanceof FakePlayer || !player.isAlive()) {
            return;
        }

        CompoundTag state = player.getPersistentData().getCompound(DATA);
        int previousTicks = state.getInt("Ticks");
        if (!ChatOverhaulServerConfig.CRITICAL_BAR_ENABLED.get()) {
            clear(player, state, previousTicks);
        } else if (previousTicks == 1) {
            resetWithSound(player, state, previousTicks);
        } else if (previousTicks > 1) {
            previousTicks = normalizeActiveTimer(player, state);
            int remaining = previousTicks - 1;
            maintainEffect(player, state, MobEffects.DAMAGE_BOOST, "Strength", previousTicks, remaining);
            maintainEffect(player, state, MobEffects.DAMAGE_RESISTANCE, "Resistance", previousTicks, remaining);
            state.putInt("Ticks", remaining);
        } else if (state.getInt("Charge") > 0) {
            // Old saves with partial charge get a full inactivity window.
            if (!state.contains(CHARGING_TIMER, Tag.TAG_INT)) {
                state.putInt(CHARGING_TIMER, CHARGING_IDLE_TICKS);
            } else {
                int remaining = state.getInt(CHARGING_TIMER) - 1;
                if (remaining > 0) {
                    state.putInt(CHARGING_TIMER, remaining);
                } else {
                    resetWithSound(player, state, 0);
                }
            }
        }
        sync(player, false);
    }

    private static void maintainEffect(ServerPlayer player, CompoundTag state, MobEffect effect,
                                       String key, int previousTicks, int remaining) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && (current.getAmplifier() > AMPLIFIER
                || (current.getAmplifier() == AMPLIFIER && !current.endsWithin(remaining - 1)))) {
            if (!matchesCriticalEffect(current, previousTicks)) {
                state.remove(key);
            }
            return;
        }

        // Vanilla merges effect instances by type and keeps weaker long effects in its hidden chain.
        player.addEffect(new MobEffectInstance(effect, remaining, AMPLIFIER, false, false, true));
        state.putBoolean(key, matchesCriticalEffect(player.getEffect(effect), remaining));
    }

    private static int normalizeActiveTimer(ServerPlayer player, CompoundTag state) {
        int ticks = state.getInt("Ticks");
        if (ticks > ACTIVE_TICKS) {
            // Clamp a Critical State saved by the previous 50-second version.
            shortenLegacyEffect(player, state, MobEffects.DAMAGE_BOOST, "Strength", ticks);
            shortenLegacyEffect(player, state, MobEffects.DAMAGE_RESISTANCE, "Resistance", ticks);
            return ACTIVE_TICKS;
        }
        return ticks;
    }

    private static void shortenLegacyEffect(ServerPlayer player, CompoundTag state, MobEffect type,
                                            String key, int previousTicks) {
        MobEffectInstance current = player.getEffect(type);
        if (state.getBoolean(key) && matchesCriticalEffect(current, previousTicks)) {
            CompoundTag saved = current.save(new CompoundTag());
            saved.putInt("Duration", ACTIVE_TICKS);
            MobEffectInstance shortened = MobEffectInstance.load(saved);
            if (shortened != null) {
                player.forceAddEffect(shortened, null);
            }
        }
    }

    private static boolean matchesCriticalEffect(MobEffectInstance effect, int ticks) {
        return ticks > 0 && effect != null && effect.getAmplifier() == AMPLIFIER && !effect.isAmbient()
                && !effect.isVisible() && effect.showIcon() && Math.abs(effect.getDuration() - ticks) <= 1;
    }

    private static void playSound(ServerPlayer player, SoundEvent sound) {
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(),
                1.0F, 1.0F, player.getRandom().nextLong()));
    }

    private static void resetWithSound(ServerPlayer player, CompoundTag state, int ticks) {
        boolean hadCharge = state.getInt("Charge") > 0;
        clear(player, state, ticks);
        if (hadCharge) {
            playSound(player, DLTCoreSounds.CRITICAL_DEACTIVATE.get());
        }
    }

    private static void clear(ServerPlayer player, CompoundTag state, int ticks) {
        endEffect(player, state, MobEffects.DAMAGE_BOOST, "Strength", ticks);
        endEffect(player, state, MobEffects.DAMAGE_RESISTANCE, "Resistance", ticks);
        player.getPersistentData().remove(DATA);
    }

    private static void endEffect(ServerPlayer player, CompoundTag state, MobEffect type, String key, int ticks) {
        MobEffectInstance current = player.getEffect(type);
        if (state.getBoolean(key) && matchesCriticalEffect(current, ticks)) {
            // Restore a weaker, longer-lasting effect that vanilla had suspended.
            CompoundTag saved = current.save(new CompoundTag());
            MobEffectInstance fallback = null;
            if (saved.contains("HiddenEffect", Tag.TAG_COMPOUND)) {
                CompoundTag hidden = saved.getCompound("HiddenEffect");
                hidden.put("Id", saved.get("Id"));
                fallback = MobEffectInstance.load(hidden);
            }
            if (player.removeEffect(type) && fallback != null
                    && (fallback.isInfiniteDuration() || fallback.getDuration() > 0)) {
                player.addEffect(fallback);
            }
        }
    }

    private static CompoundTag getOrCreateState(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(DATA, Tag.TAG_COMPOUND)) {
            data.put(DATA, new CompoundTag());
        }
        return data.getCompound(DATA);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // A death costs the bar. Returning through the End exit preserves it.
        CompoundTag original = event.getOriginal().getPersistentData();
        if (!event.isWasDeath() && original.contains(DATA, Tag.TAG_COMPOUND)) {
            event.getEntity().getPersistentData().put(DATA, original.getCompound(DATA).copy());
        } else if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player
                && !(player instanceof FakePlayer) && ChatOverhaulServerConfig.CRITICAL_BAR_ENABLED.get()
                && original.getCompound(DATA).getInt("Charge") > 0) {
            // Deliver the death reset cue after the client has respawned.
            PENDING_RESPAWN_RESET_SOUND.add(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, true);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, true);
            if (PENDING_RESPAWN_RESET_SOUND.remove(player) && ChatOverhaulServerConfig.CRITICAL_BAR_ENABLED.get()) {
                playSound(player, DLTCoreSounds.CRITICAL_DEACTIVATE.get());
            }
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_SENT.remove(player);
        }
    }

    private static void sync(ServerPlayer player, boolean force) {
        boolean enabled = ChatOverhaulServerConfig.CRITICAL_BAR_ENABLED.get();
        CompoundTag state = player.getPersistentData().getCompound(DATA);
        CriticalBarSyncPacket snapshot = new CriticalBarSyncPacket(enabled, enabled ? state.getInt("Charge") : 0,
                CAPACITY, enabled && state.getInt("Ticks") > 0);
        if (force || !snapshot.equals(LAST_SENT.get(player))) {
            ChatOverhaulNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
            LAST_SENT.put(player, snapshot);
        }
    }
}
