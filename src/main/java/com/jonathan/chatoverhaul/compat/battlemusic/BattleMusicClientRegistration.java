package com.jonathan.chatoverhaul.compat.battlemusic;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Do not reflect over lifecycle methods mentioning Battle Music types unless that optional mod exists. */
@Mod.EventBusSubscriber(modid = "chatoverhaul", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BattleMusicClientRegistration {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("battlemusic"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.register(BattleMusicLifecycle.class));
    }
}
