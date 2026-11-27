package com.jonathan.chatoverhaul.client;

import com.jonathan.chatoverhaul.ChatOverhaul;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Restores the two configured looping rotation effects once per client login,
 * after Custom Skyboxes has finished loading its persistent skybox config.
 * Uses reflection so Custom Skyboxes remains an optional dependency.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID, value = Dist.CLIENT)
public final class CustomSkyboxesRotationLifecycle {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SKYBOX_TYPE_CLASS = "org.cotii.customskyboxes.client.sky.SkyboxType";
    private static final String EFFECT_CLASS = "org.cotii.customskyboxes.client.sky.SkyEffects$Effect";
    private static final String SKY_EFFECTS_CLASS = "org.cotii.customskyboxes.client.sky.SkyEffects";
    private static final String CLIENT_CLASS = "org.cotii.customskyboxes.forge.CustomskyboxesForgeClient";

    private static boolean awaitingReadyWorld;
    private static ClientLevel observedLevel;
    private static Field customSkyboxesConfigLoadedField;
    private static boolean configReadinessUnavailable;

    private CustomSkyboxesRotationLifecycle() {}

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        awaitingReadyWorld = true;
        observedLevel = null;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        awaitingReadyWorld = false;
        observedLevel = null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel currentLevel = minecraft.level;
        if (currentLevel == null || minecraft.player == null) {
            if (currentLevel == null) observedLevel = null;
            return;
        }
        if (observedLevel != currentLevel) {
            if (observedLevel != null) awaitingReadyWorld = true;
            observedLevel = currentLevel;
        }
        if (!awaitingReadyWorld) return;

        if (!ChatOverhaulConfig.CUSTOM_SKYBOXES_AUTO_ROTATION.get()) {
            awaitingReadyWorld = false;
            return;
        }
        if (!ModList.get().isLoaded("customskyboxes")) {
            awaitingReadyWorld = false;
            return;
        }

        if (!isCustomSkyboxesConfigReady()) {
            if (configReadinessUnavailable) awaitingReadyWorld = false;
            return;
        }

        // One attempt per login. Later ticks and dimension changes never reset
        // player-authored animations or restart a running rotation.
        awaitingReadyWorld = false;
        try {
            ClassLoader loader = CustomSkyboxesRotationLifecycle.class.getClassLoader();
            Class<?> typeClass = Class.forName(SKYBOX_TYPE_CLASS, false, loader);
            Class<?> effectClass = Class.forName(EFFECT_CLASS, false, loader);
            Class<?> effectsClass = Class.forName(SKY_EFFECTS_CLASS, false, loader);
            Object rotation = enumValue(effectClass, "ROTATION");

            startIfRotationMissing(effectsClass, typeClass, effectClass, "DAY_SKY", rotation,
                    ChatOverhaulConfig.CUSTOM_SKYBOXES_DAY_ROTATION_TICKS.get());
            startIfRotationMissing(effectsClass, typeClass, effectClass, "NIGHT_SKY", rotation,
                    ChatOverhaulConfig.CUSTOM_SKYBOXES_NIGHT_ROTATION_TICKS.get());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.error("Could not restore Custom Skyboxes rotation effects", exception);
        }
    }

    /** Custom Skyboxes loads SkyboxConfig synchronously from its first client tick. */
    private static boolean isCustomSkyboxesConfigReady() {
        try {
            if (customSkyboxesConfigLoadedField == null) {
                Class<?> clientClass = Class.forName(CLIENT_CLASS, false,
                        CustomSkyboxesRotationLifecycle.class.getClassLoader());
                customSkyboxesConfigLoadedField = clientClass.getDeclaredField("configLoaded");
                customSkyboxesConfigLoadedField.setAccessible(true);
            }
            return customSkyboxesConfigLoadedField.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // The exact 1.0.4.2 readiness marker was inspected in its Forge client class.
            // Avoid racing its persistent JSON load; stop retrying on an incompatible jar.
            configReadinessUnavailable = true;
            LOGGER.warn("Cannot inspect Custom Skyboxes config readiness; automatic rotation was skipped", exception);
            return false;
        }
    }

    private static void startIfRotationMissing(Class<?> effectsClass, Class<?> typeClass,
                                               Class<?> effectClass, String typeName,
                                               Object rotation, int ticks)
            throws ReflectiveOperationException {
        Object type = enumValue(typeClass, typeName);
        if (hasLiveRotation(effectsClass, typeClass, type, effectClass)) return;

        Method start = effectsClass.getMethod("start", typeClass, effectClass,
                int.class, int.class, float.class, float.class, boolean.class, String.class);
        // These are SkyboxCommand.doAnimate defaults for the supplied commands:
        // white, strength 1, size 1, looping flag from loop=true, global biome.
        start.invoke(null, type, rotation, ticks, 0xFFFFFF, 1.0f, 1.0f, true, null);
    }

    /**
     * SkyEffects.isRunning(type) returns true for any effect on that type.
     * Inspect the jar's live list so unrelated effects such as pulse do not
     * suppress rotation initialization.
     */
    private static boolean hasLiveRotation(Class<?> effectsClass, Class<?> typeClass,
                                           Object type, Class<?> effectClass)
            throws ReflectiveOperationException {
        Method live = effectsClass.getDeclaredMethod("live", typeClass);
        live.setAccessible(true);
        List<?> active = (List<?>) live.invoke(null, type);
        if (active == null || active.isEmpty()) return false;

        Field effect = Class.forName(SKY_EFFECTS_CLASS + "$Running", false,
                CustomSkyboxesRotationLifecycle.class.getClassLoader()).getDeclaredField("effect");
        effect.setAccessible(true);
        for (Object running : active) {
            Object value = effect.get(running);
            if (value != null && effectClass.isInstance(value)
                    && "ROTATION".equals(((Enum<?>) value).name())) return true;
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }
}
