package com.jonathan.chatoverhaul.mixin.arg;

import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(targets = "net.mcreator.minecraftalphaargmod.procedures.ModCheckProcedure", remap = false)
public class ModCheckProcedureMixin {

    /**
     * Server-safe replacement for the client-only Long Jump toast lambda.
     * Original lambda shows a client toast and returns; on dedicated server
     * the class must be verifiable without referencing net/minecraft/client/*.
     * This no-op preserves server behavior and leaves client untouched when
     * only the server mixin config is applied.
     */
    @Overwrite
    private static void lambda$execute$0() {
        // No-op on server; client behavior unchanged (client not targeted)
    }

    /**
     * Server-safe replacement for the client-only Dash toast lambda.
     * Original lambda shows a client toast, sets ToastLogic=true, syncs data,
     * and schedules a follow-up server task. On dedicated server we only need
     * the class to be verifiable; the original scheduling/logic tied to toasts
     * is not required for server startup and is client-side UI.
     */
    @Overwrite
    private static void lambda$execute$1(LevelAccessor levelAccessor) {
        // No-op on server; client behavior unchanged
    }
}
