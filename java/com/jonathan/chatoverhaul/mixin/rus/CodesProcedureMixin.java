package com.jonathan.chatoverhaul.mixin.rus;

import net.minecraftforge.event.ServerChatEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables The Broken Script's Null chat-response system entirely.
 *
 * rus-patch's net.mcreator.interpritation.procedures.CodesProcedure is a
 * dedicated @Mod.EventBusSubscriber on ServerChatEvent that does nothing
 * except pattern-match the player's typed message against ~20 trigger
 * phrases ("hello", "who are you", "null", "friend?", "circuit",
 * "the broken end", etc.) and respond with its own separate
 * `/tellraw @a "<null> ..."` broadcasts, sounds, and occasional entity
 * spawns. This is the entire contents of that class - it has no other
 * responsibility, so disabling it entirely does not touch Null's actual
 * entity/AI behavior at all (that logic lives in the various NullXxxEntity
 * classes and their own tick/spawn procedures, none of which this
 * touches).
 *
 * Simply cancelling ServerChatEvent ourselves at high priority was
 * considered and rejected: that would also suppress the player's own
 * message from broadcasting to everyone else, which is not what was
 * asked for - only Null's automatic response should go away, not the
 * player's own chat visibility. Injecting directly into
 * CodesProcedure#onChat and cancelling that specific callback leaves the
 * original ServerChatEvent completely untouched for every other listener,
 * including whatever normally broadcasts the player's message.
 *
 * No compile-time dependency on rus-patch was needed for this - the only
 * type referenced here (ServerChatEvent) is Forge's own class, already on
 * the classpath via the normal Forge dependency. Lives in its own
 * "required": false mixin config (mixins.chatoverhaul.rus.json) so
 * chatoverhaul still loads fine in a pack without rus-patch installed.
 */
@Mixin(targets = "net.mcreator.interpritation.procedures.CodesProcedure", remap = false)
public class CodesProcedureMixin {

    @Inject(method = "onChat", at = @At("HEAD"), cancellable = true)
    private static void chatoverhaul$disableNullChatResponses(ServerChatEvent event, CallbackInfo ci) {
        ci.cancel();
    }
}
