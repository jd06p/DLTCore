package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import com.jonathan.chatoverhaul.network.ChatOverhaulNetwork;
import com.jonathan.chatoverhaul.network.CircuitChasePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-owned state for each Circuit, never the RUS global MapVariables frame. */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class CircuitChaseDelivery {
    private static final String FRAME = "DLTCoreCircuitFrame";
    private static final Map<Entity, Entity> FLASHES = new WeakHashMap<>();
    private static final Map<ServerPlayer, Snapshot> SENT = new WeakHashMap<>();
    private record Snapshot(ResourceLocation dimension, boolean active, double frame, boolean flash) {}

    private CircuitChaseDelivery() {}

    public static double frame(Entity circuit) {
        return circuit.getPersistentData().getDouble(FRAME);
    }

    public static void frame(Entity circuit, double value) {
        circuit.getPersistentData().putDouble(FRAME, value);
        FLASHES.putIfAbsent(circuit, null);
    }

    public static ServerPlayer target(Entity circuit) {
        if (!circuit.isAlive()) return null;
        if (circuit instanceof Mob mob && mob.getTarget() instanceof ServerPlayer player
                && player.isAlive() && !player.hasDisconnected() && player.level() == circuit.level()) {
            return player;
        }
        return null;
    }

    /** Spawn sounds precede RUS's first target-selection tick. Use that same selection rule. */
    public static void spawned(Entity circuit) {
        if (circuit == null || circuit.level().isClientSide()) return;
        FLASHES.putIfAbsent(circuit, null);
        if (circuit instanceof Mob mob && target(circuit) == null
                && !circuit.level().getEntitiesOfClass(Player.class,
                        AABB.ofSize(circuit.position(), 1450, 1450, 1450), player -> true).isEmpty()) {
            circuit.level().getEntitiesOfClass(Player.class,
                            AABB.ofSize(circuit.position(), 1500, 1500, 1500), player -> true).stream()
                    .min(Comparator.comparingDouble(circuit::distanceToSqr)).ifPresent(mob::setTarget);
        }
    }

    public static void sound(Entity circuit, BlockPos pos, SoundEvent sound, SoundSource source,
                             float volume, float pitch) {
        ServerPlayer player = target(circuit);
        if (player != null && sound != null) {
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    source, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    volume, pitch, circuit.level().getRandom().nextLong()));
        }
    }

    public static void flash(Entity circuit, Entity flash) {
        FLASHES.put(circuit, flash);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Map<ServerPlayer, Entity> nearest = new HashMap<>();
        FLASHES.keySet().removeIf(Entity::isRemoved);
        for (Entity circuit : FLASHES.keySet()) {
            ServerPlayer player = target(circuit);
            if (player == null) continue;
            // Preserve the original nearby-check's 1000-wide box, but only for this chase.
            if (!AABB.ofSize(player.position(), 1000, 1000, 1000).intersects(circuit.getBoundingBox())) {
                circuit.getPersistentData().putDouble(FRAME, 0);
            }
            Entity previous = nearest.get(player);
            if (previous == null || player.distanceToSqr(circuit) < player.distanceToSqr(previous)) {
                nearest.put(player, circuit);
            }
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Entity circuit = nearest.get(player);
            ResourceLocation dimension = player.level().dimension().location();
            Snapshot state = new Snapshot(dimension, false, 0, false);
            if (circuit != null) {
                Entity flash = FLASHES.get(circuit);
                boolean visible = flash != null && !flash.isRemoved()
                        && AABB.ofSize(player.position(), 400, 400, 400).intersects(flash.getBoundingBox());
                state = new Snapshot(dimension, true, frame(circuit), visible);
            }
            if (!state.equals(SENT.get(player))) {
                ChatOverhaulNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new CircuitChasePacket(player.level().dimension().location(),
                                state.active(), state.frame(), state.flash()));
                SENT.put(player, state);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        FLASHES.clear();
        SENT.clear();
    }
}
