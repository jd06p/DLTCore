package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rewrites rus-patch's "null joined the game" tellraw broadcast.
 *
 * The two @Inject-based attempts on ServerPlayer#displayClientMessage and
 * ServerPlayer#sendSystemMessage (in PlayerDisplayMessageMixin) were both
 * guesses at which internal method vanilla's TellRawCommand actually
 * calls, made without being able to compile-verify against vanilla's own
 * source. Since the rewrite still wasn't firing, evidently neither guess
 * was correct.
 *
 * This sidesteps that uncertainty entirely by intercepting the command
 * itself, before ANY of its internal implementation runs, via Forge's own
 * CommandEvent - fired for every command dispatched through the command
 * source stack, including ones (like this one) dispatched programmatically
 * by another mod rather than typed by a player. Cancelling it here means
 * whatever TellRawCommand does internally never executes at all, so it
 * doesn't matter which specific method it would have called - and the
 * replacement is broadcast via PlayerList#broadcastSystemMessage, the same
 * method PlayerListBroadcastMixin already uses successfully for the
 * "no one" dialogue rewrite.
 *
 * The two mixin-based attempts are left in place as harmless redundancy -
 * if either of them ever turns out to be correct after all, it simply
 * never gets a chance to fire, since this cancels the command before
 * either injected method would even be reached.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class NullJoinCommandInterceptor {

    private NullJoinCommandInterceptor() {}

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        String input = event.getParseResults().getReader().getString();

        if (!input.startsWith("tellraw") || !input.contains("null joined the game")) {
            return;
        }

        event.setCanceled(true);

        MinecraftServer server = event.getParseResults().getContext().getSource().getServer();
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("\u273A joined the game").withStyle(ChatFormatting.YELLOW), false);
    }
}
