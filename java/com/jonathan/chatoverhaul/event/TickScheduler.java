package com.jonathan.chatoverhaul.event;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jonathan.chatoverhaul.ChatOverhaul;

/**
 * A tiny "run this N ticks from now" scheduler, driven off the server tick
 * event. Exists so FirstJoinSequence (and anything else in this mod that
 * needs delayed broadcasts) doesn't depend on KubeJS's scheduler at all.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class TickScheduler {

    private record ScheduledTask(long dueTick, Runnable task) {}

    private static final List<ScheduledTask> TASKS = new ArrayList<>();
    private static long currentTick = 0;

    private TickScheduler() {}

    public static void scheduleInTicks(long delayTicks, Runnable task) {
        TASKS.add(new ScheduledTask(currentTick + delayTicks, task));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        currentTick++;

        if (TASKS.isEmpty()) return;

        Iterator<ScheduledTask> it = TASKS.iterator();
        while (it.hasNext()) {
            ScheduledTask t = it.next();
            if (t.dueTick() <= currentTick) {
                it.remove();
                t.task().run();
            }
        }
    }
}
