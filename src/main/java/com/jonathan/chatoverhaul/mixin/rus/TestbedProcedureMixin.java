package com.jonathan.chatoverhaul.mixin.rus;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.Event;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps rus-patch's TestbedProcedure from resolving client classes on a
 * dedicated server.
 *
 * The failure this fixes:
 *
 *   Attempted to load class net/minecraft/client/Minecraft for invalid
 *   dist DEDICATED_SERVER
 *     at net.minecraftforge.fml.loading.RuntimeDistCleaner.processClassWithFlags
 *     at net.mcreator.interpritation.procedures.TestbedProcedure.execute(TestbedProcedure.java:45)
 *     at net.mcreator.interpritation.procedures.TestbedProcedure.onPlayerTick(TestbedProcedure.java:26)
 *
 * This is a genuine client/server separation bug in rus-patch, not
 * something DLTCore introduced. A full audit of all 99 @SubscribeEvent /
 * @Mod.EventBusSubscriber classes in rus-patch 1.9.3 that are reachable on
 * a dedicated server shows this is the only unguarded one:
 *
 *   - TestbedProcedure is the single procedure that calls into
 *     net.minecraft.client / com.mojang.blaze3d / org.lwjgl.glfw with no
 *     side check at all.
 *   - Its three sibling procedures that also touch client classes -
 *     MoonDevoidsProcedure, PlayerJoinsProcedure and TitleChangeProcedure -
 *     each already guard their client calls with level.isClientSide(), which
 *     is why they never crash the server.
 *   - Everything else that references client classes is already
 *     unreachable server-side: ExitDissable / Nomodsnoconfigs /
 *     Norealms / the four init.* registration classes and the four
 *     DimensionSpecialEffects handlers all declare
 *     @Mod.EventBusSubscriber(value = Dist.CLIENT), and the
 *     client.screens.* overlay classes and entity.layer.* render layers
 *     are only ever loaded by the client.
 *
 * What TestbedProcedure actually does (from bytecode):
 *
 *   onPlayerTick(Phase.END)
 *     -> execute(event, player.level, player.getX(), getY(), getZ())
 *        -> level.getEntitiesOfClass(CircuitEntity.class,
 *                AABB.ofSize(pos, 400, 400, 400), e -> true)
 *        -> if the list is non-empty:
 *             if (Math.random() < 0.7)  GLFW.glfwSetWindowPos(handle,  50,  40);
 *             else                      GLFW.glfwSetWindowPos(handle, -50, -40);
 *
 * where handle comes from Minecraft.getInstance().getWindow().getWindow().
 * That is a native call that jitters the game window when the player is
 * within 400 blocks of a Circuit - a pure client-side scare effect.
 *
 * The entity lookup itself is a harmless read, so the entire observable
 * behaviour of this procedure is client-only; there is no server-side
 * gameplay, AI, damage or networking to preserve here. The Circuit's actual
 * kill/leave logic is unrelated and untouched: CircuitEntity holds no
 * client references at all and only calls remove(RemovalReason), while the
 * fake-leave effect is driven by ClientboundPlayerAbilities packets from
 * completely separate procedures (ExitOnTickUpdateProcedure,
 * FollowOnEntityTickUpdateProcedure, ItOnTickUpdateProcedure,
 * VoidExpEntracnceProcedure and others).
 *
 * Why it only appeared after rejoining rather than at startup: the class
 * reference is resolved lazily, on the first tick where a CircuitEntity is
 * actually in range. Standing far away returns early and never touches
 * Minecraft, so the dedicated server boots fine; the crash lands as soon as
 * a player ticks near a Circuit, which is exactly what happens on rejoin.
 *
 * Rather than overwriting the method, this injects a single early-return at
 * its HEAD. That keeps the whole client path byte-for-byte identical (the
 * config is server-only, so the client keeps the original code and still
 * gets the window shake in singleplayer and on the host of a LAN world)
 * while making the dedicated server skip the block before Minecraft is
 * ever touched. It also mirrors how the mod author already guarded their
 * other three client-touching procedures, so this is the pattern rus-patch
 * itself would use.
 */
@Mixin(targets = "net.mcreator.interpritation.procedures.TestbedProcedure", remap = false)
public class TestbedProcedureMixin {

    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    // One-shot confirmation, so the dedicated server log can positively show
    // that this build is deployed AND that the mixin actually applied. If the
    // guard is working you will see exactly one of these lines in the server
    // console, the first time a player ticks within 400 blocks of a Circuit.
    @Unique
    private static boolean chatoverhaul$guardReported = false;

    // The descriptor is mandatory here and must not be trimmed back to just
    // "execute". TestbedProcedure declares TWO overloads of that name:
    //
    //   public  static void execute(LevelAccessor, double, double, double)
    //   private static void execute(Event, LevelAccessor, double, double, double)
    //
    // onPlayerTick calls the private 5-arg one directly (which is the one
    // that touches Minecraft at line 37), while the public 3-arg one just
    // forwards to it. A bare method = "execute" selector matches both, and
    // the 3-arg target is incompatible with a 5-arg handler - Mixin raises
    // InvalidInjectionException and, because this config is "required": false,
    // only warns about it instead of failing the boot. The result is a server
    // that starts cleanly with the mixin silently NOT applied. Pinning the
    // full descriptor targets the 5-arg method unambiguously.
    @Inject(
            method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void chatoverhaul$skipClientOnlyWindowShake(Event event, LevelAccessor level,
                                                                double x, double y, double z,
                                                                CallbackInfo ci) {
        if (!level.isClientSide()) {
            if (!chatoverhaul$guardReported) {
                chatoverhaul$guardReported = true;
                LOGGER.info("[DLTCore] Broken Script TestbedProcedure dedicated-server guard is ACTIVE");
            }
            ci.cancel();
        }
    }
}