package com.jonathan.chatoverhaul.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.Optional;

import com.mojang.logging.LogUtils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class RusKillTriggerGuard {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean reported;
    private static final Entity NULL_SENTINEL = new NullEntitySentinel();

    private RusKillTriggerGuard() {
    }

    public static Entity nullSafeEntity(Entity e) {
        return e == null ? NULL_SENTINEL : e;
    }

    public static Level levelOf(Entity entity) {
        if (entity != null && entity != NULL_SENTINEL) {
            return entity.level();
        }
        report();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.overworld() : null;
    }

    public static void discard(Entity entity) {
        if (entity != null && entity != NULL_SENTINEL) {
            entity.discard();
        } else {
            report();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T guardOrElse(Optional<T> opt, T def) {
        T v = opt.orElse(def);
        if (v == null) {
            report();
            return (T) NULL_SENTINEL;
        }
        return v;
    }

    private static void report() {
        if (!reported) {
            reported = true;
            LOGGER.info("[DLTCore] Broken Script kill-trigger null-entity guard is ACTIVE");
        }
    }

    private static final class NullEntitySentinel extends Entity {
        private NullEntitySentinel() {
            super(null, null);
        }

        @Override
        public Level level() {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            return server != null ? server.overworld() : null;
        }

        @Override
        public void remove(Entity.RemovalReason reason) {
        }

        

        @Override
        protected void defineSynchedData() {
        }

        @Override
        public void readAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        public void addAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        protected Component getTypeName() {
            return Component.empty();
        }

        @Override
        public EntityType<?> getType() {
            return EntityType.AREA_EFFECT_CLOUD;
        }
    }
}
