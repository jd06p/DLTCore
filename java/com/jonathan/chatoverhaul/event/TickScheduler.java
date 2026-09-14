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
 *
 * IMPORTANT: onServerTick extracts all due tasks into a separate list
 * BEFORE running any of them, rather than running each task while still
 * iterating the live TASKS list directly. This matters because some tasks
 * schedule further tasks from within their own execution - most notably
 * Entity303RitualManager's tickTimer(), which reschedules itself every
 * second. If a task's run() calls scheduleInTicks() while TASKS is still
 * being iterated by the SAME loop that's calling that task, the resulting
 * TASKS.add() mid-iteration throws ConcurrentModificationException. This
 * bug existed from the start but never surfaced until a self-rescheduling
 * task (tickTimer) was actually added - nothing before that ever scheduled
 * a new task from inside a currently-running one. Collecting the due tasks
 * into their own list first means any such re-scheduling only ever touches
 * TASKS, never the (separate, already-extracted) list this method is
 * currently looping over, so there's nothing left to corrupt.
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

        List<ScheduledTask> due = new ArrayList<>();
        Iterator<ScheduledTask> it = TASKS.iterator();
        while (it.hasNext()) {
            ScheduledTask t = it.next();
            if (t.dueTick() <= currentTick) {
                it.remove();
                due.add(t);
            }
        }

        for (ScheduledTask t : due) {
            t.task().run();
        }
    }
}
