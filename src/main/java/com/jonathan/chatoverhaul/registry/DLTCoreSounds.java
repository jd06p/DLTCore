package com.jonathan.chatoverhaul.registry;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sound events owned by DLTCore. */
public final class DLTCoreSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ChatOverhaul.MODID);

    public static final RegistryObject<SoundEvent> THE_VOID_MUSIC = SOUND_EVENTS.register(
            "music.thevoid",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ChatOverhaul.MODID, "music.thevoid")));

    private DLTCoreSounds() {}

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
